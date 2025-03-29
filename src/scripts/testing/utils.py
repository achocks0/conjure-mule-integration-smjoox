"""
Utility module for the Payment API Security Enhancement testing framework.

This module provides common functions and classes for authentication testing, 
token validation, HTTP request handling, test execution, and result reporting.
"""

import requests
import json
import os
import datetime
from datetime import datetime
import time
import uuid
import csv
import base64

# Import local modules
from .config import LOGGER, TestConfig, PerformanceTestConfig

# Default constants
DEFAULT_TIMEOUT = 30
DEFAULT_HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json"
}
DEFAULT_RETRY_COUNT = 3
DEFAULT_RETRY_DELAY = 1  # seconds


def create_http_session(headers=None, timeout=None, verify_ssl=True):
    """
    Creates and configures an HTTP session for API testing.
    
    Args:
        headers (dict): Dictionary of default headers to use for all requests
        timeout (int): Default timeout for requests in seconds
        verify_ssl (bool): Whether to verify SSL certificates
        
    Returns:
        requests.Session: Configured HTTP session
    """
    session = requests.Session()
    
    # Set default headers if provided, otherwise use defaults
    session.headers.update(headers or DEFAULT_HEADERS)
    
    # Set timeout
    session.timeout = timeout or DEFAULT_TIMEOUT
    
    # Set SSL verification
    session.verify = verify_ssl
    
    # Configure retry adapter if needed
    from requests.adapters import HTTPAdapter
    from urllib3.util.retry import Retry
    retry_strategy = Retry(
        total=DEFAULT_RETRY_COUNT,
        backoff_factor=0.5,
        status_forcelist=[429, 500, 502, 503, 504],
    )
    adapter = HTTPAdapter(max_retries=retry_strategy)
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    
    return session


def authenticate_client(session, eapi_url, client_id, client_secret):
    """
    Authenticates a client using Client ID and Client Secret.
    
    Args:
        session (requests.Session): HTTP session to use for the request
        eapi_url (str): URL of the Payment-EAPI service
        client_id (str): Client ID for authentication
        client_secret (str): Client Secret for authentication
        
    Returns:
        dict: Authentication response containing token if successful, None if failed
    """
    try:
        # Log authentication attempt (with sanitized credentials)
        sanitized_data = {"client_id": client_id, "client_secret": client_secret}
        LOGGER.info(f"Authenticating client {client_id} with Payment-EAPI at {eapi_url}")
        LOGGER.debug(f"Authentication data: {sanitize_log_data(sanitized_data)}")
        
        # Set authentication headers
        headers = {
            "X-Client-ID": client_id,
            "X-Client-Secret": client_secret
        }
        
        # Construct the authentication endpoint URL
        auth_url = f"{eapi_url}/api/v1/authenticate"
        
        # Send authentication request
        response = session.post(auth_url, headers=headers)
        
        # Check if authentication was successful
        if response.status_code == 200:
            LOGGER.info(f"Authentication successful for client {client_id}")
            return response.json()
        else:
            LOGGER.error(f"Authentication failed for client {client_id}: {response.status_code} - {response.text}")
            return None
            
    except Exception as e:
        LOGGER.error(f"Error during authentication: {str(e)}")
        return None


