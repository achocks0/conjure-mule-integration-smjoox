# Credential Rotation Runbook

## Introduction

This runbook provides detailed procedures for rotating client credentials in the Payment API Security Enhancement system. Credential rotation is a critical security practice that allows for the secure replacement of authentication credentials without disrupting service for vendors. The system supports a transition period where both old and new credentials are valid, ensuring zero-downtime during rotation.

### Purpose

The purpose of credential rotation is to:

- Enhance security by regularly updating authentication credentials
- Mitigate risk in case of credential exposure
- Support security best practices and compliance requirements
- Enable emergency credential replacement when necessary

### Scope

This runbook covers:

- Scheduled (planned) credential rotation
- Emergency credential rotation
- Monitoring rotation progress
- Troubleshooting rotation issues
- Rollback procedures

### Prerequisites

Before performing credential rotation, ensure:

- You have appropriate access permissions to the Credential Rotation API
- You have access to Conjur vault (for manual intervention if needed)
- You understand the client ID for which credentials need to be rotated
- You have reviewed any vendor-specific considerations

## Credential Rotation Overview

Credential rotation follows a state-based process that ensures both old and new credentials are valid during a transition period, preventing service disruption.

### Rotation States

The credential rotation process progresses through the following states:

1. **INITIATED**: Initial state when rotation is requested but not yet started
2. **DUAL_ACTIVE**: Both old and new credentials are active during the transition period
3. **OLD_DEPRECATED**: Old credentials are marked as deprecated but still valid for existing sessions
4. **NEW_ACTIVE**: Only new credentials are active, rotation completed successfully
5. **FAILED**: Rotation process failed and was rolled back or cancelled

### Rotation Flow

The credential rotation process follows this flow:

1. Initiate rotation for a specific client ID
2. Generate new credentials in Conjur vault
3. Configure transition period where both old and new credentials are valid
4. Monitor usage of old credentials during transition period
5. After transition period or when no usage of old credentials is detected, deprecate old credentials
6. Complete rotation by removing old credentials
7. Verify rotation completion

### Transition Period

The transition period is a critical aspect of zero-downtime credential rotation:

- Default transition period is 60 minutes (configurable)
- During this period, both old and new credentials are valid
- This allows clients to gradually transition to new credentials
- The system monitors usage of old credentials during this period
- Transition can be completed early if no usage of old credentials is detected

## Rotation Methods

Credential rotation can be performed using multiple methods depending on your preference and environment.

### Using the REST API

The Credential Rotation API provides RESTful endpoints for managing rotation:

```bash
# Initiate rotation
curl -X POST http://localhost:8080/api/v1/rotations/initiate \
  -H "Content-Type: application/json" \
  -d '{"clientId": "xyz-vendor", "reason": "Scheduled rotation", "transitionPeriodMinutes": 60, "forceRotation": false}'

# Check rotation status
curl -X GET http://localhost:8080/api/v1/rotations/{rotation-id}

# Complete rotation
curl -X PUT http://localhost:8080/api/v1/rotations/{rotation-id}/complete

# Cancel rotation
curl -X DELETE http://localhost:8080/api/v1/rotations/{rotation-id}?reason=Cancellation+reason
```

### Using the Rotation Script

The system provides a shell script for credential rotation operations:

```bash
# Initiate rotation
./rotate-credentials.sh initiate --client-id xyz-vendor --reason "Scheduled rotation" --transition-period 60

# Monitor rotation
./rotate-credentials.sh monitor --rotation-id {rotation-id}

# Complete rotation
./rotate-credentials.sh complete --rotation-id {rotation-id}

# Execute full rotation workflow (initiate, monitor, complete)
./rotate-credentials.sh execute --client-id xyz-vendor --reason "Scheduled rotation" --transition-period 60 --wait-for-completion

# Check rotation status
./rotate-credentials.sh status --rotation-id {rotation-id}
```

### Using the Python Module

For programmatic access, you can use the Python module:

```python
from conjur.rotate_credentials import rotate_credential_with_retry, get_rotation_status
from conjur.config import create_conjur_config, get_rotation_config

# Create configurations
conjur_config = create_conjur_config()
rotation_config = get_rotation_config()
rotation_config.transition_period_seconds = 3600  # 60 minutes

# Initiate rotation
result = rotate_credential_with_retry(
    client_id="xyz-vendor",
    conjur_config=conjur_config,
    rotation_config=rotation_config
)

# Check status
status = get_rotation_status("xyz-vendor", conjur_config)
print(f"Rotation state: {status['state']}")
```

### Automated Rotation

The system includes a scheduler that can automatically rotate credentials based on configured policies:

- Credentials can be configured for automatic rotation based on age
- The `RotationScheduler` component checks for credentials that need rotation
- Automated rotation follows the same process as manual rotation
- Rotation events are logged and notifications are sent to configured recipients

## Rotation Procedures

This section provides detailed step-by-step procedures for common rotation scenarios.

### Scheduled Rotation

