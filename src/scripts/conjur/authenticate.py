"""
Module for authenticating with Conjur vault.

Provides functions to securely authenticate with Conjur vault using certificate-based
authentication and retrieve authentication tokens for subsequent API calls.
"""
import requests  # version 2.28.1
import base64    # standard library
import json      # standard library
import time      # standard library
import argparse  # standard library
import sys       # standard library

from .config import LOGGER, ConjurConfig, read_certificate
from .utils import (
    create_http_session,
    build_conjur_url,
    encode_credentials,
    parse_conjur_response,
    RetryHandler,
    ConjurAuthenticationError,
    ConjurConnectionError
)

# Global token cache - stores authentication tokens with expiration data
AUTH_TOKEN_CACHE = {}

# Default token expiration time in seconds (10 minutes)
TOKEN_EXPIRATION_SECONDS = 600


class AuthenticationResult:
    """Class representing the result of an authentication operation."""
    
    def __init__(self, token=None, expiration=None, success=False, error_message=None):
        """
        Initializes a new AuthenticationResult instance.
        
        Args:
            token (str, optional): Authentication token. Defaults to None.
            expiration (int, optional): Token expiration timestamp. Defaults to None.
            success (bool, optional): Whether authentication was successful. Defaults to False.
            error_message (str, optional): Error message if authentication failed. Defaults to None.
        """
        self.token = token
        self.expiration = expiration
        self.success = success
        self.error_message = error_message
    
    def is_valid(self):
        """
        Checks if the authentication result is valid.
        
        Returns:
            bool: True if valid, False otherwise.
        """
        return (
            self.success and
            self.token is not None and
            not self.is_expired()
        )
    
    def is_expired(self):
        """
        Checks if the token is expired.
        
        Returns:
            bool: True if expired, False otherwise.
        """
        if not self.expiration:
            return True
        
        current_time = int(time.time())
        return current_time >= self.expiration


def authenticate(conjur_config):
    """
    Authenticates with Conjur vault and returns an authentication token.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        str: Authentication token for subsequent API calls.
    
    Raises:
        ConjurConnectionError: If connection to Conjur fails.
        ConjurAuthenticationError: If authentication fails.
        ValueError: If configuration is invalid.
    """
    if not conjur_config.validate():
        raise ValueError("Invalid Conjur configuration")
    
    # Check for cached token
    cached_token = get_cached_token(conjur_config)
    if cached_token:
        LOGGER.debug("Using cached authentication token")
        return cached_token
    
    LOGGER.debug(f"Authenticating with Conjur vault at {conjur_config.url}")
    
    try:
        # Create session with TLS configuration
        session = create_http_session(conjur_config.cert_path)
        
        # In Conjur, certificate authentication typically uses the TLS client certificate
        if conjur_config.cert_path:
            try:
                # Configure session to use client certificate
                session.cert = conjur_config.cert_path
                
                LOGGER.debug(f"Using certificate authentication with {conjur_config.cert_path}")
            except Exception as e:
                raise ConjurAuthenticationError(f"Failed to setup certificate authentication: {str(e)}")
        
        # Build authentication URL
        auth_url = build_conjur_url(
            conjur_config.url,
            conjur_config.account,
            f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
        )
        
        headers = {
            "Accept": "application/json"
        }
        
        # Send authentication request
        # For certificate auth, the certificate is used via the session.cert property
        # If not using certificate auth, we need to get an API key first
        if not conjur_config.cert_path:
            LOGGER.warning("No certificate provided, falling back to API key authentication")
            
            # Step 1: Login to get API key
            login_url = build_conjur_url(
                conjur_config.url,
                conjur_config.account,
                f"/authn/{conjur_config.account}/login"
            )
            
            # Basic auth with login and empty password to get API key
            login_response = session.get(
                login_url, 
                auth=(conjur_config.authn_login, ""),
                headers=headers
            )
            
            api_key = parse_conjur_response(login_response)
            
            # Step 2: Authenticate with API key
            response = session.post(auth_url, data=api_key, headers=headers)
        else:
            # Certificate auth directly
            response = session.post(auth_url, headers=headers)
        
        # Parse response
        auth_token = parse_conjur_response(response)
        
        # Conjur returns the raw token which needs to be base64 encoded for use in headers
        if isinstance(auth_token, str):
            token_data = auth_token
        else:
            # If it's not a string, it might be JSON or another structure
            token_data = json.dumps(auth_token)
        
        # Ensure token is in bytes before base64 encoding
        if not isinstance(token_data, bytes):
            token_data = token_data.encode('utf-8')
        
        # Base64 encode the token for use in Authorization headers
        encoded_token = base64.b64encode(token_data).decode('utf-8')
        
        # Cache the token
        cache_token(conjur_config, encoded_token, TOKEN_EXPIRATION_SECONDS)
        
        LOGGER.info("Successfully authenticated with Conjur vault")
        return encoded_token
        
    except ConjurAuthenticationError as e:
        LOGGER.error(f"Authentication failed: {str(e)}")
        raise
    except ConjurConnectionError as e:
        LOGGER.error(f"Connection to Conjur failed: {str(e)}")
        raise
    except Exception as e:
        LOGGER.error(f"Unexpected error during authentication: {str(e)}")
        raise ConjurConnectionError(f"Authentication failed: {str(e)}", e)


