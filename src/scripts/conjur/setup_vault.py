#!/usr/bin/env python
"""
Script for setting up and initializing Conjur vault for the Payment API Security Enhancement project.
This script handles the initial configuration of Conjur vault, creating necessary policies,
and setting up authentication mechanisms required for secure credential storage and retrieval.
"""
import os
import sys
import argparse
import yaml
import requests
import json

from .config import LOGGER, ConjurConfig, create_conjur_config, read_certificate
from .utils import (
    create_http_session,
    build_conjur_url,
    parse_conjur_response,
    RetryHandler,
    ConjurConnectionError
)
from .authenticate import authenticate_with_retry
from .store_credentials import generate_credential, store_credential_with_retry

# Global constants
DEFAULT_POLICY_DIR = "../../../infrastructure/conjur/policy/"
DEFAULT_POLICIES = ['eapi-policy.yml', 'sapi-policy.yml', 'rotation-policy.yml']


class SetupResult:
    """Class representing the result of a vault setup operation."""
    
    def __init__(self, success, applied_policies=None, credentials=None, error_message=None):
        """
        Initializes a new SetupResult instance.
        
        Args:
            success (bool): Whether the setup was successful.
            applied_policies (list, optional): List of policies that were applied. Defaults to None.
            credentials (dict, optional): Generated credentials. Defaults to None.
            error_message (str, optional): Error message if setup failed. Defaults to None.
        """
        self.success = success
        self.applied_policies = applied_policies or []
        self.credentials = credentials or {}
        self.error_message = error_message
    
    def to_dict(self):
        """
        Converts the result to a dictionary.
        
        Returns:
            dict: Dictionary representation of the result.
        """
        return {
            'success': self.success,
            'applied_policies': self.applied_policies,
            'credentials': self.credentials,
            'error_message': self.error_message
        }
    
    def to_json(self):
        """
        Converts the result to a JSON string.
        
        Returns:
            str: JSON string representation of the result.
        """
        return json.dumps(self.to_dict())


def setup_vault(conjur_config, policy_dir=DEFAULT_POLICY_DIR, policies=DEFAULT_POLICIES):
    """
    Sets up Conjur vault with necessary policies and initial configuration.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
        policy_dir (str, optional): Directory containing policy files. Defaults to DEFAULT_POLICY_DIR.
        policies (list, optional): List of policy files to apply. Defaults to DEFAULT_POLICIES.
    
    Returns:
        bool: True if setup was successful, False otherwise.
    """
    # Validate conjur_config
    if not conjur_config.validate():
        LOGGER.error("Invalid Conjur configuration")
        return False
    
    try:
        # Authenticate with Conjur vault
        auth_token = authenticate_with_retry(conjur_config)
        
        # Create HTTP session with TLS configuration
        session = create_http_session(conjur_config.cert_path)
        
        # Apply each policy
        applied_policies = []
        for policy_file in policies:
            try:
                # Load policy file
                policy_content = load_policy_file(policy_dir, policy_file)
                
                # Apply policy
                if apply_policy(policy_content, policy_file, auth_token, session, conjur_config):
                    applied_policies.append(policy_file)
                    LOGGER.info(f"Successfully applied policy: {policy_file}")
                else:
                    LOGGER.error(f"Failed to apply policy: {policy_file}")
            except Exception as e:
                LOGGER.error(f"Error applying policy {policy_file}: {str(e)}")
        
        # Check if all policies were applied
        if len(applied_policies) == len(policies):
            LOGGER.info("Vault setup completed successfully")
            return True
        else:
            LOGGER.warning(f"Vault setup partially completed. Applied {len(applied_policies)} of {len(policies)} policies")
            return False
            
    except Exception as e:
        LOGGER.error(f"Error setting up Conjur vault: {str(e)}")
        return False


def load_policy_file(policy_dir, policy_file):
    """
    Loads a policy file from the specified directory.
    
    Args:
        policy_dir (str): Directory containing policy files.
        policy_file (str): Name of the policy file to load.
    
    Returns:
        str: Content of the policy file.
        
    Raises:
        FileNotFoundError: If policy file is not found.
        IOError: If error reading policy file.
    """
    policy_path = os.path.join(policy_dir, policy_file)
    
    if not os.path.isfile(policy_path):
        LOGGER.error(f"Policy file not found: {policy_path}")
        raise FileNotFoundError(f"Policy file not found: {policy_path}")
    
    try:
        with open(policy_path, 'r') as f:
            policy_content = f.read()
        return policy_content
    except Exception as e:
        LOGGER.error(f"Error reading policy file {policy_path}: {str(e)}")
        raise IOError(f"Error reading policy file: {str(e)}")


