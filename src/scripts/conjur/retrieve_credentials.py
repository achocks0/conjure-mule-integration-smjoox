"""
Module for retrieving credentials from Conjur vault.

Provides functions to securely retrieve client credentials stored in Conjur vault
with proper error handling, retry mechanisms, and caching capabilities.

This module is part of the Payment API Security Enhancement project and addresses
the requirements for Conjur Vault integration, including credential retrieval
and graceful handling of connection failures.
"""
import requests  # version 2.28.1
import json      # standard library
import time      # standard library
import argparse  # standard library
import sys       # standard library

from .config import LOGGER, ConjurConfig, get_credential_path, create_conjur_config
from .utils import (
    create_http_session,
    build_conjur_url,
    parse_conjur_response,
    RetryHandler,
    ConjurConnectionError,
    ConjurNotFoundError,
    ConjurPermissionError,
    sanitize_log_data
)
from .authenticate import authenticate_with_retry

# Global cache for credentials to minimize vault requests
CREDENTIAL_CACHE = {}

# Default time in seconds for cached credentials to be valid
CACHE_EXPIRATION_SECONDS = 300  # 5 minutes


class CredentialResult:
    """Class representing the result of a credential retrieval operation."""

    def __init__(self, credential=None, expiration=None, success=False, error_message=None):
        """
        Initializes a new CredentialResult instance.

        Args:
            credential (dict, optional): Credential data containing client_id and client_secret.
                Defaults to None.
            expiration (int, optional): Credential expiration timestamp. Defaults to None.
            success (bool, optional): Whether retrieval was successful. Defaults to False.
            error_message (str, optional): Error message if retrieval failed. Defaults to None.
        """
        self.credential = credential
        self.expiration = expiration
        self.success = success
        self.error_message = error_message

    def is_valid(self):
        """
        Checks if the credential result is valid.

        A valid result must have success status, contain credential data,
        and not be expired.

        Returns:
            bool: True if valid, False otherwise.
        """
        return (
            self.success and
            self.credential is not None and
            not self.is_expired()
        )

    def is_expired(self):
        """
        Checks if the credential is expired.

        Compares the current timestamp with the expiration timestamp.

        Returns:
            bool: True if expired, False otherwise.
        """
        if not self.expiration:
            return True

        current_time = int(time.time())
        return current_time >= self.expiration


def retrieve_credential(client_id, conjur_config):
    """
    Retrieves a credential from Conjur vault.

    This function attempts to retrieve a credential from the local cache first.
    If not found or expired, it will authenticate with Conjur vault and retrieve
    the credential, then cache it for future use.

    Args:
        client_id (str): Client ID for which to retrieve the credential.
        conjur_config (ConjurConfig): Conjur configuration object.

    Returns:
        dict: Credential data containing client_id and client_secret.

    Raises:
        ConjurConnectionError: If connection to Conjur fails.
        ConjurNotFoundError: If credential is not found.
        ConjurPermissionError: If permission denied.
        ValueError: If configuration is invalid.
    """
    if not conjur_config.validate():
        raise ValueError("Invalid Conjur configuration")

    # Check for cached credential
    cached_credential = get_cached_credential(client_id)
    if cached_credential:
        LOGGER.debug(f"Using cached credential for client_id: {client_id}")
        return cached_credential

    LOGGER.debug(f"Retrieving credential for client_id: {client_id}")

    try:
        # Get authentication token
        auth_token = authenticate_with_retry(conjur_config)

        # Create session with TLS configuration
        session = create_http_session(conjur_config.cert_path)

        # Build credential path
        credential_path = get_credential_path(client_id, conjur_config)

        # Build credential URL
        credential_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/secrets/{conjur_config.account}/variable/{credential_path}"
        )

        # Set up headers with authentication token
        headers = {
            "Authorization": f"Token token=\"{auth_token}\"",
            "Accept": "application/json"
        }

        # Send request to retrieve credential
        start_time = time.time()
        response = session.get(credential_url, headers=headers)
        elapsed_time = (time.time() - start_time) * 1000  # Convert to milliseconds

        # Log retrieval time for performance monitoring (requirement: <100ms)
        LOGGER.debug(f"Credential retrieval time: {elapsed_time:.2f}ms")

        # Parse response
        response_data = parse_conjur_response(response)

        # Extract credential data
        credential = parse_credential(client_id, response_data)

        # Cache credential
        cache_credential(client_id, credential)

        LOGGER.info(f"Successfully retrieved credential for client_id: {client_id}")
        return credential

    except ConjurNotFoundError as e:
        LOGGER.error(f"Credential not found for client_id {client_id}: {str(e)}")
        raise
    except ConjurPermissionError as e:
        LOGGER.error(f"Permission denied for client_id {client_id}: {str(e)}")
        raise
    except ConjurConnectionError as e:
        LOGGER.error(f"Connection to Conjur failed while retrieving credential: {str(e)}")
        raise
    except Exception as e:
        LOGGER.error(f"Unexpected error during credential retrieval: {str(e)}")
        raise ConjurConnectionError(f"Credential retrieval failed: {str(e)}", e)