Follow these steps for planned, scheduled credential rotation:

1. **Preparation**:
   - Notify relevant stakeholders of planned rotation
   - Schedule rotation during a low-traffic period if possible
   - Verify system health before proceeding

2. **Execution**:
   - Initiate rotation using preferred method (API, script, or Python module)
   - Specify an appropriate transition period (default: 60 minutes)
   - Monitor rotation progress

3. **Verification**:
   - Verify rotation completed successfully
   - Check logs for any warnings or errors
   - Verify vendor systems continue to function properly

4. **Documentation**:
   - Document the rotation in your change management system
   - Record rotation ID, timestamp, and outcome

### Emergency Rotation

Follow these steps for emergency credential rotation (e.g., in response to suspected compromise):

1. **Assessment**:
   - Determine the scope and impact of the credential compromise
   - Identify all affected client IDs
   - Notify security team and relevant stakeholders

2. **Execution**:
   - Initiate rotation with `forceRotation=true` to override any active rotations
   - Consider using a shorter transition period based on urgency
   - Monitor rotation closely

3. **Verification**:
   - Verify rotation completed successfully
   - Check for any unauthorized access attempts using old credentials
   - Verify vendor systems continue to function properly

4. **Documentation and Follow-up**:
   - Document the incident and rotation in your security incident system
   - Conduct post-incident review
   - Implement any recommended security improvements

### Monitoring Rotation Progress

During the rotation process, monitor progress using these methods:

1. **API Status Check**:
   ```bash
   curl -X GET http://localhost:8080/api/v1/rotations/{rotation-id}
   ```

2. **Script Status Check**:
   ```bash
   ./rotate-credentials.sh status --rotation-id {rotation-id}
   ```

3. **Log Monitoring**:
   - Monitor `/var/log/payment/rotation.log` for rotation events
   - Check application logs for authentication patterns

4. **Metrics Dashboard**:
   - Monitor the credential usage dashboard in Grafana
   - Watch for changes in authentication patterns

### Completing Rotation Early

If you need to complete rotation before the transition period ends:

1. Verify that old credentials are no longer in use:
   ```bash
   ./rotate-credentials.sh monitor --rotation-id {rotation-id}
   ```

2. If safe to proceed, complete the rotation:
   ```bash
   ./rotate-credentials.sh complete --rotation-id {rotation-id}
   ```

3. Verify completion:
   ```bash
   ./rotate-credentials.sh status --rotation-id {rotation-id}
   ```

### Cancelling Rotation

If you need to cancel an in-progress rotation:

1. Identify the rotation ID:
   ```bash
   ./rotate-credentials.sh status --client-id xyz-vendor
   ```

2. Cancel the rotation with a reason:
   ```bash
   ./rotate-credentials.sh cancel --rotation-id {rotation-id} --reason "Cancellation reason"
   ```
   or
   ```bash
   curl -X DELETE http://localhost:8080/api/v1/rotations/{rotation-id}?reason=Cancellation+reason
   ```

3. Verify cancellation:
   ```bash
   ./rotate-credentials.sh status --rotation-id {rotation-id}
   ```

Note: Cancellation will revert to the original credentials and clean up any new credentials that were generated.

## Troubleshooting

This section provides guidance for troubleshooting common issues during credential rotation.

### Common Issues and Resolutions

| Issue | Possible Causes | Resolution |
|-------|-----------------|------------|
| Rotation fails to initiate | - Invalid client ID<br>- Insufficient permissions<br>- Conjur vault unavailable | - Verify client ID exists<br>- Check permissions<br>- Verify Conjur connectivity |
| Rotation stuck in DUAL_ACTIVE state | - Clients still using old credentials<br>- Monitoring service failure | - Extend transition period<br>- Manually advance rotation state<br>- Restart monitoring service |
| Rotation fails during completion | - Conjur vault connectivity issues<br>- Permission errors | - Check Conjur connectivity<br>- Verify permissions<br>- Retry completion |
| Authentication failures after rotation | - Cached credentials in client systems<br>- Incomplete rotation | - Clear client credential caches<br>- Verify rotation completed successfully<br>- Consider rolling back if necessary |

### Checking Rotation Logs

Rotation logs contain detailed information about the rotation process:

```bash
# View rotation logs
tail -f /var/log/payment/rotation.log

# Search for specific rotation ID
grep "rotation-id" /var/log/payment/rotation.log

# Check for errors
grep -i "error\|fail\|exception" /var/log/payment/rotation.log
```

### Manual Intervention

In some cases, manual intervention may be required:

1. **Manually Advancing Rotation State**:
   ```bash
   curl -X PUT http://localhost:8080/api/v1/rotations/{rotation-id}/advance \
     -H "Content-Type: application/json" \
     -d '{"targetState": "OLD_DEPRECATED"}'
   ```

2. **Directly Accessing Conjur Vault**:
   In extreme cases, you may need to directly access Conjur vault to manage credentials. This should only be done by authorized personnel following established procedures.

