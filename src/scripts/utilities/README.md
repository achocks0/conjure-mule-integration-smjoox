# Payment API Security Enhancement Utilities

## Introduction

This directory contains utility scripts for the Payment API Security Enhancement project. These utilities help with maintenance, testing, and operational tasks related to the payment processing system's security enhancements.

## Available Utilities

The following utility scripts are available in this directory:

### Configuration Utilities
- `config.py`: Provides configuration management for all utility scripts, including database, Redis, and token cleanup settings.

### Common Utilities
- `utils.py`: Contains shared functionality for database connections, Redis operations, token management, and error handling used by other utility scripts.

### Token Management
- `cleanup_expired_tokens.py`: Removes expired tokens from both the database and Redis cache to maintain system performance and security.
- `validate_tokens.py`: Validates JWT tokens, checking their signatures, expiration, and claims.

### Database Maintenance
- `database_maintenance.py`: Performs database optimization tasks including vacuum, reindex, analyze operations, and data archiving based on retention policies.

### Testing Utilities
- `generate_test_credentials.py`: Creates test client IDs and secrets for testing authentication, token generation, and credential rotation.

## Usage Instructions

### Configuration

All utility scripts use a common configuration approach. You can specify configuration through:

1. Configuration files (JSON or YAML)
2. Environment variables
3. Command-line arguments

Configuration precedence: Command-line arguments > Environment variables > Configuration files > Default values

### Token Cleanup

```bash
# Basic usage
python cleanup_expired_tokens.py

# With custom configuration
python cleanup_expired_tokens.py --config=config.json --batch-size=500 --max-tokens=10000

# Dry run (no actual deletions)
python cleanup_expired_tokens.py --dry-run --verbose
```

### Token Validation

```bash
# Validate a specific token by ID
python validate_tokens.py --token-id=abc123 --check-db --check-cache

# Validate a raw JWT token
python validate_tokens.py --token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... --secret-key=your-secret-key

# List all tokens
python validate_tokens.py --list-all --format=table

# List expired tokens
python validate_tokens.py --list-expired --format=json
```

### Database Maintenance

```bash
# Perform all maintenance operations
python database_maintenance.py --all

# Perform specific operations
python database_maintenance.py --vacuum --reindex

# Specify tables to maintain
python database_maintenance.py --analyze --tables TOKEN_METADATA AUTHENTICATION_EVENT

# Dry run (no actual changes)
python database_maintenance.py --all --dry-run --verbose
```

### Test Credential Generation

```bash
# Generate default test credentials
python generate_test_credentials.py

# Generate specific number of credentials with custom settings
python generate_test_credentials.py --num=10 --secret-length=64 --prefix=test-vendor-

# Store credentials in different locations
python generate_test_credentials.py --store-in-file --output=credentials.json
python generate_test_credentials.py --store-in-db
python generate_test_credentials.py --store-in-conjur
```

## Scheduling Recommendations

For optimal system maintenance, we recommend the following schedule for running these utilities:

### Token Cleanup
- Frequency: Hourly
- Recommended cron: `0 * * * *`
- Command: `python cleanup_expired_tokens.py --batch-size=1000`

### Database Maintenance
- Frequency: Monthly
- Recommended cron: `0 0 1 * *`
- Command: `python database_maintenance.py --all`

### Database Archiving
- Frequency: Monthly
- Recommended cron: `0 1 1 * *`
- Command: `python database_maintenance.py --archive`

## Logging

All utility scripts use a common logging configuration. Logs are written to:

- Console (standard output)
- Log files (when configured)

Log level can be controlled with the `--verbose` flag or by setting the `LOG_LEVEL` environment variable.

## Error Handling

The utilities implement robust error handling with appropriate exit codes:

- 0: Successful execution
- 1: General error
- 2: Configuration error
- 3: Database connection error
- 4: Redis connection error

Detailed error information is provided in the logs.

## Examples

### Example Configuration File

```json
{
  "database": {
    "host": "localhost",
    "port": 5432,
    "dbname": "payment",
    "username": "payment_user",
    "password": "secure_password",
    "connect_timeout": 5,
    "read_timeout": 10
  },
  "redis": {
    "host": "localhost",
    "port": 6379,
    "password": "redis_password",
    "ssl": true,
    "socket_timeout": 5
  },
  "token_cleanup": {
    "batch_size": 1000,
    "max_tokens_per_run": 10000,
    "dry_run": false
  }
}
```

### Example Shell Script for Scheduled Maintenance

```bash
#!/bin/bash
# maintenance.sh - Run all maintenance tasks

# Set environment variables
export CONFIG_FILE=/path/to/config.json
export LOG_LEVEL=INFO

# Run token cleanup
python cleanup_expired_tokens.py --batch-size=1000

# Run database maintenance if it's the first day of the month
if [ $(date +%d) -eq 1 ]; then
  python database_maintenance.py --all
fi

# Exit with the status of the last command
exit $?
```

## Troubleshooting

Common issues and their solutions:

### Database Connection Issues
- Verify database credentials in configuration
- Check network connectivity to database server
- Ensure database service is running
- Check for firewall rules blocking connections

### Redis Connection Issues
- Verify Redis credentials in configuration
- Check network connectivity to Redis server
- Ensure Redis service is running
- Verify SSL settings if enabled

### Permission Issues
- Ensure the user running the scripts has appropriate database permissions
- For Conjur operations, verify that service account has proper access

### Performance Issues
- Adjust batch sizes for token cleanup and database operations
- Schedule maintenance during off-peak hours
- Monitor system resources during utility execution

## Contributing

When adding new utilities or modifying existing ones, please follow these guidelines:

1. Use the common configuration and logging framework in `config.py`
2. Implement proper error handling and return appropriate exit codes
3. Include detailed help text for command-line arguments
4. Add unit tests for new functionality
5. Update this README with usage instructions and examples