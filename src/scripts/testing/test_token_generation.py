#!/usr/bin/env python3
"""
Token Generation Testing Script

This script tests the token generation functionality of the Payment API Security
Enhancement project. It validates token creation, properties, validation, and security
aspects as specified in the technical requirements.
"""

import argparse
import sys
import os
import json
import time
import datetime
import requests
from datetime import datetime

# Import modules from the testing framework
from config import LOGGER, TestConfig, setup_logging, get_test_config, generate_test_report_path
from utils import (
    TestRunner, TestResult, authenticate_client, validate_token, decode_token,
    tamper_with_token, create_http_session, save_test_results, generate_test_report,
    TokenValidationTestError
)
from conjur import retrieve_credential_with_retry

# Default values for configuration and test scenarios
DEFAULT_CONFIG_FILE = os.environ.get('TEST_CONFIG_FILE', None)
DEFAULT_ENVIRONMENT = os.environ.get('TEST_ENV', 'dev')
DEFAULT_OUTPUT_FORMAT = os.environ.get('TEST_OUTPUT_FORMAT', 'json')

# Define test scenarios
TOKEN_TEST_SCENARIOS = {
    "token_generation": "Test token generation with valid credentials",
    "token_properties": "Test token properties and claims",
    "token_validation": "Test token validation",
    "token_expiration": "Test token expiration handling",
    "token_renewal": "Test token renewal process",
    "token_revocation": "Test token revocation",
    "token_security": "Test token security against tampering"
}