def retrieve_credential_with_retry(client_id, conjur_config, max_retries=3, backoff_factor=1.5):
    """
    Retrieves a credential from Conjur vault with retry mechanism.

    This function adds resilience by retrying the credential retrieval operation
    in case of transient failures. If all retries fail, it will attempt to use
    a cached credential as a fallback mechanism.

    Args:
        client_id (str): Client ID for which to retrieve the credential.
        conjur_config (ConjurConfig): Conjur configuration object.
        max_retries (int, optional): Maximum number of retry attempts. Defaults to 3.
        backoff_factor (float, optional): Backoff factor for retry delays. Defaults to 1.5.

    Returns:
        dict: Credential data containing client_id and client_secret.

    Raises:
        ConjurConnectionError: If connection to Conjur fails after all retries and no
            valid cached credential is available.
        ConjurNotFoundError: If credential is not found.
        ConjurPermissionError: If permission denied.
    """
    retry_handler = RetryHandler(
        max_retries=max_retries,
        backoff_factor=backoff_factor,
        retryable_exceptions=[ConjurConnectionError, requests.exceptions.RequestException]
    )

    try:
        return retry_handler.execute(retrieve_credential, client_id, conjur_config)
    except Exception as e:
        # Check for cached credential as fallback
        cached_credential = get_cached_credential(client_id)
        if cached_credential:
            LOGGER.warning(
                f"Using cached credential for client_id {client_id} after retrieval failure: {str(e)}"
            )
            return cached_credential
        
        # Log sanitized error to avoid exposing sensitive information
        LOGGER.error(f"Failed to retrieve credential for client_id {client_id} after {max_retries} retries")
        raise


def get_cached_credential(client_id):
    """
    Retrieves a cached credential if valid.

    Args:
        client_id (str): Client ID for which to retrieve the cached credential.

    Returns:
        dict or None: Cached credential if valid, None otherwise.
    """
    # Check if client_id exists in cache
    if client_id in CREDENTIAL_CACHE:
        cached_data = CREDENTIAL_CACHE[client_id]

        # Check if credential is still valid
        current_time = int(time.time())
        if current_time < cached_data['expiration']:
            LOGGER.debug(f"Retrieved valid cached credential for client_id: {client_id}")
            return cached_data['credential']
        else:
            LOGGER.debug(f"Cached credential for client_id {client_id} has expired")

    return None


