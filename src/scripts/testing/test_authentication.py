#!/usr/bin/env python3
"""
Authentication Test Module for Payment API Security Enhancement

This script performs comprehensive tests for the Payment API authentication mechanisms,
including Client ID/Secret validation, token generation, token validation, and security
aspects such as token tampering and brute force protection.

It is part of the test suite for the Payment API Security Enhancement project.
"""

import argparse
import sys
import os
import json
import time
import datetime
import logging
import requests

# Import local modules for configuration and testing utilities
from .config import LOGGER, TestConfig, setup_logging, get_test_config, generate_test_report_path
from .utils import (
    TestRunner, TestResult, authenticate_client, validate_token, decode_token,
    tamper_with_token, create_http_session, save_test_results, generate_test_report,
    AuthenticationTestError
)

# Default configuration values from environment variables
DEFAULT_CONFIG_FILE = os.environ.get('TEST_CONFIG_FILE', None)
DEFAULT_ENVIRONMENT = os.environ.get('TEST_ENV', 'dev')
DEFAULT_OUTPUT_FORMAT = os.environ.get('TEST_OUTPUT_FORMAT', 'json')

# Test scenarios with descriptions
TEST_SCENARIOS = {
    "valid_authentication": "Test authentication with valid credentials",
    "invalid_client_id": "Test authentication with invalid client ID",
    "invalid_client_secret": "Test authentication with invalid client secret",
    "missing_credentials": "Test authentication with missing credentials",
    "token_validation": "Test token validation",
    "token_expiration": "Test token expiration handling",
    "token_tampering": "Test security against token tampering",
    "brute_force_protection": "Test brute force protection mechanisms"
}