def validate_token(session, sapi_url, token, required_permissions=None, audience=None, allowed_issuers=None):
    """
    Validates a JWT token against the token validation endpoint.
    
    Args:
        session (requests.Session): HTTP session to use for the request
        sapi_url (str): URL of the Payment-SAPI service
        token (str): JWT token to validate
        required_permissions (list): List of permissions the token should have
        audience (str): Expected audience for the token
        allowed_issuers (list): List of allowed token issuers
        
    Returns:
        dict: Validation response containing result and details
    """
    try:
        # Log token validation attempt (without showing the full token)
        LOGGER.info(f"Validating token with Payment-SAPI at {sapi_url}")
        token_preview = token[:10] + "..." + token[-10:] if len(token) > 20 else token
        LOGGER.debug(f"Token preview: {token_preview}")
        
        # Construct the validation endpoint URL
        validation_url = f"{sapi_url}/internal/v1/tokens/validate"
        
        # Prepare the validation request payload
        payload = {
            "token": token
        }
        
        # Add optional validation parameters if provided
        if required_permissions:
            payload["required_permissions"] = required_permissions
        if audience:
            payload["audience"] = audience
        if allowed_issuers:
            payload["allowed_issuers"] = allowed_issuers
            
        # Set authorization header with the token
        headers = {
            "Authorization": f"Bearer {token}"
        }
        
        # Send validation request
        response = session.post(validation_url, json=payload, headers=headers)
        
        # Check if validation was successful
        if response.status_code == 200:
            LOGGER.info("Token validation successful")
            return response.json()
        else:
            LOGGER.error(f"Token validation failed: {response.status_code} - {response.text}")
            return {
                "valid": False,
                "error": f"Validation request failed with status {response.status_code}",
                "details": response.text
            }
            
    except Exception as e:
        LOGGER.error(f"Error during token validation: {str(e)}")
        return {
            "valid": False,
            "error": f"Exception during validation: {str(e)}",
            "details": None
        }


def decode_token(token):
    """
    Decodes a JWT token into its component parts.
    
    Args:
        token (str): JWT token string
        
    Returns:
        dict: Dictionary containing decoded header, payload, and signature
    """
    try:
        # Split token into parts
        parts = token.split('.')
        if len(parts) != 3:
            raise ValueError("Invalid token format - token must have 3 parts")
            
        # Decode header and payload (base64url decode)
        # Add padding if needed
        def decode_part(part):
            # Add padding
            padded = part + '=' * (4 - len(part) % 4)
            # Replace URL-safe characters
            padded = padded.replace('-', '+').replace('_', '/')
            # Decode
            decoded = base64.b64decode(padded)
            # Parse JSON
            return json.loads(decoded)
            
        header = decode_part(parts[0])
        payload = decode_part(parts[1])
        
        # Return decoded parts
        return {
            "header": header,
            "payload": payload,
            "signature": parts[2]  # Keep signature as is
        }
        
    except Exception as e:
        LOGGER.error(f"Error decoding token: {str(e)}")
        raise ValueError(f"Failed to decode token: {str(e)}")


def tamper_with_token(token, tamper_type, tamper_data=None):
    """
    Creates a tampered version of a JWT token for security testing.
    
    Args:
        token (str): Original JWT token
        tamper_type (str): Type of tampering to perform ('header', 'payload', 'signature')
        tamper_data (dict): Data to use for tampering (for header and payload)
        
    Returns:
        str: Tampered token
    """
    try:
        # Decode token
        parts = token.split('.')
        if len(parts) != 3:
            raise ValueError("Invalid token format - token must have 3 parts")
            
        header_part, payload_part, signature_part = parts
        
        # Tamper with token based on tamper_type
        if tamper_type == 'signature':
            # Change one character in the signature
            if signature_part:
                # Replace first character with something else
                if signature_part[0] in 'abcdef':
                    signature_part = 'z' + signature_part[1:]
                else:
                    signature_part = 'a' + signature_part[1:]
        elif tamper_type == 'payload' and tamper_data:
            # Decode payload
            def decode_part(part):
                # Add padding
                padded = part + '=' * (4 - len(part) % 4)
                # Replace URL-safe characters
                padded = padded.replace('-', '+').replace('_', '/')
                # Decode
                decoded = base64.b64decode(padded)
                # Parse JSON
                return json.loads(decoded)
                
            payload = decode_part(payload_part)
            
            # Modify payload with tamper_data
            for key, value in tamper_data.items():
                payload[key] = value
                
            # Encode modified payload
            def encode_part(data):
                # Convert to JSON
                json_str = json.dumps(data)
                # Encode
                encoded = base64.b64encode(json_str.encode('utf-8'))
                # Convert to string and remove padding
                encoded_str = encoded.decode('utf-8').rstrip('=')
                # Replace standard base64 characters with URL-safe ones
                return encoded_str.replace('+', '-').replace('/', '_')
                
            payload_part = encode_part(payload)
        elif tamper_type == 'header' and tamper_data:
            # Decode header
            def decode_part(part):
                # Add padding
                padded = part + '=' * (4 - len(part) % 4)
                # Replace URL-safe characters
                padded = padded.replace('-', '+').replace('_', '/')
                # Decode
                decoded = base64.b64decode(padded)
                # Parse JSON
                return json.loads(decoded)
                
            header = decode_part(header_part)
            
            # Modify header with tamper_data
            for key, value in tamper_data.items():
                header[key] = value
                
            # Encode modified header
            def encode_part(data):
                # Convert to JSON
                json_str = json.dumps(data)
                # Encode
                encoded = base64.b64encode(json_str.encode('utf-8'))
                # Convert to string and remove padding
                encoded_str = encoded.decode('utf-8').rstrip('=')
                # Replace standard base64 characters with URL-safe ones
                return encoded_str.replace('+', '-').replace('/', '_')
                
            header_part = encode_part(header)
        
        # Reconstruct token
        tampered_token = f"{header_part}.{payload_part}.{signature_part}"
        return tampered_token
        
    except Exception as e:
        LOGGER.error(f"Error tampering with token: {str(e)}")
        raise ValueError(f"Failed to tamper with token: {str(e)}")


