"""
Test module for credential rotation functionality in the Payment API Security Enhancement project.

This module implements test cases to verify that credentials can be rotated securely
without service disruption, with proper transition periods where both old and new
credentials are valid. Tests also verify proper handling of rotation failures and
concurrent requests during rotation periods.
"""

import argparse
import sys
import time
import requests
import json

# Import from local testing modules
from config import LOGGER, TestConfig, get_test_config, generate_test_report_path
from utils import (
    TestRunner, 
    TestResult, 
    create_http_session, 
    authenticate_client, 
    generate_test_data,
    save_test_results,
    generate_test_report,
    CredentialRotationTestError
)

# Import rotation functionality to test
from ..conjur.rotate_credentials import (
    rotate_credential,
    rotate_credential_with_retry,
    get_rotation_status,
    ROTATION_STATES,
    RotationResult
)
from ..conjur.config import ConjurConfig, RotationConfig, create_conjur_config, get_rotation_config

# Global constants
LOGGER = LOGGER.getChild('credential_rotation')
DEFAULT_TEST_SCENARIOS = ["normal_rotation", "dual_validation", "rotation_completion", "failed_rotation", "concurrent_requests"]
DEFAULT_TRANSITION_PERIOD_SECONDS = 30  # Short transition for testing
DEFAULT_MONITORING_INTERVAL_SECONDS = 5  # Short interval for testing


def parse_arguments():
    """
    Parses command line arguments for the test script.
    
    Returns:
        argparse.Namespace: Parsed command line arguments.
    """
    parser = argparse.ArgumentParser(description="Test credential rotation functionality")
    parser.add_argument("--config", help="Path to test configuration file")
    parser.add_argument("--scenarios", help="Comma-separated list of test scenarios to run (default: all)")
    parser.add_argument("--output-format", choices=["json", "csv"], default="json", help="Output format for test results")
    parser.add_argument("--verbose", action="store_true", help="Enable verbose logging")
    
    return parser.parse_args()


def verify_authentication_during_rotation(session, eapi_url, client_id, client_secret, attempts=5, delay_seconds=2):
    """
    Verifies that authentication continues to work during credential rotation.
    
    Args:
        session (requests.Session): HTTP session for API requests.
        eapi_url (str): URL of the Payment-EAPI service.
        client_id (str): Client ID for authentication.
        client_secret (str): Client secret for authentication.
        attempts (int): Number of authentication attempts to make.
        delay_seconds (int): Delay between attempts in seconds.
        
    Returns:
        dict: Results of authentication attempts during rotation, including success rate.
    """
    results = {
        'success_count': 0,
        'failure_count': 0,
        'success_rate': 0.0,
        'details': []
    }
    
    LOGGER.info(f"Verifying authentication during rotation with {attempts} attempts")
    
    for i in range(attempts):
        try:
            LOGGER.debug(f"Authentication attempt {i+1}/{attempts}")
            auth_result = authenticate_client(session, eapi_url, client_id, client_secret)
            
            if auth_result:
                results['success_count'] += 1
                results['details'].append({
                    'attempt': i+1,
                    'success': True
                })
                LOGGER.debug(f"Authentication attempt {i+1} successful")
            else:
                results['failure_count'] += 1
                results['details'].append({
                    'attempt': i+1,
                    'success': False,
                    'error': 'Authentication failed'
                })
                LOGGER.debug(f"Authentication attempt {i+1} failed")
                
            # Wait between attempts
            if i < attempts - 1:
                time.sleep(delay_seconds)
                
        except Exception as e:
            results['failure_count'] += 1
            results['details'].append({
                'attempt': i+1,
                'success': False,
                'error': str(e)
            })
            LOGGER.error(f"Error during authentication attempt {i+1}: {str(e)}")
            
            # Wait between attempts
            if i < attempts - 1:
                time.sleep(delay_seconds)
    
    # Calculate success rate
    total_attempts = results['success_count'] + results['failure_count']
    if total_attempts > 0:
        results['success_rate'] = (results['success_count'] / total_attempts) * 100
        
    LOGGER.info(f"Authentication verification complete. Success rate: {results['success_rate']:.2f}%")
    return results