def authenticate_with_retry(conjur_config, max_retries=3, backoff_factor=1.5):
    """
    Authenticates with Conjur vault with retry mechanism.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
        max_retries (int, optional): Maximum number of retry attempts. Defaults to 3.
        backoff_factor (float, optional): Backoff factor for retry delays. Defaults to 1.5.
    
    Returns:
        str: Authentication token for subsequent API calls.
    
    Raises:
        ConjurConnectionError: If connection to Conjur fails after all retries.
        ConjurAuthenticationError: If authentication fails.
    """
    retry_handler = RetryHandler(
        max_retries=max_retries,
        backoff_factor=backoff_factor,
        retryable_exceptions=[ConjurConnectionError, requests.exceptions.RequestException]
    )
    
    return retry_handler.execute(authenticate, conjur_config)


def get_cached_token(conjur_config):
    """
    Retrieves a cached authentication token if valid.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
    
    Returns:
        str or None: Cached token if valid, None otherwise.
    """
    # Generate cache key from configuration
    cache_key = f"{conjur_config.url}_{conjur_config.account}_{conjur_config.authn_login}"
    
    # Check if key exists in cache
    if cache_key in AUTH_TOKEN_CACHE:
        cached_data = AUTH_TOKEN_CACHE[cache_key]
        
        # Check if token is still valid
        current_time = int(time.time())
        if current_time < cached_data['expiration']:
            LOGGER.debug("Retrieved valid cached token")
            return cached_data['token']
        else:
            LOGGER.debug("Cached token has expired")
    
    return None


def cache_token(conjur_config, token, expiration_seconds=TOKEN_EXPIRATION_SECONDS):
    """
    Caches an authentication token with expiration time.
    
    Args:
        conjur_config (ConjurConfig): Conjur configuration object.
        token (str): Authentication token to cache.
        expiration_seconds (int, optional): Token lifetime in seconds. 
            Defaults to TOKEN_EXPIRATION_SECONDS.
    """
    # Generate cache key from configuration
    cache_key = f"{conjur_config.url}_{conjur_config.account}_{conjur_config.authn_login}"
    
    # Calculate expiration timestamp
    expiration = int(time.time()) + expiration_seconds
    
    # Store token in cache
    AUTH_TOKEN_CACHE[cache_key] = {
        'token': token,
        'expiration': expiration
    }
    
    LOGGER.debug(f"Cached authentication token with expiration in {expiration_seconds} seconds")


def clear_token_cache(conjur_config=None):
    """
    Clears the authentication token cache.
    
    Args:
        conjur_config (ConjurConfig, optional): If provided, only clears the token
            for this specific configuration. If None, clears all tokens. Defaults to None.
    """
    global AUTH_TOKEN_CACHE
    
    if conjur_config:
        # Clear specific token
        cache_key = f"{conjur_config.url}_{conjur_config.account}_{conjur_config.authn_login}"
        if cache_key in AUTH_TOKEN_CACHE:
            del AUTH_TOKEN_CACHE[cache_key]
            LOGGER.debug("Cleared specific authentication token from cache")
    else:
        # Clear all tokens
        AUTH_TOKEN_CACHE = {}
        LOGGER.debug("Cleared all authentication tokens from cache")


def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure).
    """
    parser = argparse.ArgumentParser(description="Authenticate with Conjur vault")
    parser.add_argument("--config", help="Path to configuration file")
    parser.add_argument("--output", help="Path to save token (default: print to stdout)")
    
    args = parser.parse_args()
    
    try:
        # Create configuration from file or environment
        from .config import create_conjur_config
        conjur_config = create_conjur_config(args.config)
        
        # Authenticate with retry mechanism
        token = authenticate_with_retry(conjur_config)
        
        # Print token to stdout or save to file if specified
        if args.output:
            with open(args.output, 'w') as f:
                f.write(token)
            print(f"Authentication token saved to {args.output}")
        else:
            print(token)
            
        return 0
        
    except Exception as e:
        LOGGER.error(f"Authentication failed: {str(e)}")
        print(f"ERROR: {str(e)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())