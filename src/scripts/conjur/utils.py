"""
Utility module for Conjur vault integration providing common functions for HTTP requests,
error handling, retry mechanisms, and secure data handling.
"""
import requests  # version 2.28.1
import urllib3  # version 1.26.12
import logging  # standard library
import json  # standard library
import base64  # standard library
import time  # standard library
import random  # standard library

# Configure logger
LOGGER = logging.getLogger('conjur.utils')

# Default values for configuration
DEFAULT_TIMEOUT = 10
DEFAULT_MAX_RETRIES = 3
DEFAULT_BACKOFF_FACTOR = 1.5
DEFAULT_JITTER_FACTOR = 0.1
SENSITIVE_FIELDS = ['client_secret', 'password', 'api_key', 'secret']


class ConjurError(Exception):
    """Base exception class for Conjur-related errors."""
    
    def __init__(self, message, original_exception=None):
        super().__init__(message)
        self.message = message
        self.original_exception = original_exception


class ConjurConnectionError(ConjurError):
    """Exception raised for connection errors to Conjur vault."""
    
    def __init__(self, message, original_exception=None):
        super().__init__(message, original_exception)


class ConjurAuthenticationError(ConjurError):
    """Exception raised for authentication errors with Conjur vault."""
    
    def __init__(self, message, original_exception=None):
        super().__init__(message, original_exception)


class ConjurNotFoundError(ConjurError):
    """Exception raised when a resource is not found in Conjur vault."""
    
    def __init__(self, message, original_exception=None):
        super().__init__(message, original_exception)


class ConjurPermissionError(ConjurError):
    """Exception raised for permission errors with Conjur vault."""
    
    def __init__(self, message, original_exception=None):
        super().__init__(message, original_exception)


def create_http_session(cert_path=None, timeout=DEFAULT_TIMEOUT):
    """
    Creates an HTTP session with proper TLS configuration.
    
    Args:
        cert_path (str, optional): Path to certificate for verification. Defaults to None.
        timeout (int, optional): Default timeout for requests in seconds. Defaults to DEFAULT_TIMEOUT.
    
    Returns:
        requests.Session: Configured HTTP session.
    """
    session = requests.Session()
    
    # Configure TLS 1.2+
    # Create an SSL adapter that enforces TLS v1.2 or higher
    class Tls12HttpAdapter(requests.adapters.HTTPAdapter):
        def init_poolmanager(self, *args, **kwargs):
            context = urllib3.util.ssl_.create_urllib3_context(
                ssl_version=getattr(urllib3.util.ssl_, "PROTOCOL_TLS"),
                cert_reqs=urllib3.util.ssl_.CERT_REQUIRED,
                options=getattr(urllib3.util.ssl_, "OP_NO_SSLv2") | 
                        getattr(urllib3.util.ssl_, "OP_NO_SSLv3") | 
                        getattr(urllib3.util.ssl_, "OP_NO_TLSv1") | 
                        getattr(urllib3.util.ssl_, "OP_NO_TLSv1_1")
            )
            
            # Set strong cipher suites
            context.set_ciphers('ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384')
            
            kwargs['ssl_context'] = context
            return super().init_poolmanager(*args, **kwargs)
    
    # Mount the TLS adapter for both http and https
    session.mount('https://', Tls12HttpAdapter())
    
    # Set default timeout
    session.timeout = timeout
    
    # Configure certificate verification if provided
    if cert_path:
        session.verify = cert_path
    
    LOGGER.debug("Created HTTP session with TLS 1.2+ configuration")
    return session


def build_conjur_url(base_url, account, endpoint_path):
    """
    Builds a URL for Conjur API endpoints.
    
    Args:
        base_url (str): Base URL of the Conjur server.
        account (str): Conjur account name.
        endpoint_path (str): API endpoint path.
    
    Returns:
        str: Full URL for the Conjur API endpoint.
    """
    # Ensure base_url doesn't end with a slash
    if base_url.endswith('/'):
        base_url = base_url[:-1]
    
    # Ensure endpoint_path starts with a slash
    if not endpoint_path.startswith('/'):
        endpoint_path = '/' + endpoint_path
    
    return f"{base_url}{endpoint_path}".format(account=account)