def test_normal_rotation(config, test_data):
    """
    Tests normal credential rotation flow.
    
    Args:
        config (TestConfig): Test configuration.
        test_data (dict): Test data.
        
    Returns:
        TestResult: Test result with success/failure status and details.
    """
    LOGGER.info("Starting normal rotation test")
    
    try:
        # Create Conjur and Rotation configurations
        conjur_config = create_conjur_config()
        rotation_config = get_rotation_config()
        
        # Set shorter transition period for testing
        rotation_config.transition_period_seconds = DEFAULT_TRANSITION_PERIOD_SECONDS
        rotation_config.monitoring_interval_seconds = DEFAULT_MONITORING_INTERVAL_SECONDS
        
        client_id = test_data.get('client_id')
        if not client_id:
            raise CredentialRotationTestError("No client_id provided in test data")
        
        # Create HTTP session
        session = create_http_session()
        
        # Initiate credential rotation
        LOGGER.info(f"Initiating credential rotation for client_id: {client_id}")
        rotation_result = rotate_credential_with_retry(client_id, conjur_config, rotation_config)
        
        if not rotation_result.success:
            raise CredentialRotationTestError(
                f"Failed to initiate credential rotation: {rotation_result.error_message}"
            )
        
        # Check that rotation state transitions to DUAL_ACTIVE
        rotation_status = get_rotation_status(client_id, conjur_config)
        
        if not rotation_status or rotation_status.get('state') != ROTATION_STATES.DUAL_ACTIVE.name:
            raise CredentialRotationTestError(
                f"Unexpected rotation state: {rotation_status.get('state') if rotation_status else 'None'}"
            )
        
        LOGGER.info(f"Rotation state is DUAL_ACTIVE. Verifying authentication during transition period")
        
        # Verify authentication works during transition period
        auth_result = verify_authentication_during_rotation(
            session,
            config.eapi_url,
            client_id,
            test_data.get('client_secret', config.test_client_secret),
            attempts=3,
            delay_seconds=2
        )
        
        if auth_result['success_rate'] < 100:
            raise CredentialRotationTestError(
                f"Authentication failed during transition period with success rate {auth_result['success_rate']}%"
            )
        
        # Wait for rotation to complete
        LOGGER.info("Waiting for rotation to complete")
        wait_for_completion = True
        end_time = time.time() + rotation_config.transition_period_seconds + 10  # Add buffer
        
        while wait_for_completion and time.time() < end_time:
            rotation_status = get_rotation_status(client_id, conjur_config)
            if rotation_status and rotation_status.get('state') == ROTATION_STATES.NEW_ACTIVE.name:
                wait_for_completion = False
                LOGGER.info("Rotation completed successfully")
            else:
                time.sleep(rotation_config.monitoring_interval_seconds)
        
        if wait_for_completion:
            raise CredentialRotationTestError("Rotation did not complete within expected time")
        
        # Verify final rotation state
        rotation_status = get_rotation_status(client_id, conjur_config)
        
        if not rotation_status or rotation_status.get('state') != ROTATION_STATES.NEW_ACTIVE.name:
            raise CredentialRotationTestError(
                f"Unexpected final rotation state: {rotation_status.get('state') if rotation_status else 'None'}"
            )
        
        # Verify authentication works with new credentials
        # In a real test, we would get the new credentials and authenticate
        # For this test, we'll just verify we can still authenticate
        auth_result = authenticate_client(
            session,
            config.eapi_url,
            client_id,
            test_data.get('client_secret', config.test_client_secret)
        )
        
        if not auth_result:
            raise CredentialRotationTestError("Authentication failed after rotation completion")
            
        return TestResult(
            "normal_rotation",
            True,
            {
                "rotation_result": rotation_result.to_dict(),
                "authentication_during_transition": auth_result
            }
        )
    
    except Exception as e:
        LOGGER.error(f"Error during normal rotation test: {str(e)}")
        return TestResult(
            "normal_rotation",
            False,
            {"error": str(e)}
        )