def apply_policy(policy_content, policy_name, auth_token, session, conjur_config):
    """
    Applies a policy to Conjur vault.
    
    Args:
        policy_content (str): Content of the policy file.
        policy_name (str): Name of the policy file.
        auth_token (str): Authentication token.
        session (requests.Session): HTTP session.
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        bool: True if policy was applied successfully, False otherwise.
    """
    try:
        # Extract policy ID from filename (remove extension)
        policy_id = os.path.splitext(policy_name)[0]
        
        # Build policy URL
        policy_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/policies/{conjur_config.account}/policy/{policy_id}"
        )
        
        # Set headers with authentication token
        headers = {
            "Authorization": f"Token token=\"{auth_token}\"",
            "Content-Type": "application/x-yaml"
        }
        
        # Send request to apply policy
        response = session.put(
            policy_url,
            data=policy_content,
            headers=headers
        )
        
        # Parse response
        result = parse_conjur_response(response)
        
        LOGGER.info(f"Policy {policy_name} applied successfully")
        return True
        
    except ConjurConnectionError as e:
        LOGGER.error(f"Connection error applying policy {policy_name}: {str(e)}")
        return False
    except Exception as e:
        LOGGER.error(f"Error applying policy {policy_name}: {str(e)}")
        return False


def setup_initial_credentials(conjur_config, client_ids):
    """
    Sets up initial credentials in Conjur vault.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
        client_ids (list): List of client IDs for which to generate credentials.
    
    Returns:
        dict: Dictionary of generated credentials.
    """
    # Initialize result dictionary
    generated_credentials = {}
    
    for client_id in client_ids:
        try:
            # Generate credential
            credential = generate_credential(client_id)
            client_id = credential['client_id']
            client_secret = credential['client_secret']
            
            # Store credential in Conjur vault
            if store_credential_with_retry(client_id, client_secret, conjur_config):
                LOGGER.info(f"Successfully stored credential for client_id: {client_id}")
                generated_credentials[client_id] = credential
            else:
                LOGGER.error(f"Failed to store credential for client_id: {client_id}")
        except Exception as e:
            LOGGER.error(f"Error setting up credential for client_id {client_id}: {str(e)}")
    
    return generated_credentials


def validate_setup(conjur_config):
    """
    Validates that Conjur vault setup was successful.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        bool: True if validation was successful, False otherwise.
    """
    try:
        # Authenticate with Conjur vault
        auth_token = authenticate_with_retry(conjur_config)
        
        # Create HTTP session with TLS configuration
        session = create_http_session(conjur_config.cert_path)
        
        # Check if we can access policies
        policy_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/resources/{conjur_config.account}/policy"
        )
        
        headers = {
            "Authorization": f"Token token=\"{auth_token}\"",
            "Accept": "application/json"
        }
        
        response = session.get(policy_url, headers=headers)
        parse_conjur_response(response)
        
        # Check if credential paths are accessible
        # This doesn't check actual credentials, just that the structure exists
        resource_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/resources/{conjur_config.account}"
        )
        
        response = session.get(resource_url, headers=headers)
        parse_conjur_response(response)
        
        LOGGER.info("Vault setup validation successful")
        return True
        
    except Exception as e:
        LOGGER.error(f"Vault setup validation failed: {str(e)}")
        return False


def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code.
    """
    parser = argparse.ArgumentParser(description="Set up Conjur vault for Payment API Security Enhancement")
    parser.add_argument("--config", help="Path to configuration file")
    parser.add_argument("--policy-dir", default=DEFAULT_POLICY_DIR, help="Directory containing policy files")
    parser.add_argument("--client-ids", nargs="+", help="Client IDs for which to generate credentials")
    parser.add_argument("--validate-only", action="store_true", help="Only validate vault setup")
    
    args = parser.parse_args()
    
    try:
        # Create configuration from file or environment
        conjur_config = create_conjur_config(args.config)
        
        # Validate configuration
        if not conjur_config.validate():
            LOGGER.error("Invalid Conjur configuration")
            return 1
        
        # If validate-only flag is set, only run validation
        if args.validate_only:
            validation_success = validate_setup(conjur_config)
            result = SetupResult(
                validation_success, 
                [], 
                {}, 
                "Validation successful" if validation_success else "Validation failed"
            )
            print(result.to_json())
            return 0 if validation_success else 1
        
        # Set up vault with policies
        setup_success = setup_vault(conjur_config, args.policy_dir, DEFAULT_POLICIES)
        applied_policies = DEFAULT_POLICIES if setup_success else []
        
        # Set up initial credentials if client_ids are provided
        credentials = {}
        if args.client_ids and setup_success:
            credentials = setup_initial_credentials(conjur_config, args.client_ids)
        
        # Create and print result
        result = SetupResult(
            setup_success,
            applied_policies,
            credentials,
            "Setup successful" if setup_success else "Setup failed"
        )
        
        print(result.to_json())
        return 0 if result.success else 1
        
    except Exception as e:
        LOGGER.error(f"Error: {str(e)}")
        result = SetupResult(False, [], {}, str(e))
        print(result.to_json(), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())