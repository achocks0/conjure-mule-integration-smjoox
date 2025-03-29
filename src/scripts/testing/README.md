# Payment API Security Enhancement Testing Framework

This directory contains test scripts and utilities for validating the security enhancements to the Payment API, focusing on authentication mechanisms, token management, and credential rotation functionality.

## Test Scripts Overview

The testing framework includes the following main test scripts:

- `test_authentication.py`: Tests the authentication flow using Client ID/Secret headers
- `test_token_generation.py`: Tests token generation, validation, and security aspects
- `test_credential_rotation.py`: Tests credential rotation without service disruption
- `load_test_auth.py`: Performs load testing on the authentication mechanism

Supporting modules:
- `config.py`: Configuration utilities and test settings
- `utils.py`: Common test utilities and helper functions

## Prerequisites

- Python 3.9 or higher
- Required Python packages (install using `pip install -r requirements.txt`):
  - requests>=2.28.1
  - pyyaml>=6.0
  - matplotlib>=3.5.2
  - numpy>=1.23.1
- Access to the Payment API endpoints (EAPI, SAPI)
- Test credentials configured in Conjur vault

## Configuration

Test configuration can be provided in several ways:

1. Configuration file (JSON or YAML)
2. Environment variables
3. Command-line arguments

### Configuration File Example
```yaml
eapi_url: https://payment-eapi.example.com
sapi_url: https://payment-sapi.example.com
conjur_url: https://conjur.example.com
test_client_id: test-client
test_client_secret: test-secret
test_data_dir: ./test_data
test_report_dir: ./test_reports
```

### Environment Variables
Configuration can also be provided through environment variables:

```bash
export TEST_EAPI_URL=https://payment-eapi.example.com
export TEST_SAPI_URL=https://payment-sapi.example.com
export TEST_CONJUR_URL=https://conjur.example.com
export TEST_CLIENT_ID=test-client
export TEST_CLIENT_SECRET=test-secret
export TEST_DATA_DIR=./test_data
export TEST_REPORT_DIR=./test_reports
```

## Usage

### Authentication Testing
```bash
python test_authentication.py --config config.yaml --env dev --output json
```

### Token Generation Testing
```bash
python test_token_generation.py --config config.yaml --env dev --output json
```

### Credential Rotation Testing
```bash
python test_credential_rotation.py --config config.yaml --env dev --output json
```

### Load Testing
```bash
python load_test_auth.py --config config.yaml --env dev --duration 60 --users 20 --ramp-up 5 --graphs
```

### Command-line Arguments
- `--config`: Path to configuration file (optional)
- `--env`: Environment to test (dev, test, staging, prod)
- `--output`: Output format for test results (json, csv, html)
- `--scenario`: Specific test scenario to run (optional)
- `--verbose`: Enable verbose logging

Additional arguments for load testing:
- `--duration`: Test duration in seconds
- `--users`: Number of concurrent users
- `--ramp-up`: Ramp-up period in seconds
- `--graphs`: Generate performance graphs

## Test Scenarios

### Authentication Test Scenarios
- `valid_authentication`: Test authentication with valid credentials
- `invalid_client_id`: Test authentication with invalid client ID
- `invalid_client_secret`: Test authentication with invalid client secret
- `missing_credentials`: Test authentication with missing credentials
- `token_validation`: Test token validation
- `token_expiration`: Test token expiration handling
- `token_tampering`: Test security against token tampering
- `brute_force_protection`: Test brute force protection mechanisms

### Token Generation Test Scenarios
- `token_generation`: Test token generation with valid credentials
- `token_properties`: Test token properties and claims
- `token_validation`: Test token validation
- `token_expiration`: Test token expiration handling
- `token_renewal`: Test token renewal process
- `token_revocation`: Test token revocation
- `token_security`: Test token security against tampering

### Credential Rotation Test Scenarios
- `normal_rotation`: Test normal credential rotation flow
- `dual_validation`: Test dual validation during transition period
- `rotation_completion`: Test credential rotation completion
- `failed_rotation`: Test handling of failed credential rotation
- `concurrent_requests`: Test concurrent requests during credential rotation

## Test Reports

Test reports are generated in the specified format (JSON, CSV, or HTML) and saved to the configured report directory. Reports include:

- Test results for each scenario
- Success/failure status
- Execution time
- Detailed information about failures
- Performance metrics (for load tests)

For load tests, performance graphs can be generated showing:
- Response time distribution
- Response time over time
- Throughput over time
- Error rate over time

## Best Practices

1. **Use dedicated test credentials**: Never use production credentials for testing
2. **Run tests in isolated environments**: Start with dev environment before testing in staging
3. **Review test reports carefully**: Pay attention to performance metrics and error patterns
4. **Automate test execution**: Integrate tests into CI/CD pipeline
5. **Maintain test data**: Keep test data up-to-date with system changes
6. **Monitor resource usage**: Be aware of resource consumption during load tests
7. **Secure test credentials**: Don't commit test credentials to version control

## Extending the Framework

The testing framework is designed to be extensible. To add new test scenarios:

1. Add new test methods to the appropriate test runner class
2. Update the test scenarios dictionary in the script
3. Implement the test logic using the utility functions

Example of adding a new test scenario:
```python
def test_new_scenario(self, test_data):
    # Implement test logic
    # Return test details with success status
    return {
        'success': True,
        'details': {
            'message': 'Test passed successfully',
            'data': result_data
        }
    }

# Add to scenarios dictionary
TEST_SCENARIOS['new_scenario'] = 'Description of new test scenario'
```

## Troubleshooting

### Common Issues

1. **Connection errors**:
   - Verify API endpoints are accessible
   - Check network connectivity and firewall settings

2. **Authentication failures**:
   - Verify test credentials are correct
   - Ensure test client is properly configured in Conjur vault

3. **Configuration issues**:
   - Check configuration file format
   - Verify environment variables are set correctly

4. **Performance test failures**:
   - Adjust concurrency settings
   - Check for resource limitations on test machine

### Logging

Enable verbose logging with the `--verbose` flag for detailed information about test execution. Logs include:
- API requests and responses (with sensitive data masked)
- Test execution flow
- Error details and stack traces

## Contributing

When contributing to the testing framework:

1. Follow the existing code style and patterns
2. Add appropriate documentation for new features
3. Include unit tests for utility functions
4. Update this README with new test scenarios or configuration options
5. Test changes thoroughly before submitting pull requests