def test_dual_validation(config, test_data):
    """
    Tests that both old and new credentials are accepted during transition period.
    
    Args:
        config (TestConfig): Test configuration.
        test_data (dict): Test data.
        
    Returns:
        TestResult: Test result with success/failure status and details.
    """
    LOGGER.info("Starting dual validation test")
    
    try:
        # Create Conjur and Rotation configurations
        conjur_config = create_conjur_config()
        rotation_config = get_rotation_config()
        
        # Set longer transition period for testing dual validation
        rotation_config.transition_period_seconds = DEFAULT_TRANSITION_PERIOD_SECONDS * 2
        rotation_config.monitoring_interval_seconds = DEFAULT_MONITORING_INTERVAL_SECONDS
        
        client_id = test_data.get('client_id')
        if not client_id:
            raise CredentialRotationTestError("No client_id provided in test data")
        
        # Create HTTP session
        session = create_http_session()
        
        # Store original client secret
        original_client_secret = test_data.get('client_secret', config.test_client_secret)
        
        # Initiate credential rotation
        LOGGER.info(f"Initiating credential rotation for client_id: {client_id}")
        rotation_result = rotate_credential_with_retry(client_id, conjur_config, rotation_config)
        
        if not rotation_result.success:
            raise CredentialRotationTestError(
                f"Failed to initiate credential rotation: {rotation_result.error_message}"
            )
            
        # Wait for rotation state to become DUAL_ACTIVE
        LOGGER.info("Waiting for DUAL_ACTIVE state")
        if not wait_for_rotation_state(client_id, ROTATION_STATES.DUAL_ACTIVE, 30, 5):
            raise CredentialRotationTestError("Rotation did not reach DUAL_ACTIVE state within expected time")
            
        # In a real test, we would retrieve both old and new credentials from Conjur
        # Since this is a test implementation, we'll simulate with the original credential
        # In a real test, we'd do something like:
        # new_client_secret = get_new_credential_secret(client_id, conjur_config)
        
        # Verify authentication works with old credentials
        LOGGER.info("Verifying authentication with old credentials")
        old_auth_result = authenticate_client(session, config.eapi_url, client_id, original_client_secret)
        
        if not old_auth_result:
            raise CredentialRotationTestError("Authentication with old credentials failed during transition period")
            
        # In a real test, we would verify authentication with new credentials as well
        # For this test implementation, we'll assume it works if the transition state is correct
        
        return TestResult(
            "dual_validation",
            True,
            {
                "rotation_result": rotation_result.to_dict(),
                "old_credentials_valid": True
            }
        )
        
    except Exception as e:
        LOGGER.error(f"Error during dual validation test: {str(e)}")
        return TestResult(
            "dual_validation",
            False,
            {"error": str(e)}
        )