def generate_test_data(scenario, config):
    """
    Generates test data for a specific test scenario.
    
    Args:
        scenario (str): Name of the test scenario
        config (TestConfig): Test configuration
        
    Returns:
        dict: Test data appropriate for the specified scenario
    """
    # Initialize base test data
    test_data = {
        "client_id": config.test_client_id,
        "client_secret": config.test_client_secret
    }
    
    # Add scenario-specific data
    if scenario == "valid_credentials":
        # Base test data is already set up for valid credentials
        pass
    elif scenario == "invalid_client_id":
        test_data["client_id"] = "invalid-client-id"
    elif scenario == "invalid_client_secret":
        test_data["client_secret"] = "invalid-client-secret"
    elif scenario == "missing_credentials":
        test_data = {}  # Empty data to test missing credentials
    elif scenario == "token_validation":
        # Add token validation specific data
        test_data["required_permissions"] = ["process_payment"]
        test_data["audience"] = "payment-sapi"
        test_data["allowed_issuers"] = ["payment-eapi"]
    elif scenario == "token_expiration":
        # Add token expiration specific data
        test_data["token_lifetime"] = 1  # Very short lifetime (1 second)
    elif scenario == "token_tampering":
        # Add token tampering specific data
        test_data["tamper_type"] = "payload"
        test_data["tamper_data"] = {"exp": int(time.time()) + 3600}  # Extend expiration
    elif scenario == "credential_rotation":
        # Add credential rotation specific data
        test_data["rotation_type"] = "normal"
    elif scenario == "dual_validation_period":
        # Add dual validation period specific data
        test_data["old_client_id"] = config.test_client_id
        test_data["old_client_secret"] = config.test_client_secret
        test_data["new_client_id"] = f"{config.test_client_id}-new"
        test_data["new_client_secret"] = f"{config.test_client_secret}-new"
    
    return test_data