3. **Restarting Services**:
   If services become unresponsive during rotation:
   ```bash
   # Restart rotation service
sudo systemctl restart credential-rotation-service

   # Restart cache service if token caching issues occur
   sudo systemctl restart redis
   ```

### Rollback Procedures

If rotation causes significant issues and needs to be rolled back:

1. **Cancel Active Rotation**:
   ```bash
   ./rotate-credentials.sh cancel --rotation-id {rotation-id} --reason "Rollback due to issues"
   ```

2. **Verify Original Credentials Restored**:
   ```bash
   ./rotate-credentials.sh status --client-id xyz-vendor
   ```

3. **Clear Token Caches**:
   ```bash
   # Clear Redis cache
   redis-cli KEYS "token:xyz-vendor:*" | xargs redis-cli DEL
   ```

4. **Notify Stakeholders**:
   Inform all relevant parties that credentials have been rolled back to the previous version.

### Incident Response for Rotation Failures

When credential rotation failures occur that require incident management:

1. **Assessment and Triage**:
   - Determine the severity of the rotation failure
   - Assess impact on production systems and vendors
   - Log an incident ticket with appropriate severity

2. **Containment**:
   - If rotation is causing authentication failures, follow rollback procedures immediately
   - Isolate affected components if necessary
   - Apply temporary workarounds to maintain service availability

3. **Communication**:
   - Notify relevant stakeholders based on incident severity
   - Provide regular status updates throughout the incident
   - Document all actions taken during the response

4. **Resolution**:
   - Once service is stabilized, investigate root cause
   - Develop and implement a permanent fix
   - Test fix thoroughly before re-attempting rotation

5. **Post-Incident**:
   - Conduct post-incident review
   - Document lessons learned
   - Update rotation procedures and documentation as necessary
   - Implement preventive measures for similar failures

## Best Practices

Follow these best practices for successful credential rotation:

### Rotation Planning

- Schedule rotations during low-traffic periods
- Communicate rotation plans to relevant stakeholders
- Consider vendor-specific requirements or limitations
- Document rotation plans and procedures
- Test rotation procedures in non-production environments first

### Transition Period Selection

- Default transition period (60 minutes) is suitable for most scenarios
- For critical systems, consider longer transition periods (2-24 hours)
- For emergency rotations, shorter periods may be appropriate
- Consider client behavior and refresh patterns when setting transition period
- Monitor credential usage during transition to determine optimal duration

### Monitoring and Verification

- Actively monitor rotation progress
- Verify authentication patterns during and after rotation
- Check logs for any unexpected authentication failures
- Verify vendor systems continue to function properly
- Conduct post-rotation verification to ensure completion

### Security Considerations

- Rotate credentials regularly as part of security best practices
- Immediately rotate credentials if compromise is suspected
- Ensure proper access controls for rotation operations
- Audit all rotation activities
- Securely store rotation records and logs

## Reference

Additional reference information for credential rotation.

### API Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/rotations/initiate` | POST | Initiate credential rotation |
| `/api/v1/rotations/{rotationId}` | GET | Get rotation status |
| `/api/v1/rotations/client/{clientId}` | GET | Get all rotations for a client |
| `/api/v1/rotations/active` | GET | Get all active rotations |
| `/api/v1/rotations/{rotationId}/advance` | PUT | Advance rotation to next state |
| `/api/v1/rotations/{rotationId}/complete` | PUT | Complete rotation process |
| `/api/v1/rotations/{rotationId}` | DELETE | Cancel rotation process |

### Script Reference

```
Usage: ./rotate-credentials.sh COMMAND [OPTIONS]

Commands:
  initiate    Initiate credential rotation
  monitor     Monitor rotation progress
  complete    Complete rotation process
  execute     Execute full rotation workflow
  status      Check rotation status
  cancel      Cancel rotation process

Options:
  --client-id VALUE             Client ID for rotation
  --rotation-id VALUE           Rotation ID for monitoring/completion
  --reason VALUE                Reason for rotation or cancellation
  --transition-period VALUE     Transition period in minutes (default: 60)
  --force                       Force rotation even if one is in progress
  --wait-for-completion         Wait for rotation to complete (for execute command)
  --mode [api|script]           Use API or script mode (default: api)
  --help                        Display this help message
```

### Configuration Reference

Rotation behavior can be configured through application properties:

```yaml
# Rotation Configuration
rotation:
  # Default transition period in minutes
  default-transition-period: 60
  
  # Scheduler interval in milliseconds (5 minutes)
  scheduler.interval: 300000
  
  # Progress check interval in milliseconds (1 minute)
  check.interval: 60000
  
  # Notification settings
  notification:
    enabled: true
    recipients: operations@example.com,security@example.com
```

### Related Documentation

- [High-Level Architecture](../architecture/high-level-architecture.md)
- [Credential Rotation Architecture](../architecture/credential-rotation.md)
- [Monitoring Guide](./monitoring-guide.md)
- [Deployment Guide](./deployment-guide.md)