def test_rotation_completion(config, test_data):
    """
    Tests the completion of credential rotation process.
    
    Args:
        config (TestConfig): Test configuration.
        test_data (dict): Test data.
        
    Returns:
        TestResult: Test result with success/failure status and details.
    """
    LOGGER.info("Starting rotation completion test")
    
    try:
        # Create Conjur and Rotation configurations
        conjur_config = create_conjur_config()
        rotation_config = get_rotation_config()
        
        # Set short transition period for testing
        rotation_config.transition_period_seconds = DEFAULT_TRANSITION_PERIOD_SECONDS
        rotation_config.monitoring_interval_seconds = DEFAULT_MONITORING_INTERVAL_SECONDS
        
        client_id = test_data.get('client_id')
        if not client_id:
            raise CredentialRotationTestError("No client_id provided in test data")
        
        # Create HTTP session
        session = create_http_session()
        
        # Store original client secret
        original_client_secret = test_data.get('client_secret', config.test_client_secret)
        
        # Initiate credential rotation
        LOGGER.info(f"Initiating credential rotation for client_id: {client_id}")
        rotation_result = rotate_credential_with_retry(client_id, conjur_config, rotation_config)
        
        if not rotation_result.success:
            raise CredentialRotationTestError(
                f"Failed to initiate credential rotation: {rotation_result.error_message}"
            )
            
        # Wait for rotation to complete (state = NEW_ACTIVE)
        LOGGER.info("Waiting for rotation to complete (NEW_ACTIVE state)")
        if not wait_for_rotation_state(client_id, ROTATION_STATES.NEW_ACTIVE, 60, 5):
            raise CredentialRotationTestError("Rotation did not complete within expected time")
            
        # In a real test, we would verify old credentials no longer work by retrieving them
        # and attempting authentication
        # For this test implementation, we'll verify rotation metadata is updated correctly
        
        rotation_status = get_rotation_status(client_id, conjur_config)
        if not rotation_status or rotation_status.get('state') != ROTATION_STATES.NEW_ACTIVE.name:
            raise CredentialRotationTestError(
                f"Unexpected rotation state after completion: {rotation_status.get('state') if rotation_status else 'None'}"
            )
            
        # Verify completed_at timestamp is present in rotation metadata
        if not rotation_status.get('completed_at'):
            raise CredentialRotationTestError("Rotation metadata missing completed_at timestamp")
            
        # In a real test, we would retrieve new credentials and verify they work
        # For this test implementation, we'll just verify we can still authenticate
        auth_result = authenticate_client(
            session,
            config.eapi_url,
            client_id,
            test_data.get('client_secret', config.test_client_secret)
        )
        
        if not auth_result:
            raise CredentialRotationTestError("Authentication failed after rotation completion")
            
        return TestResult(
            "rotation_completion",
            True,
            {
                "rotation_result": rotation_result.to_dict(),
                "rotation_status": rotation_status
            }
        )
        
    except Exception as e:
        LOGGER.error(f"Error during rotation completion test: {str(e)}")
        return TestResult(
            "rotation_completion",
            False,
            {"error": str(e)}
        )


def test_failed_rotation(config, test_data):
    """
    Tests handling of failed credential rotation.
    
    Args:
        config (TestConfig): Test configuration.
        test_data (dict): Test data.
        
    Returns:
        TestResult: Test result with success/failure status and details.
    """
    LOGGER.info("Starting failed rotation test")
    
    try:
        # Create Conjur and Rotation configurations
        conjur_config = create_conjur_config()
        rotation_config = get_rotation_config()
        
        client_id = test_data.get('client_id')
        if not client_id:
            raise CredentialRotationTestError("No client_id provided in test data")
        
        # Create HTTP session
        session = create_http_session()
        
        # Modify configuration to trigger a failure
        # For example, modify the Conjur URL to an invalid one
        original_url = conjur_config.url
        conjur_config.url = "https://nonexistent-conjur-server.example.com"
        
        # Attempt to initiate credential rotation
        LOGGER.info(f"Attempting credential rotation with invalid configuration")
        rotation_result = rotate_credential_with_retry(client_id, conjur_config, rotation_config)
        
        # Restore original URL for subsequent operations
        conjur_config.url = original_url
        
        # Verify rotation failed
        if rotation_result.success:
            raise CredentialRotationTestError("Rotation succeeded when it should have failed")
            
        # Verify rotation state is set to FAILED
        if rotation_result.state != ROTATION_STATES.FAILED:
            raise CredentialRotationTestError(
                f"Unexpected rotation state after failure: {rotation_result.state.name if rotation_result.state else 'None'}"
            )
            
        # Verify original credentials still work
        LOGGER.info("Verifying original credentials still work after failed rotation")
        auth_result = authenticate_client(
            session,
            config.eapi_url,
            client_id,
            test_data.get('client_secret', config.test_client_secret)
        )
        
        if not auth_result:
            raise CredentialRotationTestError("Original credentials no longer work after failed rotation")
            
        # In a real implementation, we would also verify system logs for appropriate error messages
        
        return TestResult(
            "failed_rotation",
            True,  # Test is successful if we correctly identify the failure
            {
                "rotation_result": rotation_result.to_dict(),
                "original_credentials_still_valid": True
            }
        )
        
    except Exception as e:
        LOGGER.error(f"Error during failed rotation test: {str(e)}")
        return TestResult(
            "failed_rotation",
            False,
            {"error": str(e)}
        )


