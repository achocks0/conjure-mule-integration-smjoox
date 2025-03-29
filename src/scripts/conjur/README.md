# Conjur Vault Integration Scripts

Python scripts for integrating with Conjur vault for secure credential management in the Payment API Security Enhancement project.

## Overview

This package provides a comprehensive set of scripts for interacting with Conjur vault to securely store, retrieve, and rotate client credentials. The scripts implement the security requirements specified in the Payment API Security Enhancement project, ensuring secure communication, proper error handling, and zero-downtime credential rotation.

## Features

- Secure authentication with Conjur vault using certificate-based authentication
- Retrieval of client credentials with caching and retry mechanisms
- Storage of client credentials with proper validation and security measures
- Zero-downtime credential rotation with configurable transition periods
- Vault setup and initialization with policy management
- Comprehensive error handling and logging
- Command-line interface for all operations

## Installation

### Prerequisites
- Python 3.9 or higher
- Access to Conjur vault instance
- Required Python packages (see requirements.txt)

### Setup
```bash
# Install required packages
pip install -r requirements.txt

# Set up environment variables (optional)
export CONJUR_URL=https://conjur.example.com
export CONJUR_ACCOUNT=payment-system
export CONJUR_AUTHN_LOGIN=payment-eapi-service
export CONJUR_CERT_PATH=/path/to/conjur/certificate.pem
```

## Configuration

The scripts can be configured using environment variables, configuration files, or command-line arguments.

### Environment Variables
- `CONJUR_URL`: URL of the Conjur vault instance
- `CONJUR_ACCOUNT`: Conjur account name
- `CONJUR_AUTHN_LOGIN`: Service identity for authentication
- `CONJUR_CERT_PATH`: Path to Conjur SSL certificate
- `CONJUR_CREDENTIAL_PATH_TEMPLATE`: Template for credential paths

### Configuration File
You can also use a JSON or YAML configuration file:

```json
{
  "url": "https://conjur.example.com",
  "account": "payment-system",
  "authn_login": "payment-eapi-service",
  "cert_path": "/path/to/conjur/certificate.pem",
  "credential_path_template": "secrets/{account}/variable/payment/credentials/{client_id}"
}
```

### Rotation Configuration
For credential rotation, additional configuration is available:

```json
{
  "transition_period_seconds": 86400,
  "monitoring_interval_seconds": 300,
  "notification_endpoint": "https://notification.example.com/api/notify",
  "monitoring_endpoint": "https://monitoring.example.com/api/usage"
}
```

## Usage

### Authentication
```bash
# Authenticate with Conjur vault
python authenticate.py --config-file config.json
```

### Retrieving Credentials
```bash
# Retrieve a credential
python retrieve_credentials.py --client-id client123 --config-file config.json
```

### Storing Credentials
```bash
# Store a credential
python store_credentials.py --client-id client123 --client-secret secret123 --config-file config.json

# Generate and store a new credential
python store_credentials.py --client-id client123 --generate --config-file config.json
```

### Rotating Credentials
```bash
# Rotate a credential with default transition period
python rotate_credentials.py --client-id client123 --config-file config.json

# Rotate with custom transition period
python rotate_credentials.py --client-id client123 --transition-period 3600 --config-file config.json
```

### Setting Up Vault
```bash
# Set up vault with policies
python setup_vault.py --config-file config.json --policy-dir /path/to/policies

# Set up initial credentials
python setup_vault.py --config-file config.json --client-ids client123,client456
```

## Module Reference

### config.py
Configuration module for Conjur vault integration. Provides configuration classes, logging setup, and utility functions for credential path management.

### utils.py
Utility module providing common functions for HTTP requests, error handling, retry mechanisms, and secure data handling.

### authenticate.py
Module for authenticating with Conjur vault. Provides functions to securely authenticate and retrieve authentication tokens.

### retrieve_credentials.py
Module for retrieving credentials from Conjur vault with error handling, retry mechanisms, and caching.

### store_credentials.py
Module for storing credentials in Conjur vault with validation and security measures.

### rotate_credentials.py
Module for rotating credentials with a transition period where both old and new credentials are valid.

### setup_vault.py
Script for setting up and initializing Conjur vault with necessary policies and initial configuration.

## Security Considerations

- All communication with Conjur vault is encrypted using TLS 1.2+
- Sensitive data is never logged or exposed in error messages
- Certificate-based authentication is used for secure service identity
- Credential rotation supports zero-downtime updates
- Retry mechanisms include exponential backoff with jitter to prevent thundering herd problems
- Cached credentials are stored securely with proper expiration

## Error Handling

The scripts implement comprehensive error handling with specific exception classes:

- `ConjurError`: Base exception class for Conjur-related errors
- `ConjurConnectionError`: Exception for connection issues
- `ConjurAuthenticationError`: Exception for authentication failures
- `ConjurNotFoundError`: Exception when resources are not found
- `ConjurPermissionError`: Exception for permission issues

All operations include retry mechanisms with configurable backoff to handle transient failures.

## Logging

The scripts use Python's logging module with configurable log levels. Sensitive information is automatically masked in logs using the `sanitize_log_data` function.

```python
# Configure logging
import logging
logging.basicConfig(level=logging.INFO)
```

## Examples

### Complete Credential Rotation Example

```python
from conjur.config import create_conjur_config, get_rotation_config
from conjur.rotate_credentials import rotate_credential_with_retry

# Create configurations
conjur_config = create_conjur_config('conjur_config.json')
rotation_config = get_rotation_config('rotation_config.json')

# Rotate credential with retry
result = rotate_credential_with_retry(
    client_id='client123',
    conjur_config=conjur_config,
    rotation_config=rotation_config,
    max_retries=3,
    backoff_factor=1.5
)

# Check result
if result.success:
    print(f'Credential rotated successfully. New version: {result.new_version}')
    print(f'Transition period: {result.transition_period_seconds} seconds')
else:
    print(f'Rotation failed: {result.error_message}')
```

### Retrieving and Using Credentials

```python
from conjur.config import create_conjur_config
from conjur.retrieve_credentials import retrieve_credential_with_retry

# Create configuration
conjur_config = create_conjur_config('conjur_config.json')

# Retrieve credential
credential = retrieve_credential_with_retry(
    client_id='client123',
    conjur_config=conjur_config,
    max_retries=3,
    backoff_factor=1.5
)

# Use credential (securely)
client_id = credential['client_id']
client_secret = credential['client_secret']
```

## Contributing

Contributions to improve the scripts are welcome. Please follow the standard pull request process and ensure all tests pass before submitting.

## License

This project is licensed under the terms specified in the LICENSE file.