"""
Module for rotating credentials in Conjur vault.

Implements a secure credential rotation process that allows for zero-downtime
updates by supporting a transition period where both old and new credentials
are valid. This module is a critical component of the Payment API Security
Enhancement project's credential management system.
"""
import requests  # version 2.28.1
import json      # standard library
import time      # standard library
import argparse  # standard library
import sys       # standard library
import uuid      # standard library
from enum import Enum  # standard library

from .config import LOGGER, ConjurConfig, RotationConfig, get_credential_path, create_conjur_config, get_rotation_config
from .utils import (
    create_http_session,
    build_conjur_url,
    parse_conjur_response,
    RetryHandler,
    ConjurConnectionError,
    ConjurPermissionError,
    sanitize_log_data
)
from .authenticate import authenticate_with_retry
from .retrieve_credentials import retrieve_credential_with_retry, clear_credential_cache
from .store_credentials import store_credential_with_retry, generate_credential

# Default values for configuration
DEFAULT_TRANSITION_PERIOD_SECONDS = 86400  # 1 day
DEFAULT_MONITORING_INTERVAL_SECONDS = 300  # 5 minutes

# Define rotation states enum
ROTATION_STATES = Enum('RotationState', ['INITIATED', 'DUAL_ACTIVE', 'OLD_DEPRECATED', 'NEW_ACTIVE', 'FAILED'])


class RotationResult:
    """Class representing the result of a credential rotation operation."""
    
    def __init__(self, success, client_id, old_version, new_version, state, transition_period_seconds, error_message=None):
        """
        Initializes a new RotationResult instance.
        
        Args:
            success (bool): Whether the rotation was successful.
            client_id (str): Client ID for which the credential was rotated.
            old_version (str): Version identifier of the old credential.
            new_version (str): Version identifier of the new credential.
            state (ROTATION_STATES): Current state of the rotation process.
            transition_period_seconds (int): Transition period in seconds.
            error_message (str, optional): Error message if rotation failed. Defaults to None.
        """
        self.success = success
        self.client_id = client_id
        self.old_version = old_version
        self.new_version = new_version
        self.state = state
        self.transition_period_seconds = transition_period_seconds
        self.error_message = error_message
    
    def to_dict(self):
        """
        Converts the result to a dictionary.
        
        Returns:
            dict: Dictionary representation of the result.
        """
        return {
            'success': self.success,
            'client_id': self.client_id,
            'old_version': self.old_version,
            'new_version': self.new_version,
            'state': self.state.name if self.state else None,
            'transition_period_seconds': self.transition_period_seconds,
            'error_message': self.error_message
        }
    
    def to_json(self):
        """
        Converts the result to a JSON string.
        
        Returns:
            str: JSON string representation of the result.
        """
        return json.dumps(self.to_dict())
    
    def is_complete(self):
        """
        Checks if the rotation process is complete.
        
        Returns:
            bool: True if rotation is complete, False otherwise.
        """
        return self.success and self.state == ROTATION_STATES.NEW_ACTIVE


