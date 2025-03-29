# Deployment Scripts

## Introduction
This directory contains deployment scripts for the Payment API Security Enhancement project. These scripts automate the setup, configuration, backup, and synchronization of different deployment environments (development, test, staging, and production). The scripts are designed to ensure consistent, secure, and reliable deployment processes while maintaining backward compatibility with existing vendor integrations.

## Prerequisites
Before using these scripts, ensure you have the following prerequisites installed:

- Python 3.9 or higher
- Terraform CLI (for infrastructure provisioning)
- kubectl CLI (for Kubernetes deployments)
- Access to Conjur vault
- PostgreSQL client libraries
- Required Python packages (see requirements.txt in the parent directory)

## Configuration
The deployment scripts use a configuration system defined in `config.py`. Configuration can be provided through:

1. Environment variables (prefixed with `DEPLOYMENT_`)
2. Configuration files (YAML or JSON)
3. Command-line arguments

Key configuration parameters include:

- Environment names (development, test, staging, production)
- Kubernetes namespaces for each environment
- Terraform directories for each environment
- Service URLs for each environment
- Notification channels (Slack, email)
- Backup directory and retention policy

## Available Scripts

### setup_environments.py
Sets up and initializes deployment environments including infrastructure provisioning, Kubernetes namespace setup, and initial configuration of services.

```bash
python setup_environments.py --environment development --config-file config.yml --manifest-dir ../kubernetes
```

Options:
- `--environment`: Target environment (development, test, staging, production)
- `--config-file`: Path to configuration file
- `--manifest-dir`: Directory containing Kubernetes manifests
- `--conjur-config-file`: Path to Conjur configuration file
- `--setup-infrastructure`: Flag to set up infrastructure using Terraform
- `--setup-kubernetes`: Flag to set up Kubernetes resources
- `--setup-conjur`: Flag to set up Conjur vault
- `--verify`: Flag to verify the environment setup

### backup_metadata.py
Creates backups of credential and token metadata from the database for disaster recovery and environment synchronization.

```bash
python backup_metadata.py --environment production --output-dir /path/to/backups --tables credentials,tokens --format json --compress
```

Options:
- `--environment`: Source environment to backup
- `--output-dir`: Directory to store backups
- `--tables`: Comma-separated list of tables to backup
- `--format`: Backup format (json, sql, csv)
- `--compress`: Flag to create compressed archive
- `--notify`: Flag to send notifications

### restore_metadata.py
Restores credential and token metadata from backups for disaster recovery and environment synchronization.

```bash
python restore_metadata.py --backup-path /path/to/backup.tar.gz --target-environment staging --tables credentials,tokens --dry-run
```

Options:
- `--backup-path`: Path to backup file or directory
- `--target-environment`: Environment to restore to
- `--tables`: Comma-separated list of tables to restore
- `--dry-run`: Flag to simulate restore without making changes
- `--force`: Flag to override confirmation prompts
- `--notify`: Flag to send notifications

### sync_environments.py
Synchronizes configuration, metadata, and credentials between different environments for staged promotion.

```bash
python sync_environments.py --source-environment staging --target-environment production --components config,metadata,credentials --dry-run
```

Options:
- `--source-environment`: Source environment
- `--target-environment`: Target environment
- `--components`: Components to synchronize (config, metadata, credentials)
- `--tables`: Tables to synchronize for metadata
- `--config-file`: Path to configuration file
- `--dry-run`: Flag to simulate sync without making changes
- `--force`: Flag to override confirmation prompts
- `--verify`: Flag to verify sync results
- `--notify`: Flag to send notifications

## Common Utilities
The `utils.py` module provides common utilities used by all deployment scripts:

- Command execution with proper error handling
- Terraform operations (init, apply, destroy, output)
- Kubernetes operations (apply, delete, get)
- Service health checking
- Notification sending (Slack, email)
- Manifest file handling
- Environment validation

## Environment Promotion Workflow
The recommended workflow for promoting changes through environments is:

1. Develop and test changes in the development environment
2. Use `sync_environments.py` to promote configuration to the test environment
3. Run automated tests in the test environment
4. Use `sync_environments.py` to promote to staging environment
5. Perform UAT and performance testing in staging
6. Use `sync_environments.py` to promote to production with appropriate approvals

Each promotion step should include verification to ensure the target environment is functioning correctly.

## Backup and Restore Strategy
Regular backups are essential for disaster recovery and environment synchronization:

1. Scheduled daily backups of all environments using `backup_metadata.py`
2. Retention of backups for 30 days (configurable)
3. Compressed archives stored in a secure location
4. Backup manifest files with metadata for easy identification
5. Restore capability using `restore_metadata.py` with dry-run option for validation

Backups include credential metadata, token information, and rotation history, but not the actual credential values which are stored in Conjur vault.

## Credential Rotation Support
The deployment scripts support credential rotation processes:

1. Backup current credential metadata before rotation
2. Perform rotation using the credential rotation service
3. Verify rotation success
4. Maintain backup for rollback if needed

The scripts ensure zero-downtime credential rotation by supporting dual validation periods where both old and new credentials are valid during transition.

## Security Considerations
When using these deployment scripts, follow these security best practices:

1. Never store actual credentials in script files or logs
2. Use environment-specific service accounts with least privilege
3. Secure access to backup files containing metadata
4. Use the `--dry-run` option to validate changes before applying
5. Implement proper approval workflows for production deployments
6. Regularly rotate service account credentials
7. Monitor and audit all deployment activities

## Troubleshooting
Common issues and their solutions:

1. **Connection errors to Conjur vault**: Verify network connectivity and certificate validity
2. **Database connection failures**: Check database credentials and network access
3. **Terraform errors**: Ensure state files are not corrupted and backend configuration is correct
4. **Kubernetes deployment failures**: Check RBAC permissions and namespace existence
5. **Sync failures**: Verify source environment is healthy before synchronizing

All scripts produce detailed logs that can help diagnose issues. Use the `--dry-run` option to simulate operations without making changes.

## Contributing
When contributing to the deployment scripts:

1. Follow the project's coding standards
2. Add appropriate error handling and logging
3. Include unit tests for new functionality
4. Update this README with documentation for new features
5. Test changes in development environment before submitting