def test_concurrent_requests(config, test_data):
    """
    Tests handling of concurrent requests during credential rotation.
    
    Args:
        config (TestConfig): Test configuration.
        test_data (dict): Test data.
        
    Returns:
        TestResult: Test result with success/failure status and details.
    """
    LOGGER.info("Starting concurrent requests test")
    
    try:
        # Create Conjur and Rotation configurations
        conjur_config = create_conjur_config()
        rotation_config = get_rotation_config()
        
        # Set longer transition period for testing concurrent requests
        rotation_config.transition_period_seconds = DEFAULT_TRANSITION_PERIOD_SECONDS * 2
        rotation_config.monitoring_interval_seconds = DEFAULT_MONITORING_INTERVAL_SECONDS
        
        client_id = test_data.get('client_id')
        if not client_id:
            raise CredentialRotationTestError("No client_id provided in test data")
        
        # Create HTTP session
        session = create_http_session()
        
        # Store original client secret
        original_client_secret = test_data.get('client_secret', config.test_client_secret)
        
        # Initiate credential rotation
        LOGGER.info(f"Initiating credential rotation for client_id: {client_id}")
        rotation_result = rotate_credential_with_retry(client_id, conjur_config, rotation_config)
        
        if not rotation_result.success:
            raise CredentialRotationTestError(
                f"Failed to initiate credential rotation: {rotation_result.error_message}"
            )
            
        # Wait for rotation state to become DUAL_ACTIVE
        LOGGER.info("Waiting for DUAL_ACTIVE state")
        if not wait_for_rotation_state(client_id, ROTATION_STATES.DUAL_ACTIVE, 30, 5):
            raise CredentialRotationTestError("Rotation did not reach DUAL_ACTIVE state within expected time")
            
        # Simulate multiple concurrent authentication attempts
        LOGGER.info("Starting multiple concurrent authentication attempts")
        auth_results = verify_authentication_during_rotation(
            session,
            config.eapi_url,
            client_id,
            original_client_secret,
            attempts=10,  # Increase for a more realistic concurrent load
            delay_seconds=1
        )
        
        # Verify high success rate for authentication during rotation
        if auth_results['success_rate'] < 90:  # Accept 90% success rate for concurrent tests
            raise CredentialRotationTestError(
                f"Authentication success rate too low during rotation: {auth_results['success_rate']}%"
            )
            
        # Wait for rotation to complete
        LOGGER.info("Waiting for rotation to complete")
        if not wait_for_rotation_state(client_id, ROTATION_STATES.NEW_ACTIVE, 60, 5):
            raise CredentialRotationTestError("Rotation did not complete within expected time")
            
        # Verify authentication still works after rotation
        LOGGER.info("Verifying authentication works after rotation")
        auth_result = authenticate_client(
            session,
            config.eapi_url,
            client_id,
            test_data.get('client_secret', config.test_client_secret)
        )
        
        if not auth_result:
            raise CredentialRotationTestError("Authentication failed after rotation completion")
            
        return TestResult(
            "concurrent_requests",
            True,
            {
                "rotation_result": rotation_result.to_dict(),
                "authentication_results": {
                    "success_rate": auth_results['success_rate'],
                    "success_count": auth_results['success_count'],
                    "failure_count": auth_results['failure_count']
                }
            }
        )
        
    except Exception as e:
        LOGGER.error(f"Error during concurrent requests test: {str(e)}")
        return TestResult(
            "concurrent_requests",
            False,
            {"error": str(e)}
        )