def rotate_credential(client_id, conjur_config, rotation_config):
    """
    Rotates a credential in Conjur vault with a transition period where both old and new credentials are valid.
    
    Args:
        client_id (str): Client ID for which to rotate the credential.
        conjur_config (ConjurConfig): Conjur configuration object.
        rotation_config (RotationConfig): Rotation configuration object.
    
    Returns:
        RotationResult: Result of the rotation operation.
    """
    LOGGER.info(f"Starting credential rotation for client_id: {client_id}")
    
    try:
        # Validate rotation configuration
        if not validate_rotation_config(rotation_config):
            return RotationResult(
                False, 
                client_id, 
                None, 
                None, 
                ROTATION_STATES.FAILED,
                rotation_config.transition_period_seconds,
                "Invalid rotation configuration"
            )
        
        # Retrieve existing credential
        try:
            existing_credential = retrieve_credential_with_retry(client_id, conjur_config)
            
            # Extract existing credential metadata
            old_version = existing_credential.get('version', 'unknown')
            LOGGER.debug(f"Retrieved existing credential (version: {old_version}) for client_id: {client_id}")
            
        except (ConjurConnectionError, ConjurPermissionError) as e:
            return RotationResult(
                False, 
                client_id, 
                None, 
                None, 
                ROTATION_STATES.FAILED,
                rotation_config.transition_period_seconds,
                f"Failed to retrieve existing credential: {str(e)}"
            )
        
        # Update rotation state to INITIATED
        if not update_rotation_state(client_id, ROTATION_STATES.INITIATED, conjur_config):
            return RotationResult(
                False, 
                client_id, 
                old_version, 
                None, 
                ROTATION_STATES.FAILED,
                rotation_config.transition_period_seconds,
                "Failed to update rotation state to INITIATED"
            )
        
        # Generate new credential
        new_credential = generate_credential(client_id)
        new_version = str(uuid.uuid4())
        
        # Prepare credential data with metadata for dual validity period
        from datetime import datetime
        timestamp = datetime.utcnow().isoformat() + 'Z'
        
        credential_data = {
            'client_id': client_id,
            'client_secret': new_credential['client_secret'],
            'created_at': existing_credential.get('created_at', timestamp),
            'updated_at': timestamp,
            'version': new_version,
            'status': 'active',
            'rotation': {
                'state': ROTATION_STATES.DUAL_ACTIVE.name,
                'old_version': old_version,
                'started_at': timestamp,
                'transition_period_seconds': rotation_config.transition_period_seconds
            }
        }
        
        # Store new credential with rotation metadata
        if not store_credential_with_retry(client_id, json.dumps(credential_data), conjur_config):
            return RotationResult(
                False, 
                client_id, 
                old_version, 
                new_version, 
                ROTATION_STATES.FAILED,
                rotation_config.transition_period_seconds,
                "Failed to store new credential"
            )
        
        # Update rotation state to DUAL_ACTIVE
        if not update_rotation_state(client_id, ROTATION_STATES.DUAL_ACTIVE, conjur_config):
            return RotationResult(
                False, 
                client_id, 
                old_version, 
                new_version, 
                ROTATION_STATES.FAILED,
                rotation_config.transition_period_seconds,
                "Failed to update rotation state to DUAL_ACTIVE"
            )
        
        # Monitor credential usage during transition period
        transition_complete = monitor_credential_usage(client_id, conjur_config, rotation_config)
        
        # Update rotation state to OLD_DEPRECATED
        if not update_rotation_state(client_id, ROTATION_STATES.OLD_DEPRECATED, conjur_config):
            return RotationResult(
                False, 
                client_id, 
                old_version, 
                new_version, 
                ROTATION_STATES.DUAL_ACTIVE,
                rotation_config.transition_period_seconds,
                "Failed to update rotation state to OLD_DEPRECATED"
            )
        
        # After transition period, if no old credential usage is detected,
        # update rotation state to NEW_ACTIVE
        if transition_complete:
            if not update_rotation_state(client_id, ROTATION_STATES.NEW_ACTIVE, conjur_config):
                return RotationResult(
                    False, 
                    client_id, 
                    old_version, 
                    new_version, 
                    ROTATION_STATES.OLD_DEPRECATED,
                    rotation_config.transition_period_seconds,
                    "Failed to update rotation state to NEW_ACTIVE"
                )
        
        # Clear credential cache to ensure fresh credentials are retrieved
        clear_credential_cache(client_id)
        
        LOGGER.info(f"Credential rotation completed successfully for client_id: {client_id}")
        
        return RotationResult(
            True, 
            client_id, 
            old_version, 
            new_version, 
            ROTATION_STATES.NEW_ACTIVE,
            rotation_config.transition_period_seconds,
            None
        )
    
    except Exception as e:
        LOGGER.error(f"Unexpected error during credential rotation: {str(e)}")
        return RotationResult(
            False, 
            client_id, 
            None, 
            None, 
            ROTATION_STATES.FAILED,
            rotation_config.transition_period_seconds,
            f"Unexpected error: {str(e)}"
        )