def save_test_results(results, file_path, format='json'):
    """
    Saves test results to a file in the specified format.
    
    Args:
        results (list): List of test results
        file_path (str): Path to save the results to
        format (str): File format ('json' or 'csv')
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Ensure the directory exists
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        
        # Convert TestResult objects to dictionaries if needed
        if results and hasattr(results[0], 'to_dict'):
            results_data = [result.to_dict() for result in results]
        else:
            results_data = results
            
        # Save in the appropriate format
        if format.lower() == 'json':
            with open(file_path, 'w') as file:
                json.dump(results_data, file, indent=2)
        elif format.lower() == 'csv':
            if not results_data:
                LOGGER.warning("No results to save")
                return False
                
            # Get field names from the first result
            fieldnames = results_data[0].keys()
            
            with open(file_path, 'w', newline='') as file:
                writer = csv.DictWriter(file, fieldnames=fieldnames)
                writer.writeheader()
                writer.writerows(results_data)
        else:
            LOGGER.error(f"Unsupported format: {format}")
            return False
            
        LOGGER.info(f"Saved test results to {file_path}")
        return True
        
    except Exception as e:
        LOGGER.error(f"Error saving test results: {str(e)}")
        return False


def generate_test_report(results, file_path, format='txt'):
    """
    Generates a formatted test report from test results.
    
    Args:
        results (list): List of test results
        file_path (str): Path to save the report to
        format (str): Report format ('txt', 'html', 'md')
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Ensure the directory exists
        os.makedirs(os.path.dirname(file_path), exist_ok=True)
        
        # Calculate summary statistics
        total_tests = len(results)
        passed_tests = sum(1 for r in results if hasattr(r, 'success') and r.success)
        failed_tests = total_tests - passed_tests
        success_rate = (passed_tests / total_tests) * 100 if total_tests > 0 else 0
        
        # Format the report based on the specified format
        if format.lower() == 'txt':
            with open(file_path, 'w') as file:
                file.write("# Test Execution Report\n\n")
                file.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                file.write(f"Total Tests: {total_tests}\n")
                file.write(f"Passed: {passed_tests}\n")
                file.write(f"Failed: {failed_tests}\n")
                file.write(f"Success Rate: {success_rate:.2f}%\n\n")
                
                file.write("## Test Results\n\n")
                for idx, result in enumerate(results, 1):
                    status = "PASS" if hasattr(result, 'success') and result.success else "FAIL"
                    scenario = result.scenario if hasattr(result, 'scenario') else "Unknown"
                    duration = f"{result.duration:.2f}s" if hasattr(result, 'duration') else "Unknown"
                    
                    file.write(f"{idx}. [{status}] {scenario} - {duration}\n")
                    
                    if hasattr(result, 'details') and result.details:
                        file.write(f"   Details: {json.dumps(result.details)}\n")
                    
                    file.write("\n")
        elif format.lower() == 'html':
            with open(file_path, 'w') as file:
                file.write("<html><head><title>Test Execution Report</title></head><body>\n")
                file.write("<h1>Test Execution Report</h1>\n")
                file.write(f"<p>Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>\n")
                file.write("<h2>Summary</h2>\n")
                file.write("<ul>\n")
                file.write(f"<li>Total Tests: {total_tests}</li>\n")
                file.write(f"<li>Passed: {passed_tests}</li>\n")
                file.write(f"<li>Failed: {failed_tests}</li>\n")
                file.write(f"<li>Success Rate: {success_rate:.2f}%</li>\n")
                file.write("</ul>\n")
                
                file.write("<h2>Test Results</h2>\n")
                file.write("<table border='1'>\n")
                file.write("<tr><th>#</th><th>Status</th><th>Scenario</th><th>Duration</th><th>Details</th></tr>\n")
                
                for idx, result in enumerate(results, 1):
                    status = "PASS" if hasattr(result, 'success') and result.success else "FAIL"
                    scenario = result.scenario if hasattr(result, 'scenario') else "Unknown"
                    duration = f"{result.duration:.2f}s" if hasattr(result, 'duration') else "Unknown"
                    details = json.dumps(result.details) if hasattr(result, 'details') and result.details else ""
                    
                    status_color = "green" if status == "PASS" else "red"
                    
                    file.write(f"<tr>\n")
                    file.write(f"<td>{idx}</td>\n")
                    file.write(f"<td style='color:{status_color}'>{status}</td>\n")
                    file.write(f"<td>{scenario}</td>\n")
                    file.write(f"<td>{duration}</td>\n")
                    file.write(f"<td>{details}</td>\n")
                    file.write(f"</tr>\n")
                
                file.write("</table>\n")
                file.write("</body></html>\n")
        elif format.lower() == 'md':
            with open(file_path, 'w') as file:
                file.write("# Test Execution Report\n\n")
                file.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                file.write("## Summary\n\n")
                file.write(f"- Total Tests: {total_tests}\n")
                file.write(f"- Passed: {passed_tests}\n")
                file.write(f"- Failed: {failed_tests}\n")
                file.write(f"- Success Rate: {success_rate:.2f}%\n\n")
                
                file.write("## Test Results\n\n")
                file.write("| # | Status | Scenario | Duration | Details |\n")
                file.write("|---|--------|----------|----------|--------|\n")
                
                for idx, result in enumerate(results, 1):
                    status = "PASS" if hasattr(result, 'success') and result.success else "FAIL"
                    scenario = result.scenario if hasattr(result, 'scenario') else "Unknown"
                    duration = f"{result.duration:.2f}s" if hasattr(result, 'duration') else "Unknown"
                    details = json.dumps(result.details) if hasattr(result, 'details') and result.details else ""
                    
                    file.write(f"| {idx} | {status} | {scenario} | {duration} | {details} |\n")
        else:
            LOGGER.error(f"Unsupported format: {format}")
            return False
            
        LOGGER.info(f"Generated test report at {file_path}")
        return True
        
    except Exception as e:
        LOGGER.error(f"Error generating test report: {str(e)}")
        return False