def cache_credential(client_id, credential, expiration_seconds=CACHE_EXPIRATION_SECONDS):
    """
    Caches a credential with expiration time.

    Storing credentials in cache reduces the need for frequent Conjur vault access,
    improving performance and resilience.

    Args:
        client_id (str): Client ID associated with the credential.
        credential (dict): Credential data to cache.
        expiration_seconds (int, optional): Credential cache lifetime in seconds.
            Defaults to CACHE_EXPIRATION_SECONDS.
    """
    # Calculate expiration timestamp
    expiration = int(time.time()) + expiration_seconds

    # Store credential in cache
    CREDENTIAL_CACHE[client_id] = {
        'credential': credential,
        'expiration': expiration
    }

    # Log caching without exposing any sensitive data
    LOGGER.debug(
        f"Cached credential for client_id {client_id} with expiration in {expiration_seconds} seconds"
    )


def clear_credential_cache(client_id=None):
    """
    Clears the credential cache.

    This function is useful during credential rotation or when credentials
    need to be refreshed from the source.

    Args:
        client_id (str, optional): If provided, only clears the credential
            for this specific client_id. If None, clears all credentials. Defaults to None.
    """
    global CREDENTIAL_CACHE

    if client_id:
        # Clear specific credential
        if client_id in CREDENTIAL_CACHE:
            del CREDENTIAL_CACHE[client_id]
            LOGGER.debug(f"Cleared cached credential for client_id: {client_id}")
    else:
        # Clear all credentials
        CREDENTIAL_CACHE = {}
        LOGGER.debug("Cleared all cached credentials")


def parse_credential(client_id, response_data):
    """
    Parses credential data from Conjur response.

    Handles different response formats to extract the credential data and
    structure it consistently.

    Args:
        client_id (str): Client ID associated with the credential.
        response_data (str): Response data from Conjur.

    Returns:
        dict: Structured credential data containing client_id and client_secret.
    """
    # Try to parse as JSON first
    try:
        if isinstance(response_data, str):
            data = json.loads(response_data)
        else:
            data = response_data

        # If it's already a dictionary with the expected structure, return it
        if isinstance(data, dict) and 'client_id' in data and 'client_secret' in data:
            return data

        # If it's a dictionary but doesn't have expected structure,
        # try to extract client_secret or use the whole response as client_secret
        if isinstance(data, dict):
            client_secret = data.get('client_secret', json.dumps(data))
        else:
            client_secret = response_data

    except (json.JSONDecodeError, TypeError):
        # If not valid JSON, treat the entire response as the client secret
        client_secret = response_data

    # Create credential dictionary
    credential = {
        'client_id': client_id,
        'client_secret': client_secret
    }

    # Log successful parsing without exposing sensitive data
    LOGGER.debug(f"Successfully parsed credential data for client_id: {client_id}")

    return credential


def main():
    """
    Main function for CLI usage.

    Provides command-line interface for retrieving credentials from Conjur vault.

    Returns:
        int: Exit code (0 for success, non-zero for failure).
    """
    parser = argparse.ArgumentParser(description="Retrieve credentials from Conjur vault")
    parser.add_argument("client_id", help="Client ID for which to retrieve the credential")
    parser.add_argument("--config", help="Path to configuration file")
    parser.add_argument("--output", help="Path to save credential (default: print to stdout)")
    parser.add_argument("--format", choices=["json", "raw"], default="json",
                      help="Output format (default: json)")

    args = parser.parse_args()

    try:
        # Create configuration from file or environment
        conjur_config = create_conjur_config(args.config)

        # Retrieve credential with retry mechanism
        credential = retrieve_credential_with_retry(args.client_id, conjur_config)

        # Format output
        if args.format == "json":
            output = json.dumps(credential, indent=2)
        else:
            output = credential['client_secret']

        # Output credential to stdout or file
        if args.output:
            with open(args.output, 'w') as f:
                f.write(output)
            print(f"Credential saved to {args.output}")
        else:
            print(output)

        return 0

    except Exception as e:
        # Log error without exposing sensitive information
        LOGGER.error(f"Credential retrieval failed: {str(e)}")
        print(f"ERROR: {str(e)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())