def rotate_credential_with_retry(client_id, conjur_config, rotation_config, max_retries=3, backoff_factor=1.5):
    """
    Rotates a credential in Conjur vault with retry mechanism.
    
    Args:
        client_id (str): Client ID for which to rotate the credential.
        conjur_config (ConjurConfig): Conjur configuration object.
        rotation_config (RotationConfig): Rotation configuration object.
        max_retries (int, optional): Maximum number of retry attempts. Defaults to 3.
        backoff_factor (float, optional): Backoff factor for retry delays. Defaults to 1.5.
    
    Returns:
        RotationResult: Result of the rotation operation.
    """
    retry_handler = RetryHandler(
        max_retries=max_retries,
        backoff_factor=backoff_factor,
        retryable_exceptions=[ConjurConnectionError, requests.exceptions.RequestException]
    )
    
    try:
        return retry_handler.execute(rotate_credential, client_id, conjur_config, rotation_config)
    except Exception as e:
        LOGGER.error(f"Failed to rotate credential after {max_retries} retries: {str(e)}")
        return RotationResult(
            False, 
            client_id, 
            None, 
            None, 
            ROTATION_STATES.FAILED,
            rotation_config.transition_period_seconds if rotation_config else DEFAULT_TRANSITION_PERIOD_SECONDS,
            f"Failed after {max_retries} retries: {str(e)}"
        )


def update_rotation_state(client_id, new_state, conjur_config):
    """
    Updates the rotation state of a credential in Conjur vault.
    
    Args:
        client_id (str): Client ID for which to update the rotation state.
        new_state (ROTATION_STATES): New rotation state.
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        bool: True if successful, False otherwise.
    """
    try:
        # Get authentication token
        auth_token = authenticate_with_retry(conjur_config)
        
        # Retrieve current credential data
        credential = retrieve_credential_with_retry(client_id, conjur_config)
        
        if not credential or not isinstance(credential, dict):
            LOGGER.error(f"Failed to retrieve credential data for client_id: {client_id}")
            return False
            
        # Update rotation state in credential data
        if isinstance(credential, dict):
            # Initialize rotation metadata if not present
            if 'rotation' not in credential:
                from datetime import datetime
                timestamp = datetime.utcnow().isoformat() + 'Z'
                credential['rotation'] = {
                    'state': new_state.name,
                    'updated_at': timestamp
                }
            else:
                # Update existing rotation metadata
                credential['rotation']['state'] = new_state.name
                from datetime import datetime
                credential['rotation']['updated_at'] = datetime.utcnow().isoformat() + 'Z'
                
                # Add completion timestamp if state is NEW_ACTIVE
                if new_state == ROTATION_STATES.NEW_ACTIVE:
                    credential['rotation']['completed_at'] = datetime.utcnow().isoformat() + 'Z'
        else:
            LOGGER.error(f"Credential data is not in the expected format for client_id: {client_id}")
            return False
        
        # Store updated credential data back to Conjur vault
        success = store_credential_with_retry(client_id, json.dumps(credential), conjur_config)
        
        if success:
            LOGGER.info(f"Updated rotation state to {new_state.name} for client_id: {client_id}")
            return True
        else:
            LOGGER.error(f"Failed to update rotation state for client_id: {client_id}")
            return False
            
    except Exception as e:
        LOGGER.error(f"Error updating rotation state: {str(e)}")
        return False