def sanitize_log_data(data, sensitive_fields=None):
    """
    Sanitizes sensitive data for logging purposes.
    
    Args:
        data (dict): Dictionary containing potentially sensitive data
        sensitive_fields (list): List of field names to sanitize
        
    Returns:
        dict: Sanitized data safe for logging
    """
    if not data:
        return data
        
    if sensitive_fields is None:
        sensitive_fields = ['client_secret', 'password', 'token', 'key', 'secret']
        
    # Create a copy to avoid modifying the original
    sanitized = data.copy()
    
    for field in sensitive_fields:
        if field in sanitized:
            value = str(sanitized[field])
            if len(value) > 8:
                # Show first and last 4 characters, mask the rest
                sanitized[field] = value[:4] + '*' * (len(value) - 8) + value[-4:]
            else:
                # Just mask the whole value if it's too short
                sanitized[field] = '*' * len(value)
                
    return sanitized


class TestResult:
    """
    Class representing the result of a single test.
    """
    
    def __init__(self, scenario, success, details=None, duration=0):
        """
        Initializes a new TestResult instance.
        
        Args:
            scenario (str): Name of the test scenario
            success (bool): Whether the test was successful
            details (dict): Additional test details
            duration (float): Test execution duration in seconds
        """
        self.test_id = str(uuid.uuid4())
        self.scenario = scenario
        self.success = success
        self.details = details or {}
        self.timestamp = datetime.now()
        self.duration = duration
        
    def to_dict(self):
        """
        Converts the test result to a dictionary.
        
        Returns:
            dict: Dictionary representation of the test result
        """
        return {
            'test_id': self.test_id,
            'scenario': self.scenario,
            'success': self.success,
            'details': self.details,
            'timestamp': self.timestamp.isoformat(),
            'duration': self.duration
        }
        
    @classmethod
    def from_dict(cls, data):
        """
        Creates a TestResult instance from a dictionary.
        
        Args:
            data (dict): Dictionary containing test result data
            
        Returns:
            TestResult: TestResult instance
        """
        result = cls(
            scenario=data.get('scenario', 'unknown'),
            success=data.get('success', False),
            details=data.get('details', {}),
            duration=data.get('duration', 0)
        )
        
        # Set additional properties if present
        if 'test_id' in data:
            result.test_id = data['test_id']
            
        if 'timestamp' in data:
            try:
                if isinstance(data['timestamp'], str):
                    result.timestamp = datetime.fromisoformat(data['timestamp'])
                else:
                    result.timestamp = data['timestamp']
            except ValueError:
                pass
                
        return result