def encode_credentials(username, password):
    """
    Encodes credentials for Conjur authentication.
    
    Args:
        username (str): Username or login.
        password (str): Password or API key.
    
    Returns:
        str: Base64 encoded credentials.
    """
    credentials = f"{username}:{password}"
    credentials_bytes = credentials.encode('utf-8')
    encoded = base64.b64encode(credentials_bytes).decode('ascii')
    return encoded


def parse_conjur_response(response):
    """
    Parses the response from Conjur API.
    
    Args:
        response (requests.Response): Response from Conjur API.
    
    Returns:
        dict or str: Parsed response data.
    
    Raises:
        ConjurConnectionError: If connection error occurred.
        ConjurAuthenticationError: If authentication failed.
        ConjurNotFoundError: If resource not found.
        ConjurPermissionError: If permission denied.
        ConjurError: For other errors.
    """
    # Check if response is successful
    if not (200 <= response.status_code < 300):
        error_message = f"Error from Conjur API: {response.status_code}"
        
        try:
            error_data = response.json()
            if 'error' in error_data:
                error_message = f"{error_message} - {error_data['error']}"
        except:
            if response.text:
                error_message = f"{error_message} - {response.text}"
        
        if response.status_code == 401:
            raise ConjurAuthenticationError(error_message)
        elif response.status_code == 403:
            raise ConjurPermissionError(error_message)
        elif response.status_code == 404:
            raise ConjurNotFoundError(error_message)
        elif response.status_code >= 500:
            raise ConjurConnectionError(error_message)
        else:
            raise ConjurError(error_message)
    
    # Check if response is JSON
    content_type = response.headers.get('Content-Type', '')
    if 'application/json' in content_type:
        try:
            return response.json()
        except json.JSONDecodeError:
            LOGGER.warning("Response claimed to be JSON but couldn't be parsed")
            return response.text
    
    # Return text for non-JSON responses
    return response.text


def sanitize_log_data(data, sensitive_fields=None):
    """
    Sanitizes data for logging by masking sensitive fields.
    
    Args:
        data (dict): Data to sanitize.
        sensitive_fields (list, optional): List of sensitive field names to mask.
            Defaults to SENSITIVE_FIELDS.
    
    Returns:
        dict: Sanitized data safe for logging.
    """
    if not data or not isinstance(data, dict):
        return data
    
    # Make a copy to avoid modifying the original
    sanitized = data.copy()
    
    # Use default sensitive fields if none provided
    if sensitive_fields is None:
        sensitive_fields = SENSITIVE_FIELDS
    
    # Mask sensitive fields
    for field in sensitive_fields:
        if field in sanitized:
            sanitized[field] = '***'
    
    return sanitized


def calculate_backoff(retry_count, backoff_factor=DEFAULT_BACKOFF_FACTOR, jitter_factor=DEFAULT_JITTER_FACTOR):
    """
    Calculates backoff time for retry mechanism.
    
    Args:
        retry_count (int): Current retry attempt number.
        backoff_factor (float, optional): Base for exponential backoff.
            Defaults to DEFAULT_BACKOFF_FACTOR.
        jitter_factor (float, optional): Factor for adding randomness to backoff.
            Defaults to DEFAULT_JITTER_FACTOR.
    
    Returns:
        float: Backoff time in seconds.
    """
    # Calculate base backoff using exponential formula
    base_backoff = backoff_factor * (2 ** retry_count)
    
    # Add jitter to prevent thundering herd problem
    jitter = random.uniform(0, base_backoff * jitter_factor)
    
    return base_backoff + jitter