def monitor_credential_usage(client_id, conjur_config, rotation_config):
    """
    Monitors the usage of old credentials during transition period.
    
    Args:
        client_id (str): Client ID to monitor.
        conjur_config (ConjurConfig): Conjur configuration object.
        rotation_config (RotationConfig): Rotation configuration object.
    
    Returns:
        bool: True when transition period is complete, False if monitoring should continue.
    """
    LOGGER.info(f"Starting to monitor credential usage for client_id: {client_id}")
    
    # Calculate end time for transition period
    end_time = time.time() + rotation_config.transition_period_seconds
    no_usage_count = 0
    
    while time.time() < end_time:
        # Check if old credentials are still being used
        is_used = check_credential_usage(client_id, conjur_config)
        
        if is_used:
            LOGGER.info(f"Old credentials still in use for client_id: {client_id}")
            no_usage_count = 0
        else:
            no_usage_count += 1
            LOGGER.info(f"No usage of old credentials detected for client_id: {client_id} ({no_usage_count} consecutive checks)")
            
            # If no usage detected for a sufficient period (3 consecutive checks), we can end early
            if no_usage_count >= 3:
                LOGGER.info(f"No usage of old credentials detected for sufficient period. Ending transition period early for client_id: {client_id}")
                return True
        
        # Sleep for monitoring interval
        LOGGER.debug(f"Sleeping for {rotation_config.monitoring_interval_seconds} seconds before next check")
        time.sleep(rotation_config.monitoring_interval_seconds)
    
    LOGGER.info(f"Transition period completed for client_id: {client_id}")
    return True


def check_credential_usage(client_id, conjur_config):
    """
    Checks if old credentials are still being used.
    
    In a real implementation, this would query usage metrics or logs.
    For this implementation, we'll simulate by assuming a decreasing 
    probability of usage over time.
    
    Args:
        client_id (str): Client ID to check.
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        bool: True if old credentials are still in use, False otherwise.
    """
    # This is a simulation - in a real implementation, this would query
    # monitoring systems, logs, or metrics to determine if the old credentials
    # are still being actively used.
    
    # For simulation purposes, we'll assume a random chance of usage
    # that decreases over time
    import random
    
    # Get rotation status to determine how long the rotation has been active
    rotation_status = get_rotation_status(client_id, conjur_config)
    
    if rotation_status and 'started_at' in rotation_status:
        try:
            # Parse the started_at timestamp
            from datetime import datetime
            start_time = datetime.fromisoformat(rotation_status['started_at'].replace('Z', '+00:00'))
            now = datetime.utcnow()
            
            # Calculate elapsed time in hours
            elapsed_hours = (now - start_time).total_seconds() / 3600
            
            # Probability of usage decreases over time
            # Starting at 80% and decreasing to 5% after 24 hours
            if elapsed_hours >= 24:
                usage_probability = 0.05
            else:
                usage_probability = 0.8 - (0.75 * elapsed_hours / 24)
                
            # Simulate usage based on probability
            return random.random() < usage_probability
            
        except Exception as e:
            LOGGER.error(f"Error calculating credential usage probability: {str(e)}")
            # Default to assuming it's still in use
            return True
    
    # If we can't determine rotation status, assume credentials are still in use
    return True


def get_rotation_status(client_id, conjur_config):
    """
    Gets the current rotation status for a credential.
    
    Args:
        client_id (str): Client ID to check.
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        dict: Rotation status information.
    """
    try:
        # Retrieve credential data
        credential = retrieve_credential_with_retry(client_id, conjur_config)
        
        if not credential or not isinstance(credential, dict):
            LOGGER.error(f"Failed to retrieve credential data for client_id: {client_id}")
            return None
        
        # Extract rotation metadata
        rotation_data = credential.get('rotation', {})
        
        # If no rotation data exists, return empty status
        if not rotation_data:
            return {
                'state': None,
                'client_id': client_id,
                'in_progress': False
            }
        
        # Add client_id to rotation data
        rotation_data['client_id'] = client_id
        
        # Add a convenience flag to indicate if rotation is in progress
        if 'state' in rotation_data:
            state = rotation_data['state']
            rotation_data['in_progress'] = (
                state != ROTATION_STATES.NEW_ACTIVE.name and 
                state != ROTATION_STATES.FAILED.name
            )
        else:
            rotation_data['in_progress'] = False
        
        return rotation_data
        
    except Exception as e:
        LOGGER.error(f"Error retrieving rotation status: {str(e)}")
        return None