def parse_arguments():
    """
    Parse command line arguments for the test script.
    
    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    parser = argparse.ArgumentParser(
        description="Test token generation for Payment API Security Enhancement"
    )
    
    # Configuration options
    parser.add_argument(
        "--config", 
        help=f"Configuration file path (default: {DEFAULT_CONFIG_FILE})",
        default=DEFAULT_CONFIG_FILE
    )
    
    parser.add_argument(
        "--env", 
        help=f"Test environment (default: {DEFAULT_ENVIRONMENT})",
        choices=["dev", "test", "staging", "prod"],
        default=DEFAULT_ENVIRONMENT
    )
    
    parser.add_argument(
        "--output-format", 
        help=f"Output format for test results (default: {DEFAULT_OUTPUT_FORMAT})",
        choices=["json", "csv"],
        default=DEFAULT_OUTPUT_FORMAT
    )
    
    # Test selection
    parser.add_argument(
        "--scenario", 
        help="Specific test scenario to run (default: run all scenarios)",
        choices=list(TOKEN_TEST_SCENARIOS.keys())
    )
    
    # Verbosity
    parser.add_argument(
        "--verbose", "-v", 
        help="Enable verbose output",
        action="store_true"
    )
    
    return parser.parse_args()


class TokenGenerationTestRunner(TestRunner):
    """
    Test runner for token generation tests.
    
    This class extends the base TestRunner to provide specific token testing
    functionality including generation, validation, and security testing.
    """
    
    def __init__(self, config):
        """
        Initialize the TokenGenerationTestRunner with configuration.
        
        Args:
            config (TestConfig): Test configuration
        """
        super().__init__(config)
        # Dictionary to store tokens generated during tests
        self.tokens = {}
    
    def run_test(self, scenario, test_data=None):
        """
        Run a specific token generation test scenario.
        
        Args:
            scenario (str): Name of the test scenario
            test_data (dict): Test data for the scenario
            
        Returns:
            TestResult: Result of the test execution
        """
        LOGGER.info(f"Running token test scenario: {scenario}")
        
        start_time = time.time()
        success = False
        details = {}
        
        try:
            # Dispatch to appropriate test method based on scenario
            if scenario == "token_generation":
                details = self.test_token_generation(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_properties":
                details = self.test_token_properties(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_validation":
                details = self.test_token_validation(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_expiration":
                details = self.test_token_expiration(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_renewal":
                details = self.test_token_renewal(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_revocation":
                details = self.test_token_revocation(test_data or {})
                success = details.get("success", False)
            elif scenario == "token_security":
                details = self.test_token_security(test_data or {})
                success = details.get("success", False)
            else:
                LOGGER.warning(f"Unknown test scenario: {scenario}")
                details = {"error": f"Unknown test scenario: {scenario}"}
                success = False
                
        except Exception as e:
            LOGGER.error(f"Error executing test scenario '{scenario}': {str(e)}")
            details = {"error": str(e)}
            success = False
            
        finally:
            end_time = time.time()
            duration = end_time - start_time
            
            # Create and return test result
            result = TestResult(
                scenario=scenario,
                success=success,
                details=details,
                duration=duration
            )
            
            return result
    
    def test_token_generation(self, test_data):
        """
        Test token generation with valid credentials.
        
        Args:
            test_data (dict): Test data including client_id and client_secret
            
        Returns:
            dict: Test details including success status and token data
        """
        LOGGER.info("Testing token generation")
        
        # Get credentials from test data or config
        client_id = test_data.get("client_id", self.config.test_client_id)
        client_secret = test_data.get("client_secret", self.config.test_client_secret)
        
        if not client_id or not client_secret:
            return {
                "success": False,
                "error": "Missing client_id or client_secret for token generation test"
            }
        
        # Authenticate with the API
        auth_response = authenticate_client(
            self.session, 
            self.config.eapi_url, 
            client_id, 
            client_secret
        )
        
        if not auth_response:
            return {
                "success": False,
                "error": "Authentication failed"
            }
        
        # Check if authentication response contains token and expiration
        if "token" not in auth_response or "expiresAt" not in auth_response:
            return {
                "success": False,
                "error": "Authentication response missing token or expiration",
                "response": auth_response
            }
        
        # Store token for later tests
        self.tokens[client_id] = auth_response["token"]
        
        return {
            "success": True,
            "token": auth_response["token"],
            "expires_at": auth_response["expiresAt"],
            "message": "Successfully generated token"
        }
    
    def test_token_properties(self, test_data):
        """
        Test token properties and claims.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and token properties
        """
        LOGGER.info("Testing token properties")
        
        # Get or generate token
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            # Generate token if not available
            token_result = self.test_token_generation(test_data)
            if not token_result.get("success"):
                return {
                    "success": False,
                    "error": "Failed to generate token for properties test",
                    "token_generation_error": token_result.get("error")
                }
            token = token_result["token"]
        
        try:
            # Decode token to check properties
            decoded_token = decode_token(token)
            
            # Verify token has the expected structure
            if "header" not in decoded_token or "payload" not in decoded_token:
                return {
                    "success": False,
                    "error": "Token does not have expected structure",
                    "decoded_token": decoded_token
                }
            
            # Verify header properties
            header = decoded_token["header"]
            if header.get("alg") != "HS256":
                return {
                    "success": False,
                    "error": f"Token using unexpected algorithm: {header.get('alg')}",
                    "header": header
                }
            
            # Verify payload claims
            payload = decoded_token["payload"]
            required_claims = ["sub", "iss", "aud", "exp", "iat", "jti", "permissions"]
            
            missing_claims = [claim for claim in required_claims if claim not in payload]
            if missing_claims:
                return {
                    "success": False,
                    "error": f"Token missing required claims: {', '.join(missing_claims)}",
                    "payload": payload
                }
            
            # Verify subject (client_id)
            if payload["sub"] != client_id:
                return {
                    "success": False,
                    "error": f"Token subject does not match client_id. Expected: {client_id}, Got: {payload['sub']}",
                    "payload": payload
                }
            
            # Verify issuer
            if payload["iss"] != "payment-eapi":
                return {
                    "success": False,
                    "error": f"Token has unexpected issuer: {payload['iss']}",
                    "payload": payload
                }
            
            # Verify audience
            if payload["aud"] != "payment-sapi":
                return {
                    "success": False,
                    "error": f"Token has unexpected audience: {payload['aud']}",
                    "payload": payload
                }
            
            # Verify expiration is in the future
            exp_time = payload["exp"]
            current_time = int(time.time())
            if exp_time <= current_time:
                return {
                    "success": False,
                    "error": "Token is already expired",
                    "expiration": exp_time,
                    "current_time": current_time
                }
            
            # Verify permissions is an array
            if not isinstance(payload["permissions"], list):
                return {
                    "success": False,
                    "error": "Token permissions is not an array",
                    "permissions": payload["permissions"]
                }
            
            # All checks passed
            return {
                "success": True,
                "header": header,
                "payload": payload,
                "message": "Token has all required properties and valid values"
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error validating token properties: {str(e)}"
            }
    
    def test_token_validation(self, test_data):
        """
        Test token validation functionality.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and validation results
        """
        LOGGER.info("Testing token validation")
        
        # Get or generate token
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            # Generate token if not available
            token_result = self.test_token_generation(test_data)
            if not token_result.get("success"):
                return {
                    "success": False,
                    "error": "Failed to generate token for validation test",
                    "token_generation_error": token_result.get("error")
                }
            token = token_result["token"]
        
        try:
            # Basic token validation
            validation_result = validate_token(
                self.session,
                self.config.sapi_url,
                token
            )
            
            if not validation_result.get("valid", False):
                return {
                    "success": False,
                    "error": "Token validation failed",
                    "validation_result": validation_result
                }
            
            # Test validation with specific permissions
            permission_result = validate_token(
                self.session,
                self.config.sapi_url,
                token,
                required_permissions=["process_payment"]
            )
            
            if not permission_result.get("valid", False):
                return {
                    "success": False,
                    "error": "Token validation with required permissions failed",
                    "validation_result": permission_result
                }
            
            # Test validation with correct audience
            audience_result = validate_token(
                self.session,
                self.config.sapi_url,
                token,
                audience="payment-sapi"
            )
            
            if not audience_result.get("valid", False):
                return {
                    "success": False,
                    "error": "Token validation with audience check failed",
                    "validation_result": audience_result
                }
            
            # Test validation with incorrect audience (should fail)
            wrong_audience_result = validate_token(
                self.session,
                self.config.sapi_url,
                token,
                audience="wrong-audience"
            )
            
            if wrong_audience_result.get("valid", True):
                return {
                    "success": False,
                    "error": "Token validation with wrong audience succeeded when it should have failed",
                    "validation_result": wrong_audience_result
                }
            
            # All validation tests passed
            return {
                "success": True,
                "validation_results": {
                    "basic": validation_result,
                    "with_permissions": permission_result,
                    "with_audience": audience_result,
                    "with_wrong_audience": wrong_audience_result
                },
                "message": "Token validation tests passed successfully"
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error during token validation tests: {str(e)}"
            }
    
    def test_token_expiration(self, test_data):
        """
        Test token expiration handling.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and expiration handling results
        """
        LOGGER.info("Testing token expiration")
        
        # Get or generate token
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            # Generate token if not available
            token_result = self.test_token_generation(test_data)
            if not token_result.get("success"):
                return {
                    "success": False,
                    "error": "Failed to generate token for expiration test",
                    "token_generation_error": token_result.get("error")
                }
            token = token_result["token"]
        
        try:
            # Decode token to check expiration
            decoded_token = decode_token(token)
            payload = decoded_token["payload"]
            
            # Verify expiration claim exists
            if "exp" not in payload:
                return {
                    "success": False,
                    "error": "Token does not have expiration claim",
                    "payload": payload
                }
            
            # Check if expiration is in the future
            exp_time = payload["exp"]
            current_time = int(time.time())
            seconds_until_expiration = exp_time - current_time
            
            LOGGER.info(f"Token expires in {seconds_until_expiration} seconds")
            
            if seconds_until_expiration <= 0:
                return {
                    "success": False,
                    "error": "Token is already expired",
                    "expiration": exp_time,
                    "current_time": current_time
                }
            
            # If test data includes a short token lifetime, we can try to test
            # actual expiration by waiting for it to expire
            token_lifetime = test_data.get("token_lifetime")
            if token_lifetime and token_lifetime < 5 and seconds_until_expiration < 30:
                LOGGER.info(f"Waiting for token to expire ({seconds_until_expiration} seconds)...")
                
                # Wait until just after expiration
                time.sleep(seconds_until_expiration + 1)
                
                # Try to validate the expired token
                validation_result = validate_token(
                    self.session,
                    self.config.sapi_url,
                    token
                )
                
                # Token should be invalid now
                if validation_result.get("valid", True):
                    return {
                        "success": False,
                        "error": "Token still valid after expiration time",
                        "validation_result": validation_result
                    }
                
                LOGGER.info("Expired token was correctly rejected")
                
                # Test passes if the token was rejected
                return {
                    "success": True,
                    "message": "Token expiration correctly enforced",
                    "expired_validation_result": validation_result
                }
            
            # If we can't actually test expiration (token lifetime too long),
            # just verify token validation works and report that actual expiration
            # couldn't be tested
            validation_result = validate_token(
                self.session,
                self.config.sapi_url,
                token
            )
            
            if not validation_result.get("valid", False):
                return {
                    "success": False,
                    "error": "Token validation failed during expiration test",
                    "validation_result": validation_result
                }
            
            return {
                "success": True,
                "message": "Token has valid expiration claim but actual expiration couldn't be tested",
                "expiration": exp_time,
                "current_time": current_time,
                "seconds_until_expiration": seconds_until_expiration
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error during token expiration test: {str(e)}"
            }
    
    def test_token_renewal(self, test_data):
        """
        Test token renewal process.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and renewal results
        """
        LOGGER.info("Testing token renewal")
        
        # Get or generate token
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            # Generate token if not available
            token_result = self.test_token_generation(test_data)
            if not token_result.get("success"):
                return {
                    "success": False,
                    "error": "Failed to generate token for renewal test",
                    "token_generation_error": token_result.get("error")
                }
            token = token_result["token"]
        
        try:
            # Decode token to get original expiration
            original_decoded = decode_token(token)
            original_exp = original_decoded["payload"]["exp"]
            
            # Send token renewal request
            renewal_url = f"{self.config.eapi_url}/api/v1/tokens/renew"
            headers = {
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json"
            }
            
            renewal_response = self.session.post(renewal_url, headers=headers)
            
            if renewal_response.status_code != 200:
                return {
                    "success": False,
                    "error": "Token renewal request failed",
                    "status_code": renewal_response.status_code,
                    "response_text": renewal_response.text
                }
            
            # Parse renewal response
            try:
                renewal_result = renewal_response.json()
            except Exception as e:
                return {
                    "success": False,
                    "error": f"Failed to parse renewal response: {str(e)}",
                    "response_text": renewal_response.text
                }
            
            # Check if response contains a new token
            if "token" not in renewal_result:
                return {
                    "success": False,
                    "error": "Renewal response does not contain a new token",
                    "renewal_result": renewal_result
                }
            
            # Get the new token
            new_token = renewal_result["token"]
            
            # Decode new token to check expiration
            new_decoded = decode_token(new_token)
            new_exp = new_decoded["payload"]["exp"]
            
            # Verify new token has later expiration
            if new_exp <= original_exp:
                return {
                    "success": False,
                    "error": "New token does not have extended expiration",
                    "original_expiration": original_exp,
                    "new_expiration": new_exp
                }
            
            # Validate the new token
            validation_result = validate_token(
                self.session,
                self.config.sapi_url,
                new_token
            )
            
            if not validation_result.get("valid", False):
                return {
                    "success": False,
                    "error": "New token validation failed",
                    "validation_result": validation_result
                }
            
            # Store the new token for later tests
            self.tokens[client_id] = new_token
            
            return {
                "success": True,
                "original_token": token,
                "new_token": new_token,
                "original_expiration": original_exp,
                "new_expiration": new_exp,
                "extended_by_seconds": new_exp - original_exp,
                "message": "Token renewal successful"
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error during token renewal test: {str(e)}"
            }
    
    def test_token_revocation(self, test_data):
        """
        Test token revocation functionality.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and revocation results
        """
        LOGGER.info("Testing token revocation")
        
        # Generate a new token specifically for revocation testing
        # (so we don't invalidate tokens used by other tests)
        client_id = test_data.get("client_id", self.config.test_client_id)
        client_secret = test_data.get("client_secret", self.config.test_client_secret)
        
        if not client_id or not client_secret:
            return {
                "success": False,
                "error": "Missing client_id or client_secret for token revocation test"
            }
        
        # Generate a token for revocation
        auth_response = authenticate_client(
            self.session, 
            self.config.eapi_url, 
            client_id, 
            client_secret
        )
        
        if not auth_response or "token" not in auth_response:
            return {
                "success": False,
                "error": "Failed to generate token for revocation test"
            }
        
        token_to_revoke = auth_response["token"]
        
        # Validate the token is initially valid
        initial_validation = validate_token(
            self.session,
            self.config.sapi_url,
            token_to_revoke
        )
        
        if not initial_validation.get("valid", False):
            return {
                "success": False,
                "error": "Token is invalid before revocation test",
                "validation_result": initial_validation
            }
        
        try:
            # Revoke the token
            revocation_url = f"{self.config.eapi_url}/api/v1/tokens/revoke"
            headers = {
                "Authorization": f"Bearer {token_to_revoke}",
                "Content-Type": "application/json"
            }
            
            revocation_response = self.session.post(revocation_url, headers=headers)
            
            if revocation_response.status_code != 200:
                return {
                    "success": False,
                    "error": "Token revocation request failed",
                    "status_code": revocation_response.status_code,
                    "response_text": revocation_response.text
                }
            
            # Try to use the revoked token
            post_revocation_validation = validate_token(
                self.session,
                self.config.sapi_url,
                token_to_revoke
            )
            
            # Token should be invalid after revocation
            if post_revocation_validation.get("valid", True):
                return {
                    "success": False,
                    "error": "Token still valid after revocation",
                    "validation_result": post_revocation_validation
                }
            
            return {
                "success": True,
                "pre_revocation_validation": initial_validation,
                "post_revocation_validation": post_revocation_validation,
                "message": "Token revocation successful"
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error during token revocation test: {str(e)}"
            }
    
    def test_token_security(self, test_data):
        """
        Test token security against tampering.
        
        Args:
            test_data (dict): Test data
            
        Returns:
            dict: Test details including success status and security test results
        """
        LOGGER.info("Testing token security against tampering")
        
        # Get or generate token
        client_id = test_data.get("client_id", self.config.test_client_id)
        token = self.tokens.get(client_id)
        
        if not token:
            # Generate token if not available
            token_result = self.test_token_generation(test_data)
            if not token_result.get("success"):
                return {
                    "success": False,
                    "error": "Failed to generate token for security test",
                    "token_generation_error": token_result.get("error")
                }
            token = token_result["token"]
        
        try:
            # Validate the original token first
            original_validation = validate_token(
                self.session,
                self.config.sapi_url,
                token
            )
            
            if not original_validation.get("valid", False):
                return {
                    "success": False,
                    "error": "Original token is invalid before security tests",
                    "validation_result": original_validation
                }
            
            # Test 1: Tamper with signature
            tampered_signature = tamper_with_token(token, "signature")
            signature_validation = validate_token(
                self.session,
                self.config.sapi_url,
                tampered_signature
            )
            
            # Should be invalid
            if signature_validation.get("valid", True):
                return {
                    "success": False,
                    "error": "Token with tampered signature was accepted",
                    "validation_result": signature_validation
                }
            
            # Test 2: Tamper with payload (modify claims)
            tampered_payload = tamper_with_token(token, "payload", {
                "exp": int(time.time()) + 9999999  # Far future expiration
            })
            payload_validation = validate_token(
                self.session,
                self.config.sapi_url,
                tampered_payload
            )
            
            # Should be invalid
            if payload_validation.get("valid", True):
                return {
                    "success": False,
                    "error": "Token with tampered payload was accepted",
                    "validation_result": payload_validation
                }
            
            # Test 3: Tamper with header (change algorithm)
            tampered_header = tamper_with_token(token, "header", {
                "alg": "none"  # Insecure algorithm
            })
            header_validation = validate_token(
                self.session,
                self.config.sapi_url,
                tampered_header
            )
            
            # Should be invalid
            if header_validation.get("valid", True):
                return {
                    "success": False,
                    "error": "Token with tampered header was accepted",
                    "validation_result": header_validation
                }
            
            # All security tests passed
            return {
                "success": True,
                "tamper_test_results": {
                    "original_token_valid": original_validation.get("valid", False),
                    "tampered_signature_valid": signature_validation.get("valid", False),
                    "tampered_payload_valid": payload_validation.get("valid", False),
                    "tampered_header_valid": header_validation.get("valid", False)
                },
                "message": "All token security tests passed - tampered tokens were correctly rejected"
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": f"Error during token security test: {str(e)}"
            }
    
    def get_summary(self):
        """
        Generate a summary of test results.
        
        Returns:
            dict: Summary of test results including pass/fail counts
        """
        total_tests = len(self.results)
        passed_tests = sum(1 for r in self.results if r.success)
        failed_tests = total_tests - passed_tests
        success_rate = (passed_tests / total_tests) * 100 if total_tests > 0 else 0
        
        return {
            "total_tests": total_tests,
            "passed_tests": passed_tests,
            "failed_tests": failed_tests,
            "success_rate": success_rate
        }


def main():
    """
    Main function for executing token generation tests.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    # Parse command line arguments
    args = parse_arguments()
    
    # Setup logging with appropriate verbosity
    log_level = logging.DEBUG if args.verbose else logging.INFO
    setup_logging(log_level=log_level)
    
    LOGGER.info("Starting token generation testing")
    
    # Load test configuration
    config = get_test_config(args.config, args.env)
    if not config.validate():
        LOGGER.error("Invalid test configuration. Aborting.")
        return 1
    
    # Create test runner
    test_runner = TokenGenerationTestRunner(config)
    
    # Determine which test scenarios to run
    if args.scenario:
        scenarios = [args.scenario]
    else:
        scenarios = list(TOKEN_TEST_SCENARIOS.keys())
    
    LOGGER.info(f"Running test scenarios: {', '.join(scenarios)}")
    
    # Execute the tests
    results = test_runner.run_tests(scenarios)
    
    # Generate report path
    report_path = generate_test_report_path('token_generation', args.output_format)
    
    # Save test results
    save_test_results(results, report_path, args.output_format)
    
    # Generate and save report
    report_format = 'html'  # HTML is more readable for reports
    report_path = report_path.replace(f'.{args.output_format}', f'.{report_format}')
    generate_test_report(results, report_path, report_format)
    
    # Get test summary
    summary = test_runner.get_summary()
    
    # Log test completion
    LOGGER.info(f"Testing completed. Results saved to {report_path}")
    LOGGER.info(f"Total: {summary['total_tests']}, "
                f"Passed: {summary['passed_tests']}, "
                f"Failed: {summary['failed_tests']}, "
                f"Success rate: {summary['success_rate']:.2f}%")
    
    # Return success if all tests passed
    return 0 if summary['failed_tests'] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())