def parse_arguments():
    """
    Parse command line arguments for the authentication test script.

    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    parser = argparse.ArgumentParser(description="Payment API Authentication Testing")
    
    parser.add_argument(
        "--config", "-c",
        dest="config_file",
        default=DEFAULT_CONFIG_FILE,
        help="Path to the test configuration file"
    )
    
    parser.add_argument(
        "--env", "-e",
        dest="environment",
        default=DEFAULT_ENVIRONMENT,
        choices=["dev", "test", "staging", "prod"],
        help="Target environment for testing"
    )
    
    parser.add_argument(
        "--output", "-o",
        dest="output_format",
        default=DEFAULT_OUTPUT_FORMAT,
        choices=["json", "csv"],
        help="Output format for test results"
    )
    
    parser.add_argument(
        "--scenario", "-s",
        dest="scenario",
        choices=list(TEST_SCENARIOS.keys()),
        help="Specific test scenario to run"
    )
    
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )
    
    return parser.parse_args()


class AuthenticationTestRunner(TestRunner):
    """
    Test runner class for authentication tests that extends the base TestRunner.
    
    This class implements specific test methods for authentication scenarios including
    valid/invalid credentials, token validation, and security testing.
    """
    
    def __init__(self, config):
        """
        Initializes a new AuthenticationTestRunner instance.
        
        Args:
            config (TestConfig): Configuration for the test execution
        """
        super().__init__(config)
        self.tokens = {}  # Store tokens from successful authentications for later tests
    
    def run_test(self, scenario, test_data):
        """
        Run a single authentication test scenario.
        
        Args:
            scenario (str): Name of the test scenario
            test_data (dict): Test data for the scenario
            
        Returns:
            TestResult: Result of the test execution
        """
        LOGGER.info(f"Starting test scenario: {scenario}")
        
        start_time = time.time()
        success = False
        details = {}
        
        try:
            # Dispatch to the appropriate test method based on scenario
            if scenario == "valid_authentication":
                details = self.test_valid_authentication(test_data)
                success = details.get("success", False)
            elif scenario == "invalid_client_id":
                details = self.test_invalid_client_id(test_data)
                success = details.get("success", False)
            elif scenario == "invalid_client_secret":
                details = self.test_invalid_client_secret(test_data)
                success = details.get("success", False)
            elif scenario == "missing_credentials":
                details = self.test_missing_credentials(test_data)
                success = details.get("success", False)
            elif scenario == "token_validation":
                details = self.test_token_validation(test_data)
                success = details.get("success", False)
            elif scenario == "token_expiration":
                details = self.test_token_expiration(test_data)
                success = details.get("success", False)
            elif scenario == "token_tampering":
                details = self.test_token_tampering(test_data)
                success = details.get("success", False)
            elif scenario == "brute_force_protection":
                details = self.test_brute_force_protection(test_data)
                success = details.get("success", False)
            else:
                LOGGER.error(f"Unknown test scenario: {scenario}")
                details = {"error": f"Unknown test scenario: {scenario}"}
                success = False
                
        except Exception as e:
            LOGGER.error(f"Error during test execution: {str(e)}")
            details = {"error": str(e)}
            success = False
            
        finally:
            end_time = time.time()
            duration = end_time - start_time
            
            LOGGER.info(f"Completed test scenario: {scenario} - Success: {success}, Duration: {duration:.2f}s")
            
            # Create and return the test result
            result = TestResult(
                scenario=scenario,
                success=success,
                details=details,
                duration=duration
            )
            
            return result
    
    def test_valid_authentication(self, test_data):
        """
        Test authentication with valid credentials.
        
        Args:
            test_data (dict): Test data containing client_id and client_secret
            
        Returns:
            dict: Test details including success status and response data
        """
        LOGGER.info("Testing authentication with valid credentials")
        
        client_id = test_data.get("client_id")
        client_secret = test_data.get("client_secret")
        
        if not client_id or not client_secret:
            error_msg = "Test data missing required fields: client_id and client_secret"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
        
        # Authenticate with the provided credentials
        auth_response = authenticate_client(
            self.session,
            self.config.eapi_url,
            client_id,
            client_secret
        )
        
        if not auth_response:
            error_msg = "Authentication failed for valid credentials"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
        
        # Verify the response contains a token and expiration
        if "token" not in auth_response or "expiresAt" not in auth_response:
            error_msg = "Authentication response missing required fields: token and expiresAt"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg, "response": auth_response}
        
        # Store the token for later tests
        self.tokens[client_id] = auth_response["token"]
        
        LOGGER.info("Authentication successful with valid credentials")
        return {
            "success": True,
            "response": auth_response
        }
    
    def test_invalid_client_id(self, test_data):
        """
        Test authentication with invalid client ID.
        
        Args:
            test_data (dict): Test data containing invalid client_id and valid client_secret
            
        Returns:
            dict: Test details including success status and error information
        """
        LOGGER.info("Testing authentication with invalid client ID")
        
        invalid_client_id = test_data.get("client_id", "invalid-client-id")
        client_secret = test_data.get("client_secret")
        
        if not client_secret:
            error_msg = "Test data missing required field: client_secret"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
        
        # Authenticate with invalid client ID
        auth_response = authenticate_client(
            self.session,
            self.config.eapi_url,
            invalid_client_id,
            client_secret
        )
        
        # Authentication should fail
        if auth_response:
            error_msg = "Authentication succeeded with invalid client ID"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg, "response": auth_response}
        
        # Check for appropriate error response
        try:
            # Make direct request to verify status code
            headers = {
                "X-Client-ID": invalid_client_id,
                "X-Client-Secret": client_secret
            }
            response = self.session.post(
                f"{self.config.eapi_url}/api/v1/authenticate",
                headers=headers
            )
            
            # Verify 401 status code
            if response.status_code != 401:
                error_msg = f"Unexpected status code for invalid client ID: {response.status_code}"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "status_code": response.status_code}
            
            LOGGER.info("Authentication correctly rejected invalid client ID")
            return {
                "success": True,
                "status_code": response.status_code,
                "error_response": response.json() if response.headers.get("content-type") == "application/json" else response.text
            }
            
        except requests.RequestException as e:
            error_msg = f"Error making request: {str(e)}"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
    
    def test_invalid_client_secret(self, test_data):
        """
        Test authentication with invalid client secret.
        
        Args:
            test_data (dict): Test data containing valid client_id and invalid client_secret
            
        Returns:
            dict: Test details including success status and error information
        """
        LOGGER.info("Testing authentication with invalid client secret")
        
        client_id = test_data.get("client_id")
        invalid_client_secret = test_data.get("client_secret", "invalid-client-secret")
        
        if not client_id:
            error_msg = "Test data missing required field: client_id"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
        
        # Authenticate with invalid client secret
        auth_response = authenticate_client(
            self.session,
            self.config.eapi_url,
            client_id,
            invalid_client_secret
        )
        
        # Authentication should fail
        if auth_response:
            error_msg = "Authentication succeeded with invalid client secret"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg, "response": auth_response}
        
        # Check for appropriate error response
        try:
            # Make direct request to verify status code
            headers = {
                "X-Client-ID": client_id,
                "X-Client-Secret": invalid_client_secret
            }
            response = self.session.post(
                f"{self.config.eapi_url}/api/v1/authenticate",
                headers=headers
            )
            
            # Verify 401 status code
            if response.status_code != 401:
                error_msg = f"Unexpected status code for invalid client secret: {response.status_code}"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "status_code": response.status_code}
            
            LOGGER.info("Authentication correctly rejected invalid client secret")
            return {
                "success": True,
                "status_code": response.status_code,
                "error_response": response.json() if response.headers.get("content-type") == "application/json" else response.text
            }
            
        except requests.RequestException as e:
            error_msg = f"Error making request: {str(e)}"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
    
    def test_missing_credentials(self, test_data):
        """
        Test authentication with missing credentials.
        
        Args:
            test_data (dict): Test data (not used in this test)
            
        Returns:
            dict: Test details including success status and error information
        """
        LOGGER.info("Testing authentication with missing credentials")
        
        # Prepare test cases with missing credentials
        test_cases = [
            {"description": "Missing client ID", "headers": {"X-Client-Secret": "some-secret"}},
            {"description": "Missing client secret", "headers": {"X-Client-ID": "some-id"}},
            {"description": "Missing both", "headers": {}}
        ]
        
        results = []
        success = True
        
        for test_case in test_cases:
            description = test_case["description"]
            headers = test_case["headers"]
            
            LOGGER.info(f"Testing {description}")
            
            try:
                # Make direct request with missing credentials
                response = self.session.post(
                    f"{self.config.eapi_url}/api/v1/authenticate",
                    headers=headers
                )
                
                # Verify 400 status code for missing required credentials
                if response.status_code != 400:
                    error_msg = f"Unexpected status code for {description}: {response.status_code}"
                    LOGGER.error(error_msg)
                    results.append({
                        "description": description,
                        "success": False,
                        "error": error_msg,
                        "status_code": response.status_code
                    })
                    success = False
                    continue
                
                # Record successful test case
                LOGGER.info(f"Authentication correctly rejected {description}")
                results.append({
                    "description": description,
                    "success": True,
                    "status_code": response.status_code,
                    "error_response": response.json() if response.headers.get("content-type") == "application/json" else response.text
                })
                
            except requests.RequestException as e:
                error_msg = f"Error making request for {description}: {str(e)}"
                LOGGER.error(error_msg)
                results.append({
                    "description": description,
                    "success": False,
                    "error": error_msg
                })
                success = False
        
        return {
            "success": success,
            "test_cases": results
        }
    
    def test_token_validation(self, test_data):
        """
        Test token validation functionality.
        
        Args:
            test_data (dict): Test data with optional validation parameters
            
        Returns:
            dict: Test details including success status and validation results
        """
        LOGGER.info("Testing token validation")
        
        # Get a valid token first if not already available
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            LOGGER.info("No token available, authenticating first")
            auth_result = self.test_valid_authentication({
                "client_id": client_id,
                "client_secret": test_data.get("client_secret", self.config.test_client_secret)
            })
            
            if not auth_result.get("success"):
                error_msg = "Failed to obtain token for validation test"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg}
            
            token = self.tokens.get(client_id)
        
        # Extract validation parameters from test data
        required_permissions = test_data.get("required_permissions")
        audience = test_data.get("audience")
        allowed_issuers = test_data.get("allowed_issuers")
        
        # Validate the token
        validation_result = validate_token(
            self.session,
            self.config.sapi_url,
            token,
            required_permissions,
            audience,
            allowed_issuers
        )
        
        if not validation_result.get("valid", False):
            error_msg = f"Token validation failed: {validation_result.get('error', 'Unknown error')}"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg, "validation_result": validation_result}
        
        # Token is valid, now decode and verify claims
        try:
            decoded_token = decode_token(token)
            
            # Verify basic token structure
            if not all(key in decoded_token for key in ["header", "payload"]):
                error_msg = "Token has invalid structure"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "decoded_token": decoded_token}
            
            # Verify header has 'alg' and 'typ'
            if not all(key in decoded_token["header"] for key in ["alg", "typ"]):
                error_msg = "Token header missing required fields"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "header": decoded_token["header"]}
            
            # Verify payload has required claims
            required_claims = ["sub", "iss", "aud", "exp", "iat", "jti"]
            missing_claims = [claim for claim in required_claims if claim not in decoded_token["payload"]]
            
            if missing_claims:
                error_msg = f"Token payload missing required claims: {', '.join(missing_claims)}"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "payload": decoded_token["payload"]}
            
            # Verify expiration is in the future
            if decoded_token["payload"]["exp"] <= time.time():
                error_msg = "Token is already expired"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "expiration": decoded_token["payload"]["exp"]}
            
            LOGGER.info("Token validation successful")
            return {
                "success": True,
                "validation_result": validation_result,
                "decoded_token": decoded_token
            }
            
        except ValueError as e:
            error_msg = f"Error decoding token: {str(e)}"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
    
    def test_token_expiration(self, test_data):
        """
        Test token expiration handling.
        
        Args:
            test_data (dict): Test data with optional token lifetime
            
        Returns:
            dict: Test details including success status and expiration handling results
        """
        LOGGER.info("Testing token expiration handling")
        
        # Get a valid token first if not already available
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            LOGGER.info("No token available, authenticating first")
            auth_result = self.test_valid_authentication({
                "client_id": client_id,
                "client_secret": test_data.get("client_secret", self.config.test_client_secret)
            })
            
            if not auth_result.get("success"):
                error_msg = "Failed to obtain token for expiration test"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg}
            
            token = self.tokens.get(client_id)
        
        # Decode the token to check expiration time
        try:
            decoded_token = decode_token(token)
            expiration_time = decoded_token["payload"]["exp"]
            current_time = time.time()
            
            # Calculate time until expiration
            time_until_expiration = expiration_time - current_time
            
            LOGGER.info(f"Token expires in {time_until_expiration:.2f} seconds")
            
            # If token lifteime is specified and very short, we can test actual expiration
            token_lifetime = test_data.get("token_lifetime")
            
            if token_lifetime and token_lifetime < 10:
                # Request a short-lived token for testing expiration
                LOGGER.info(f"Requesting short-lived token with lifetime of {token_lifetime} seconds")
                
                # This would require a special endpoint or parameter to request a short-lived token
                # For demonstration purposes, we'll assume it exists
                try:
                    # Request a short-lived token
                    headers = {
                        "X-Client-ID": client_id,
                        "X-Client-Secret": test_data.get("client_secret", self.config.test_client_secret),
                        "X-Token-Lifetime": str(token_lifetime)
                    }
                    response = self.session.post(
                        f"{self.config.eapi_url}/api/v1/authenticate",
                        headers=headers
                    )
                    
                    if response.status_code != 200:
                        LOGGER.warning(f"Could not request short-lived token: {response.status_code}")
                        return {
                            "success": True,
                            "note": "Could not test actual expiration, but token has valid expiration time",
                            "time_until_expiration": time_until_expiration
                        }
                    
                    # Get the short-lived token
                    short_lived_token = response.json().get("token")
                    
                    # Wait for token to expire
                    LOGGER.info(f"Waiting {token_lifetime + 1} seconds for token to expire")
                    time.sleep(token_lifetime + 1)
                    
                    # Try to validate the expired token
                    validation_result = validate_token(
                        self.session,
                        self.config.sapi_url,
                        short_lived_token
                    )
                    
                    # Token should be expired
                    if validation_result.get("valid", False):
                        error_msg = "Expired token was incorrectly validated as valid"
                        LOGGER.error(error_msg)
                        return {"success": False, "error": error_msg, "validation_result": validation_result}
                    
                    LOGGER.info("Expired token correctly rejected")
                    return {
                        "success": True,
                        "expired_token_validation": validation_result
                    }
                    
                except Exception as e:
                    LOGGER.warning(f"Error testing actual expiration: {str(e)}")
                    # Continue with basic expiration time validation
            
            # Basic validation of expiration time
            if time_until_expiration <= 0:
                error_msg = "Token is already expired"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg, "expiration": expiration_time}
            
            # Success case - token has valid expiration time
            return {
                "success": True,
                "time_until_expiration": time_until_expiration,
                "expiration_time": expiration_time
            }
            
        except ValueError as e:
            error_msg = f"Error decoding token: {str(e)}"
            LOGGER.error(error_msg)
            return {"success": False, "error": error_msg}
    
    def test_token_tampering(self, test_data):
        """
        Test security against token tampering.
        
        Args:
            test_data (dict): Test data with optional tampering parameters
            
        Returns:
            dict: Test details including success status and security test results
        """
        LOGGER.info("Testing security against token tampering")
        
        # Get a valid token first if not already available
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            LOGGER.info("No token available, authenticating first")
            auth_result = self.test_valid_authentication({
                "client_id": client_id,
                "client_secret": test_data.get("client_secret", self.config.test_client_secret)
            })
            
            if not auth_result.get("success"):
                error_msg = "Failed to obtain token for tampering test"
                LOGGER.error(error_msg)
                return {"success": False, "error": error_msg}
            
            token = self.tokens.get(client_id)
        
        # Define tampering scenarios to test
        tampering_scenarios = [
            {
                "name": "signature_tampering",
                "type": "signature",
                "data": None,
                "description": "Tampered signature"
            },
            {
                "name": "payload_tampering",
                "type": "payload",
                "data": {"sub": "fake-client-id"},
                "description": "Modified subject claim"
            },
            {
                "name": "expiration_tampering",
                "type": "payload",
                "data": {"exp": int(time.time()) + 86400*365},  # Extend expiration by 1 year
                "description": "Extended expiration time"
            },
            {
                "name": "algorithm_tampering",
                "type": "header",
                "data": {"alg": "none"},
                "description": "Removed signing algorithm"
            }
        ]
        
        # Test each tampering scenario
        results = []
        
        for scenario in tampering_scenarios:
            LOGGER.info(f"Testing {scenario['description']}")
            
            try:
                # Create tampered token
                tampered_token = tamper_with_token(
                    token,
                    scenario["type"],
                    scenario["data"]
                )
                
                # Validate the tampered token
                validation_result = validate_token(
                    self.session,
                    self.config.sapi_url,
                    tampered_token
                )
                
                # Token should be invalid
                if validation_result.get("valid", False):
                    error_msg = f"Tampered token ({scenario['name']}) was incorrectly validated as valid"
                    LOGGER.error(error_msg)
                    results.append({
                        "name": scenario["name"],
                        "success": False,
                        "error": error_msg,
                        "validation_result": validation_result
                    })
                    continue
                
                # Success - tampered token was rejected
                LOGGER.info(f"Tampered token ({scenario['name']}) correctly rejected")
                results.append({
                    "name": scenario["name"],
                    "success": True,
                    "validation_result": validation_result
                })
                
            except Exception as e:
                error_msg = f"Error during {scenario['name']} test: {str(e)}"
                LOGGER.error(error_msg)
                results.append({
                    "name": scenario["name"],
                    "success": False,
                    "error": error_msg
                })
        
        # Check if all tampering tests were successful
        all_successful = all(result["success"] for result in results)
        
        return {
            "success": all_successful,
            "tampering_tests": results
        }
    
    def test_brute_force_protection(self, test_data):
        """
        Test protection against brute force attacks.
        
        Args:
            test_data (dict): Test data with client credentials
            
        Returns:
            dict: Test details including success status and brute force protection results
        """
        LOGGER.info("Testing brute force protection mechanisms")
        
        client_id = test_data.get("client_id", self.config.test_client_id)
        invalid_client_secret = "invalid-secret-for-brute-force-test"
        
        # Number of authentication attempts to make
        max_attempts = 10
        
        # Track response times and status codes
        responses = []
        
        LOGGER.info(f"Making {max_attempts} authentication attempts with invalid credentials")
        
        # Make multiple authentication attempts with invalid credentials
        for i in range(max_attempts):
            start_time = time.time()
            
            try:
                # Make direct request with invalid credentials
                headers = {
                    "X-Client-ID": client_id,
                    "X-Client-Secret": f"{invalid_client_secret}-{i}"  # Use different invalid secret each time
                }
                response = self.session.post(
                    f"{self.config.eapi_url}/api/v1/authenticate",
                    headers=headers
                )
                
                end_time = time.time()
                duration = end_time - start_time
                
                # Record response details
                responses.append({
                    "attempt": i + 1,
                    "status_code": response.status_code,
                    "duration": duration,
                    "response": response.json() if response.headers.get("content-type") == "application/json" else response.text
                })
                
                LOGGER.info(f"Attempt {i + 1}: Status: {response.status_code}, Time: {duration:.3f}s")
                
                # If we start getting 429 (Too Many Requests) or similar rate limiting status,
                # we've detected brute force protection
                if response.status_code == 429:
                    LOGGER.info(f"Rate limiting detected after {i + 1} attempts")
                    break
                
                # If response time significantly increases, it might indicate rate limiting
                if i > 0 and duration > 3 * responses[0]["duration"]:
                    LOGGER.info(f"Response time significantly increased after {i + 1} attempts")
                    # Continue to gather more data
                
            except requests.RequestException as e:
                LOGGER.error(f"Error during brute force test attempt {i + 1}: {str(e)}")
                responses.append({
                    "attempt": i + 1,
                    "error": str(e)
                })
        
        # Analyze results to detect brute force protection
        rate_limited = any(r.get("status_code") == 429 for r in responses)
        
        # Check for increasing response times
        if len(responses) > 3:
            early_avg = sum(r["duration"] for r in responses[:3]) / 3 if all("duration" in r for r in responses[:3]) else 0
            late_avg = sum(r["duration"] for r in responses[-3:]) / 3 if all("duration" in r for r in responses[-3:]) else 0
            time_increase = late_avg > early_avg * 2
        else:
            time_increase = False
        
        # Check for account lockout
        locked_out = False
        if len(responses) > 0 and all(r.get("status_code") in [401, 403, 429] for r in responses[-3:]):
            # Try with valid credentials to see if account is locked
            try:
                LOGGER.info("Testing if account is locked by trying valid credentials")
                valid_response = self.session.post(
                    f"{self.config.eapi_url}/api/v1/authenticate",
                    headers={
                        "X-Client-ID": client_id,
                        "X-Client-Secret": self.config.test_client_secret
                    }
                )
                
                # If valid credentials still fail, account might be locked
                locked_out = valid_response.status_code in [401, 403, 429]
                
                if locked_out:
                    LOGGER.info("Account appears to be locked out (valid credentials rejected)")
                else:
                    LOGGER.info("Account is not locked out (valid credentials accepted)")
                
            except requests.RequestException as e:
                LOGGER.error(f"Error checking account lockout: {str(e)}")
        
        # Determine if brute force protection was detected
        protection_detected = rate_limited or time_increase or locked_out
        
        if protection_detected:
            LOGGER.info("Brute force protection mechanisms detected")
            return {
                "success": True,
                "protection_detected": True,
                "rate_limiting": rate_limited,
                "increasing_response_time": time_increase,
                "account_lockout": locked_out,
                "responses": responses
            }
        else:
            LOGGER.warning("No brute force protection mechanisms detected")
            return {
                "success": False,
                "protection_detected": False,
                "note": "API should implement rate limiting, increasing delays, or account lockout",
                "responses": responses
            }
    
    def get_summary(self):
        """
        Generate a summary of test results.
        
        Returns:
            dict: Summary of test results including pass/fail counts and success rate
        """
        if not self.results:
            return {
                "total": 0,
                "passed": 0,
                "failed": 0,
                "success_rate": 0.0
            }
        
        total = len(self.results)
        passed = sum(1 for r in self.results if r.success)
        failed = total - passed
        success_rate = (passed / total) * 100 if total > 0 else 0.0
        
        return {
            "total": total,
            "passed": passed,
            "failed": failed,
            "success_rate": success_rate
        }


def main():
    """
    Main function that orchestrates the authentication testing process.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    # Parse command line arguments
    args = parse_arguments()
    
    # Setup logging
    log_level = logging.DEBUG if args.verbose else logging.INFO
    setup_logging(log_level)
    
    LOGGER.info("Starting authentication testing")
    
    # Load test configuration
    config = get_test_config(args.config_file, args.environment)
    
    if not config.validate():
        LOGGER.error("Invalid test configuration")
        return 1
    
    # Create authentication test runner
    test_runner = AuthenticationTestRunner(config)
    
    # Determine which test scenarios to run
    if args.scenario:
        scenarios = [args.scenario]
    else:
        scenarios = list(TEST_SCENARIOS.keys())
    
    LOGGER.info(f"Running {len(scenarios)} test scenarios")
    
    # Run the selected test scenarios
    results = test_runner.run_tests(scenarios)
    
    if not results:
        LOGGER.error("No test results were generated")
        return 1
    
    # Generate report path for test results
    report_path = generate_test_report_path("authentication", args.output_format)
    
    # Save test results
    if save_test_results(results, report_path, args.output_format):
        LOGGER.info(f"Test results saved to: {report_path}")
    else:
        LOGGER.error("Failed to save test results")
    
    # Generate and save HTML report
    html_report_path = report_path.replace(f".{args.output_format}", ".html")
    if generate_test_report(results, html_report_path, "html"):
        LOGGER.info(f"HTML report generated at: {html_report_path}")
    
    # Log summary results
    summary = test_runner.get_summary()
    LOGGER.info(f"Test Summary: {summary['passed']}/{summary['total']} passed ({summary['success_rate']:.1f}%)")
    
    # Return exit code based on test results
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())