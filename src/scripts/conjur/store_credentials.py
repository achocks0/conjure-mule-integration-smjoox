"""
Module for storing credentials in Conjur vault.

Provides functions to securely store client credentials in Conjur vault
with proper error handling, retry mechanisms, and validation.
This module is essential for the credential rotation process and initial credential setup.
"""
import requests  # version 2.28.1
import json      # standard library
import argparse  # standard library
import sys       # standard library
import uuid      # standard library

from .config import LOGGER, ConjurConfig, get_credential_path, create_conjur_config
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


class StoreResult:
    """Class representing the result of a credential storage operation."""
    
    def __init__(self, success, client_id, error_message=None):
        """
        Initializes a new StoreResult instance.
        
        Args:
            success (bool): Whether the operation was successful.
            client_id (str): The client ID for which credentials were stored.
            error_message (str, optional): Error message if operation failed. Defaults to None.
        """
        self.success = success
        self.client_id = client_id
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
            'error_message': self.error_message
        }
    
    def to_json(self):
        """
        Converts the result to a JSON string.
        
        Returns:
            str: JSON string representation of the result.
        """
        return json.dumps(self.to_dict())


def store_credential(client_id, client_secret, conjur_config):
    """
    Stores a credential in Conjur vault.
    
    Args:
        client_id (str): Client ID for the credential.
        client_secret (str): Client secret to store.
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        bool: True if successful, False otherwise.
    
    Raises:
        ConjurConnectionError: If connection to Conjur fails.
        ConjurPermissionError: If permission denied.
        ValueError: If input validation fails.
    """
    # Validate input parameters
    if not validate_credential_data(client_id, client_secret):
        raise ValueError("Invalid credential data")
    
    try:
        # Get authentication token
        auth_token = authenticate_with_retry(conjur_config)
        
        # Create HTTP session
        session = create_http_session(conjur_config.cert_path)
        
        # Get credential path
        credential_path = get_credential_path(client_id, conjur_config)
        
        # Build URL for storing credential
        credential_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/secrets/{conjur_config.account}/variable/{credential_path}"
        )
        
        # Prepare credential data as JSON
        credential_data = prepare_credential_data(client_id, client_secret)
        credential_json = json.dumps(credential_data)
        
        LOGGER.debug(f"Storing credential for client_id: {client_id}")
        
        # Set headers with authentication token
        headers = {
            "Authorization": f"Token token=\"{auth_token}\"",
            "Content-Type": "application/json"
        }
        
        # Send request to store credential
        response = session.post(
            credential_url,
            data=credential_json,
            headers=headers
        )
        
        # Parse response
        result = parse_conjur_response(response)
        
        # Log success (without exposing sensitive data)
        LOGGER.info(f"Successfully stored credential for client_id: {client_id}")
        return True
        
    except ConjurConnectionError as e:
        LOGGER.error(f"Connection to Conjur failed while storing credential: {str(e)}")
        raise
    except ConjurPermissionError as e:
        LOGGER.error(f"Permission denied while storing credential: {str(e)}")
        raise
    except Exception as e:
        LOGGER.error(f"Unexpected error while storing credential: {str(e)}")
        return False


def store_credential_with_retry(client_id, client_secret, conjur_config, max_retries=3, backoff_factor=1.5):
    """
    Stores a credential in Conjur vault with retry mechanism.
    
    Args:
        client_id (str): Client ID for the credential.
        client_secret (str): Client secret to store.
        conjur_config (ConjurConfig): Conjur configuration object.
        max_retries (int, optional): Maximum number of retry attempts. Defaults to 3.
        backoff_factor (float, optional): Backoff factor for retry delays. Defaults to 1.5.
    
    Returns:
        bool: True if successful, False otherwise.
    """
    retry_handler = RetryHandler(
        max_retries=max_retries,
        backoff_factor=backoff_factor,
        retryable_exceptions=[ConjurConnectionError, requests.exceptions.RequestException]
    )
    
    try:
        return retry_handler.execute(store_credential, client_id, client_secret, conjur_config)
    except Exception as e:
        LOGGER.error(f"Failed to store credential after {max_retries} retries: {str(e)}")
        return False