def execute_with_retry(func, retry_handler, *args, **kwargs):
    """
    Executes a function with retry mechanism.
    
    Args:
        func (callable): Function to execute.
        retry_handler (RetryHandler): Retry handler instance.
        *args: Positional arguments for the function.
        **kwargs: Keyword arguments for the function.
    
    Returns:
        Any: Result of the function execution.
    
    Raises:
        Exception: The last exception raised if all retries fail.
    """
    retry_count = 0
    last_exception = None
    
    while retry_count <= retry_handler.max_retries:
        try:
            return func(*args, **kwargs)
        except Exception as e:
            last_exception = e
            
            # Check if we should retry
            if retry_count == retry_handler.max_retries or not retry_handler.is_retryable(e):
                break
            
            # Calculate backoff time
            backoff_time = retry_handler.get_backoff_time(retry_count)
            
            LOGGER.warning(
                f"Retry attempt {retry_count + 1}/{retry_handler.max_retries} "
                f"after error: {str(e)}. Waiting {backoff_time:.2f} seconds."
            )
            
            # Sleep before retrying
            time.sleep(backoff_time)
            
            retry_count += 1
    
    # If we get here, all retries failed
    if isinstance(last_exception, ConjurError):
        raise last_exception
    else:
        # Wrap non-Conjur exceptions
        raise ConjurError(f"Operation failed after {retry_count} retries: {str(last_exception)}", last_exception)


class RetryHandler:
    """Class for handling retry logic with exponential backoff."""
    
    def __init__(self, 
                 max_retries=DEFAULT_MAX_RETRIES, 
                 backoff_factor=DEFAULT_BACKOFF_FACTOR,
                 jitter_factor=DEFAULT_JITTER_FACTOR,
                 retryable_exceptions=None):
        """
        Initializes a new RetryHandler instance.
        
        Args:
            max_retries (int, optional): Maximum number of retry attempts.
                Defaults to DEFAULT_MAX_RETRIES.
            backoff_factor (float, optional): Base for exponential backoff.
                Defaults to DEFAULT_BACKOFF_FACTOR.
            jitter_factor (float, optional): Factor for adding randomness to backoff.
                Defaults to DEFAULT_JITTER_FACTOR.
            retryable_exceptions (list, optional): List of exception classes that should
                trigger a retry. Defaults to [ConjurConnectionError, requests.exceptions.RequestException].
        """
        self.max_retries = max_retries
        self.backoff_factor = backoff_factor
        self.jitter_factor = jitter_factor
        
        # Default retryable exceptions if none provided
        if retryable_exceptions is None:
            self.retryable_exceptions = [
                ConjurConnectionError,
                requests.exceptions.RequestException,
                requests.exceptions.Timeout,
                requests.exceptions.ConnectionError
            ]
        else:
            self.retryable_exceptions = retryable_exceptions
    
    def is_retryable(self, exception):
        """
        Checks if an exception is retryable.
        
        Args:
            exception (Exception): The exception to check.
        
        Returns:
            bool: True if exception is retryable, False otherwise.
        """
        return any(isinstance(exception, exc_type) for exc_type in self.retryable_exceptions)
    
    def get_backoff_time(self, retry_count):
        """
        Calculates backoff time for a retry attempt.
        
        Args:
            retry_count (int): Current retry attempt number.
        
        Returns:
            float: Backoff time in seconds.
        """
        return calculate_backoff(retry_count, self.backoff_factor, self.jitter_factor)
    
    def execute(self, func, *args, **kwargs):
        """
        Executes a function with retry logic.
        
        Args:
            func (callable): Function to execute.
            *args: Positional arguments for the function.
            **kwargs: Keyword arguments for the function.
        
        Returns:
            Any: Result of the function execution.
        
        Raises:
            Exception: The last exception raised if all retries fail.
        """
        return execute_with_retry(func, self, *args, **kwargs)