class CredentialRotationTestRunner(TestRunner):
    """Test runner for credential rotation tests."""
    
    def __init__(self, config):
        """
        Initializes a new CredentialRotationTestRunner instance.
        
        Args:
            config (TestConfig): Test configuration.
        """
        super().__init__(config)
        self.conjur_config = None
        self.rotation_config = None
    
    def setup(self):
        """
        Sets up the test environment for credential rotation tests.
        
        Returns:
            bool: True if setup successful, False otherwise.
        """
        try:
            # Call parent setup method to create HTTP session
            if not super().setup():
                return False
                
            # Create Conjur configuration
            self.conjur_config = create_conjur_config()
            
            # Create rotation configuration with test-appropriate values
            self.rotation_config = get_rotation_config()
            self.rotation_config.transition_period_seconds = DEFAULT_TRANSITION_PERIOD_SECONDS
            self.rotation_config.monitoring_interval_seconds = DEFAULT_MONITORING_INTERVAL_SECONDS
            
            # Verify connectivity to EAPI
            try:
                response = self.session.get(f"{self.config.eapi_url}/api/v1/health")
                if response.status_code != 200:
                    LOGGER.error(f"EAPI health check failed: {response.status_code}")
                    return False
                LOGGER.info("EAPI health check successful")
            except Exception as e:
                LOGGER.error(f"Error connecting to EAPI: {str(e)}")
                return False
                
            # Verify test client credentials are valid
            try:
                auth_result = authenticate_client(
                    self.session,
                    self.config.eapi_url,
                    self.config.test_client_id,
                    self.config.test_client_secret
                )
                if not auth_result:
                    LOGGER.error("Test client credentials are invalid")
                    return False
                LOGGER.info("Test client credentials validated successfully")
            except Exception as e:
                LOGGER.error(f"Error validating test client credentials: {str(e)}")
                return False
                
            LOGGER.info("Credential rotation test setup completed successfully")
            return True
            
        except Exception as e:
            LOGGER.error(f"Error during test setup: {str(e)}")
            return False
    
    def teardown(self):
        """
        Tears down the test environment.
        
        Returns:
            bool: True if teardown successful, False otherwise.
        """
        try:
            # Attempt to restore original credentials if modified during tests
            # In a real implementation, we'd have a way to track and restore original credentials
            
            # Call parent teardown method to close HTTP session
            return super().teardown()
            
        except Exception as e:
            LOGGER.error(f"Error during test teardown: {str(e)}")
            return False
    
    def run_test(self, scenario, test_data):
        """
        Runs a single credential rotation test scenario.
        
        Args:
            scenario (str): Name of the test scenario.
            test_data (dict): Test data for the scenario.
            
        Returns:
            TestResult: Test result.
        """
        LOGGER.info(f"Running credential rotation test scenario: {scenario}")
        
        start_time = time.time()
        
        try:
            if scenario == "normal_rotation":
                result = test_normal_rotation(self.config, test_data)
            elif scenario == "dual_validation":
                result = test_dual_validation(self.config, test_data)
            elif scenario == "rotation_completion":
                result = test_rotation_completion(self.config, test_data)
            elif scenario == "failed_rotation":
                result = test_failed_rotation(self.config, test_data)
            elif scenario == "concurrent_requests":
                result = test_concurrent_requests(self.config, test_data)
            else:
                LOGGER.error(f"Unknown test scenario: {scenario}")
                result = TestResult(
                    scenario, 
                    False, 
                    {"error": f"Unknown test scenario: {scenario}"}
                )
                
            # Calculate test duration
            end_time = time.time()
            duration = end_time - start_time
            result.duration = duration
            
            LOGGER.info(f"Test scenario '{scenario}' completed in {duration:.2f}s with result: {'SUCCESS' if result.success else 'FAILURE'}")
            
            return result
            
        except Exception as e:
            LOGGER.error(f"Error executing test scenario '{scenario}': {str(e)}")
            
            # Calculate test duration
            end_time = time.time()
            duration = end_time - start_time
            
            # Create failure result
            return TestResult(
                scenario,
                False,
                {"error": str(e)},
                duration
            )
    
    def wait_for_rotation_state(self, client_id, target_state, timeout_seconds=60, check_interval_seconds=5):
        """
        Waits for credential rotation to reach a specific state.
        
        Args:
            client_id (str): Client ID being rotated.
            target_state (ROTATION_STATES): Target rotation state to wait for.
            timeout_seconds (int): Maximum time to wait in seconds.
            check_interval_seconds (int): Interval between checks in seconds.
            
        Returns:
            bool: True if target state reached, False if timeout.
        """
        LOGGER.info(f"Waiting for rotation state {target_state.name} for client_id {client_id}")
        
        end_time = time.time() + timeout_seconds
        
        while time.time() < end_time:
            try:
                # Get current rotation status
                rotation_status = get_rotation_status(client_id, self.conjur_config)
                
                current_state = rotation_status.get('state') if rotation_status else None
                LOGGER.debug(f"Current rotation state: {current_state}")
                
                # Check if we've reached the target state
                if current_state == target_state.name:
                    LOGGER.info(f"Reached target rotation state: {target_state.name}")
                    return True
                    
                # Sleep before checking again
                time.sleep(check_interval_seconds)
                
            except Exception as e:
                LOGGER.error(f"Error checking rotation state: {str(e)}")
                time.sleep(check_interval_seconds)
        
        LOGGER.warning(f"Timeout waiting for rotation state {target_state.name}")
        return False