class TestRunner:
    """
    Base class for test runners that execute test scenarios.
    """
    
    def __init__(self, config):
        """
        Initializes a new TestRunner instance.
        
        Args:
            config (TestConfig): Test configuration
        """
        self.config = config
        self.session = None
        self.results = []
        
    def setup(self):
        """
        Sets up the test environment.
        
        Returns:
            bool: True if setup successful, False otherwise
        """
        try:
            # Create HTTP session
            self.session = create_http_session()
            
            # Verify connectivity to required endpoints
            if not self._verify_connectivity():
                return False
                
            LOGGER.info("Test environment setup completed successfully")
            return True
            
        except Exception as e:
            LOGGER.error(f"Error setting up test environment: {str(e)}")
            return False
            
    def _verify_connectivity(self):
        """
        Verifies connectivity to required endpoints.
        
        Returns:
            bool: True if connectivity verification is successful, False otherwise
            
        Note:
            This method should be implemented by subclasses.
        """
        # This method should be implemented by subclasses
        LOGGER.warning("_verify_connectivity not implemented in base class")
        return True
        
    def teardown(self):
        """
        Tears down the test environment.
        
        Returns:
            bool: True if teardown successful, False otherwise
        """
        try:
            # Close the HTTP session if it exists
            if self.session:
                self.session.close()
                self.session = None
                
            LOGGER.info("Test environment teardown completed successfully")
            return True
            
        except Exception as e:
            LOGGER.error(f"Error tearing down test environment: {str(e)}")
            return False
            
    def run_test(self, scenario, test_data=None):
        """
        Runs a single test scenario.
        
        Args:
            scenario (str): Name of the test scenario
            test_data (dict): Test data for the scenario
            
        Returns:
            TestResult: Test result
        """
        LOGGER.info(f"Running test scenario: {scenario}")
        
        start_time = time.time()
        success = False
        details = {}
        
        try:
            # This method should be implemented by subclasses
            # The actual test execution logic goes here
            LOGGER.warning(f"Test execution for scenario '{scenario}' not implemented in base class")
            
            # For base class, just return a failed result
            success = False
            details = {"error": "Test execution not implemented in base class"}
            
        except Exception as e:
            LOGGER.error(f"Error executing test scenario '{scenario}': {str(e)}")
            success = False
            details = {"error": str(e)}
            
        finally:
            end_time = time.time()
            duration = end_time - start_time
            
            # Create test result
            result = TestResult(
                scenario=scenario,
                success=success,
                details=details,
                duration=duration
            )
            
            return result
            
    def run_tests(self, scenarios):
        """
        Runs multiple test scenarios.
        
        Args:
            scenarios (list): List of test scenario names
            
        Returns:
            list: List of test results
        """
        # Set up the test environment
        if not self.setup():
            LOGGER.error("Failed to set up test environment. Aborting tests.")
            return []
            
        try:
            # Run each test scenario
            for scenario in scenarios:
                # Generate test data for the scenario
                test_data = generate_test_data(scenario, self.config)
                
                # Run the test
                result = self.run_test(scenario, test_data)
                
                # Add the result to the results list
                self.results.append(result)
                
            return self.results
            
        finally:
            # Tear down the test environment
            self.teardown()
            
    def get_results(self):
        """
        Returns the current test results.
        
        Returns:
            list: List of test results
        """
        return self.results


class AuthenticationTestError(Exception):
    """
    Exception class for authentication test errors.
    """
    
    def __init__(self, message, error_type="authentication_error", details=None):
        """
        Initializes a new AuthenticationTestError instance.
        
        Args:
            message (str): Error message
            error_type (str): Type of error
            details (dict): Additional error details
        """
        super().__init__(message)
        self.message = message
        self.error_type = error_type
        self.details = details or {}


class TokenValidationTestError(Exception):
    """
    Exception class for token validation test errors.
    """
    
    def __init__(self, message, error_type="token_validation_error", details=None):
        """
        Initializes a new TokenValidationTestError instance.
        
        Args:
            message (str): Error message
            error_type (str): Type of error
            details (dict): Additional error details
        """
        super().__init__(message)
        self.message = message
        self.error_type = error_type
        self.details = details or {}


class CredentialRotationTestError(Exception):
    """
    Exception class for credential rotation test errors.
    """
    
    def __init__(self, message, error_type="credential_rotation_error", details=None):
        """
        Initializes a new CredentialRotationTestError instance.
        
        Args:
            message (str): Error message
            error_type (str): Type of error
            details (dict): Additional error details
        """
        super().__init__(message)
        self.message = message
        self.error_type = error_type
        self.details = details or {}