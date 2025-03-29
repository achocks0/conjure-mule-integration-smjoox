# Payment API Security Enhancement Scripts

This directory contains utility scripts for the Payment API Security Enhancement project. These scripts provide tools for Conjur vault integration, monitoring, deployment, testing, and general utilities to support the implementation and maintenance of secure authentication mechanisms.

## Directory Structure

The scripts are organized into the following subdirectories:

- `conjur/`: Scripts for Conjur vault integration and credential management
- `monitoring/`: Scripts for system monitoring, health checks, and metrics collection
- `deployment/`: Scripts for environment setup, backup, restore, and synchronization
- `testing/`: Scripts for testing authentication, token management, and credential rotation
- `utilities/`: General utility scripts for maintenance and operations

Each subdirectory contains its own README with detailed documentation specific to that module.

## Prerequisites

Before using these scripts, ensure you have the following prerequisites installed:

- Python 3.9 or higher
- Required Python packages (install using `pip install -r requirements.txt`)
- Access to Conjur vault instance (for credential management scripts)
- Database access (for metadata scripts)
- Kubernetes access (for deployment scripts)

Specific requirements for each module are detailed in their respective README files.

## Installation

```bash
# Clone the repository
git clone <repository-url>
cd <repository-directory>/src/scripts

# Install required packages
pip install -r requirements.txt

# Set up environment variables (optional)
export CONJUR_URL=https://conjur.example.com
export CONJUR_ACCOUNT=payment-system
export CONJUR_AUTHN_LOGIN=payment-eapi-service
```

You can also install the scripts as a package for easier importing:

```bash
pip install -e .
```

## Configuration

Most scripts support configuration through:

1. Configuration files (JSON or YAML)
2. Environment variables
3. Command-line arguments

The configuration precedence is: Command-line arguments > Environment variables > Configuration files > Default values.

Common configuration parameters include:

- API endpoints (EAPI, SAPI)
- Conjur vault connection details
- Database connection details
- Redis cache connection details
- Logging settings
- Environment-specific settings (dev, test, staging, prod)

See each module's README for specific configuration options.

## Module Overview

### Conjur Integration

The `conjur/` directory contains scripts for integrating with Conjur vault for secure credential management:

- Authentication with Conjur vault
- Retrieving credentials securely
- Storing and updating credentials
- Rotating credentials with zero downtime
- Setting up and configuring Conjur vault

See [conjur/README.md](conjur/README.md) for detailed documentation.

### Monitoring

The `monitoring/` directory contains scripts for monitoring system health and performance:

- Health checks for all system components
- Token usage metrics collection and analysis
- Credential usage monitoring
- Integration with Prometheus, ELK Stack, and other monitoring systems

See [monitoring/README.md](monitoring/README.md) for detailed documentation.

### Deployment

The `deployment/` directory contains scripts for managing deployment environments:

- Environment setup and initialization
- Metadata backup and restore
- Environment synchronization for staged promotion
- Support for credential rotation during deployment

See [deployment/README.md](deployment/README.md) for detailed documentation.

### Testing

The `testing/` directory contains scripts for testing the security enhancements:

- Authentication testing with various scenarios
- Token generation and validation testing
- Credential rotation testing
- Load testing for performance evaluation

See [testing/README.md](testing/README.md) for detailed documentation.

### Utilities

The `utilities/` directory contains general utility scripts:

- Token cleanup and validation
- Database maintenance and optimization
- Test credential generation
- Other maintenance utilities

See [utilities/README.md](utilities/README.md) for detailed documentation.

## Common Usage Examples

### Conjur Vault Integration
```bash
# Authenticate with Conjur vault
python conjur/authenticate.py --config-file config.json

# Retrieve credentials
python conjur/retrieve_credentials.py --client-id client123 --config-file config.json

# Rotate credentials
python conjur/rotate_credentials.py --client-id client123 --config-file config.json
```

### Monitoring
```bash
# Run health checks
python monitoring/health_check.py --interval 60 --verbose

# Collect token metrics
python monitoring/token_usage_metrics.py --output token_metrics.json
```

### Deployment
```bash
# Set up environment
python deployment/setup_environments.py --environment development --config-file config.yml

# Backup metadata
python deployment/backup_metadata.py --environment production --output-dir /path/to/backups

# Sync environments
python deployment/sync_environments.py --source-environment staging --target-environment production
```

### Testing
```bash
# Run authentication tests
python testing/test_authentication.py --config config.yaml --env dev

# Run load tests
python testing/load_test_auth.py --config config.yaml --duration 60 --users 20
```

### Utilities
```bash
# Clean up expired tokens
python utilities/cleanup_expired_tokens.py --batch-size 1000

# Generate test credentials
python utilities/generate_test_credentials.py --num 10 --prefix test-vendor-
```

## Scheduling and Automation

Many scripts are designed to be run on a schedule for regular maintenance and monitoring. Recommended scheduling:

- **Health Checks**: Every 5 minutes
- **Token Cleanup**: Hourly
- **Metric Collection**: Every 5-15 minutes
- **Database Maintenance**: Monthly
- **Credential Rotation**: As needed or on a regular schedule (e.g., quarterly)

Example cron configuration:
```bash
# Health checks every 5 minutes
*/5 * * * * cd /path/to/scripts && python monitoring/health_check.py --single-run >> /var/log/monitoring/health_check.log 2>&1

# Token cleanup hourly
0 * * * * cd /path/to/scripts && python utilities/cleanup_expired_tokens.py >> /var/log/maintenance/token_cleanup.log 2>&1

# Database maintenance monthly
0 0 1 * * cd /path/to/scripts && python utilities/database_maintenance.py --all >> /var/log/maintenance/db_maintenance.log 2>&1
```

## Logging

All scripts use a common logging framework with configurable log levels. Logs can be directed to:

- Console (standard output)
- Log files
- Centralized logging systems (e.g., ELK Stack)

Log level can be controlled with the `--verbose` flag or by setting the `LOG_LEVEL` environment variable. Sensitive information is automatically masked in logs to prevent credential exposure.

## Error Handling

The scripts implement robust error handling with appropriate exit codes:

- 0: Successful execution
- 1: General error
- 2: Configuration error
- 3: Connection error (database, Conjur, etc.)
- 4: Authentication error
- 5: Permission error

Detailed error information is provided in the logs, and critical errors can trigger notifications via configured channels (email, Slack, etc.).

## Security Considerations

When using these scripts, follow these security best practices:

1. Never store actual credentials in script files or logs
2. Use environment-specific service accounts with least privilege
3. Secure access to configuration files containing sensitive information
4. Use the `--dry-run` option to validate changes before applying
5. Implement proper approval workflows for production operations
6. Regularly rotate service account credentials
7. Monitor and audit all script activities

## Troubleshooting

Common issues and their solutions:

1. **Connection errors**: Verify network connectivity and credentials
2. **Permission issues**: Ensure service accounts have appropriate permissions
3. **Configuration errors**: Check configuration files and environment variables
4. **Performance issues**: Adjust batch sizes and scheduling for resource-intensive operations

Enable verbose logging with the `--verbose` flag for detailed information about script execution. Each module's README contains specific troubleshooting guidance.

## Contributing

When contributing to the scripts:

1. Follow the project's coding standards
2. Add appropriate error handling and logging
3. Include unit tests for new functionality
4. Update documentation with usage instructions and examples
5. Test changes thoroughly before submitting pull requests

All contributions should be submitted as pull requests and will be reviewed by the project maintainers.

## License

This project is licensed under the terms specified in the LICENSE file at the root of the repository.