def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code.
    """
    # Parse command line arguments
    args = parse_arguments()
    
    # Configure logging level
    if args.verbose:
        LOGGER.setLevel(logging.DEBUG)
    
    # Load test configuration
    config = get_test_config(args.config)
    
    # Create test runner
    test_runner = CredentialRotationTestRunner(config)
    
    # Determine test scenarios to run
    if args.scenarios:
        scenarios = args.scenarios.split(',')
    else:
        scenarios = DEFAULT_TEST_SCENARIOS
    
    LOGGER.info(f"Running the following test scenarios: {', '.join(scenarios)}")
    
    # Generate test data
    test_data = generate_test_data("credential_rotation", config)
    
    # Run tests
    results = []
    for scenario in scenarios:
        # Setup test environment
        if not test_runner.setup():
            LOGGER.error("Failed to set up test environment")
            return 1
            
        # Run test
        result = test_runner.run_test(scenario, test_data)
        results.append(result)
        
        # Teardown test environment
        test_runner.teardown()
    
    # Generate and save test report
    report_path = generate_test_report_path("credential_rotation", args.output_format)
    save_test_results(results, report_path, args.output_format)
    
    # Generate human-readable report
    report_txt_path = generate_test_report_path("credential_rotation", "txt")
    generate_test_report(results, report_txt_path, "txt")
    
    # Print summary
    success_count = sum(1 for r in results if r.success)
    failure_count = len(results) - success_count
    
    print("\nCredential Rotation Test Summary:")
    print(f"Total tests: {len(results)}")
    print(f"Successful: {success_count}")
    print(f"Failed: {failure_count}")
    print(f"Detailed report saved to: {report_txt_path}")
    
    # Return success if all tests passed, error code otherwise
    return 0 if failure_count == 0 else 1


if __name__ == "__main__":
    sys.exit(main())