def validate_credential_data(client_id, client_secret):
    """
    Validates credential data before storing.
    
    Args:
        client_id (str): Client ID to validate.
        client_secret (str): Client secret to validate.
    
    Returns:
        bool: True if valid, False otherwise.
    """
    # Check if client_id is valid
    if not client_id or not isinstance(client_id, str) or len(client_id) < 3:
        LOGGER.error("Invalid client_id: must be a non-empty string with at least 3 characters")
        return False
    
    # Check if client_secret is valid
    if not client_secret or not isinstance(client_secret, str) or len(client_secret) < 16:
        LOGGER.error("Invalid client_secret: must be a non-empty string with at least 16 characters")
        return False
    
    # Additional validation for client_id format (e.g., alphanumeric with hyphens)
    import re
    if not re.match(r'^[a-zA-Z0-9_-]+$', client_id):
        LOGGER.error("Invalid client_id format: must contain only alphanumeric characters, underscores, and hyphens")
        return False
    
    # Check client_secret complexity (should have a mix of character types)
    has_upper = any(c.isupper() for c in client_secret)
    has_lower = any(c.islower() for c in client_secret)
    has_digit = any(c.isdigit() for c in client_secret)
    has_special = any(not c.isalnum() for c in client_secret)
    
    if not (has_upper and has_lower and has_digit and has_special):
        LOGGER.error("Invalid client_secret: must contain uppercase, lowercase, digits, and special characters")
        return False
    
    return True


def prepare_credential_data(client_id, client_secret):
    """
    Prepares credential data for storage in Conjur vault.
    
    Args:
        client_id (str): Client ID for the credential.
        client_secret (str): Client secret to store.
    
    Returns:
        dict: Structured credential data.
    """
    # Create timestamp for when the credential was created
    from datetime import datetime
    timestamp = datetime.utcnow().isoformat() + 'Z'
    
    # Generate a version identifier (useful for rotation)
    version = str(uuid.uuid4())
    
    # Create credential data structure
    credential_data = {
        'client_id': client_id,
        'client_secret': client_secret,
        'created_at': timestamp,
        'updated_at': timestamp,
        'version': version,
        'status': 'active'
    }
    
    return credential_data


def generate_credential(client_id=None, secret_length=32):
    """
    Generates a new credential with secure random client_secret.
    
    Args:
        client_id (str, optional): Client ID for the credential. If None, a UUID will be generated.
        secret_length (int, optional): Length of the generated secret. Defaults to 32.
    
    Returns:
        dict: Generated credential data.
    """
    # Generate client_id if not provided
    if not client_id:
        client_id = f"client-{uuid.uuid4()}"
    
    # Generate a secure random secret with required complexity
    import secrets
    import string
    
    alphabet = string.ascii_letters + string.digits + string.punctuation
    while True:
        client_secret = ''.join(secrets.choice(alphabet) for _ in range(secret_length))
        
        # Ensure the secret meets complexity requirements
        has_upper = any(c.isupper() for c in client_secret)
        has_lower = any(c.islower() for c in client_secret)
        has_digit = any(c.isdigit() for c in client_secret)
        has_special = any(not c.isalnum() for c in client_secret)
        
        if has_upper and has_lower and has_digit and has_special:
            break
    
    return {
        'client_id': client_id,
        'client_secret': client_secret
    }


def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure).
    """
    parser = argparse.ArgumentParser(description="Store credentials in Conjur vault")
    parser.add_argument("--client-id", help="Client ID for the credential")
    parser.add_argument("--client-secret", help="Client secret to store")
    parser.add_argument("--config", help="Path to configuration file")
    parser.add_argument("--generate", action="store_true", help="Generate a new credential")
    parser.add_argument("--secret-length", type=int, default=32, help="Length of generated secret")
    
    args = parser.parse_args()
    
    try:
        # Create configuration from file or environment
        conjur_config = create_conjur_config(args.config)
        
        # Generate credential if requested
        if args.generate:
            credential = generate_credential(args.client_id, args.secret_length)
            client_id = credential['client_id']
            client_secret = credential['client_secret']
            
            # Print generated credential to stdout
            print(json.dumps(credential, indent=2))
        else:
            # Use provided client_id and client_secret
            client_id = args.client_id
            client_secret = args.client_secret
            
            if not client_id or not client_secret:
                print("ERROR: client-id and client-secret are required unless --generate is specified", file=sys.stderr)
                return 1
        
        # Store credential with retry mechanism
        success = store_credential_with_retry(client_id, client_secret, conjur_config)
        
        if success:
            print(f"Successfully stored credential for client_id: {client_id}")
            return 0
        else:
            print(f"Failed to store credential for client_id: {client_id}", file=sys.stderr)
            return 1
            
    except Exception as e:
        print(f"ERROR: {str(e)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())