def validate_rotation_config(rotation_config):
    """
    Validates rotation configuration parameters.
    
    Args:
        rotation_config (RotationConfig): Rotation configuration object.
    
    Returns:
        bool: True if configuration is valid, False otherwise.
    """
    if not rotation_config:
        LOGGER.error("Rotation configuration is missing")
        return False
    
    # Check if transition_period_seconds is positive and reasonable
    if rotation_config.transition_period_seconds <= 0:
        LOGGER.error("Transition period must be positive")
        return False
    
    if rotation_config.transition_period_seconds > 2592000:  # 30 days
        LOGGER.warning("Transition period is very long (> 30 days)")
    
    # Check if monitoring_interval_seconds is positive and less than transition period
    if rotation_config.monitoring_interval_seconds <= 0:
        LOGGER.error("Monitoring interval must be positive")
        return False
    
    if rotation_config.monitoring_interval_seconds >= rotation_config.transition_period_seconds:
        LOGGER.error("Monitoring interval must be less than transition period")
        return False
    
    return True


def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code.
    """
    parser = argparse.ArgumentParser(description="Rotate credentials in Conjur vault")
    parser.add_argument("client_id", help="Client ID for which to rotate the credential")
    parser.add_argument("--config", help="Path to configuration file")
    parser.add_argument("--transition-period", type=int, default=DEFAULT_TRANSITION_PERIOD_SECONDS,
                       help=f"Transition period in seconds (default: {DEFAULT_TRANSITION_PERIOD_SECONDS})")
    parser.add_argument("--monitoring-interval", type=int, default=DEFAULT_MONITORING_INTERVAL_SECONDS,
                       help=f"Monitoring interval in seconds (default: {DEFAULT_MONITORING_INTERVAL_SECONDS})")
    parser.add_argument("--output", help="Path to save rotation result (default: print to stdout)")
    parser.add_argument("--format", choices=["json", "text"], default="text",
                       help="Output format (default: text)")
    
    args = parser.parse_args()
    
    try:
        # Create Conjur configuration
        conjur_config = create_conjur_config(args.config)
        
        # Create rotation configuration
        rotation_config = get_rotation_config(args.config)
        
        # Override rotation config values if specified in command line
        if args.transition_period:
            rotation_config.transition_period_seconds = args.transition_period
        
        if args.monitoring_interval:
            rotation_config.monitoring_interval_seconds = args.monitoring_interval
        
        # Validate configurations
        if not conjur_config.validate():
            LOGGER.error("Invalid Conjur configuration")
            return 1
        
        if not validate_rotation_config(rotation_config):
            LOGGER.error("Invalid rotation configuration")
            return 1
        
        # Rotate credential with retry mechanism
        result = rotate_credential_with_retry(args.client_id, conjur_config, rotation_config)
        
        # Format output
        if args.format == "json":
            output = result.to_json()
        else:
            if result.success:
                output = (
                    f"Credential rotation successful for client_id: {result.client_id}\n"
                    f"Old version: {result.old_version}\n"
                    f"New version: {result.new_version}\n"
                    f"State: {result.state.name}\n"
                    f"Transition period: {result.transition_period_seconds} seconds"
                )
            else:
                output = (
                    f"Credential rotation failed for client_id: {result.client_id}\n"
                    f"Error: {result.error_message}\n"
                    f"State: {result.state.name if result.state else 'UNKNOWN'}"
                )
        
        # Output result
        if args.output:
            with open(args.output, 'w') as f:
                f.write(output)
            print(f"Rotation result saved to {args.output}")
        else:
            print(output)
        
        # Return appropriate exit code
        return 0 if result.success else 1
    
    except Exception as e:
        LOGGER.error(f"Error during credential rotation: {str(e)}")
        print(f"ERROR: {str(e)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())