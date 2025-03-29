# Payment API Security Enhancement - Monitoring Scripts

## Overview

This directory contains monitoring scripts for the Payment API Security Enhancement project. These scripts provide comprehensive monitoring capabilities for tracking system health, authentication metrics, token usage, and credential rotation status. They are designed to integrate with enterprise monitoring systems like Prometheus and ELK Stack while also providing standalone monitoring capabilities.

The monitoring scripts implement the observability patterns defined in the technical specifications, including health checks, performance metrics tracking, SLA monitoring, and incident response support through alerting.

## Components

### health_check.py

A comprehensive health check script that monitors the availability and performance of all system components:

- Payment-EAPI service health and response time
- Payment-SAPI service health and response time
- Conjur Vault connectivity and response time
- Redis Cache availability and performance
- Authentication flow end-to-end checks

The script supports both continuous monitoring mode and single-run execution for scheduled tasks.

### token_usage_metrics.py

Collects and analyzes JWT token usage patterns from Redis cache:

- Token generation rate
- Token validation success/failure rates
- Token lifetime statistics
- Token usage by client ID
- Token expiration and renewal patterns

This information helps identify potential issues with token management and track authentication patterns.

### credential_usage_metrics.py

Monitors credential usage and rotation status:

- Client ID and Secret usage patterns
- Credential rotation status tracking
- Credential age monitoring
- Failed authentication attempts
- Anomalous access pattern detection

These metrics are critical for security monitoring and ensuring the proper functioning of the credential rotation mechanism.

### config.py

Central configuration file that contains:

- Service endpoint definitions
- Alert thresholds for security and performance metrics
- Notification channel configurations
- Monitoring intervals and settings

### utils.py

Utility functions to support the monitoring scripts:

- HTTP request helpers with proper error handling
- Alert formatting and delivery mechanisms
- Metric collection and formatting utilities
- Redis and Conjur vault connection helpers

## Installation

### Prerequisites

- Python 3.9 or higher
- Access to all services being monitored (Payment-EAPI, Payment-SAPI, Redis, Conjur)
- Appropriate service account credentials with read-only access

### Dependencies

Install the required Python dependencies:

```bash
pip install -r requirements.txt
```

The requirements.txt file includes:

- requests>=2.27.1
- redis>=4.3.4
- prometheus-client>=0.14.1
- pyyaml>=6.0
- python-dotenv>=0.20.0
- slackclient>=2.9.3
- pdpyras>=4.5.0 # PagerDuty Python REST API Service

### Setup

1. Clone the repository and navigate to the monitoring scripts directory:

```bash
git clone <repository-url>
cd payment-api-security-enhancement/src/scripts/monitoring
```

2. Copy the example configuration file and modify it for your environment:

```bash
cp config.example.py config.py
```

3. Set up the required environment variables or modify the configuration file directly.

## Configuration

### Environment Variables

The scripts support configuration through environment variables:

- `MONITORING_ENV`: Environment name (dev, test, staging, prod)
- `EAPI_ENDPOINT`: URL of the Payment-EAPI service
- `SAPI_ENDPOINT`: URL of the Payment-SAPI service
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port
- `REDIS_PASSWORD`: Redis password
- `CONJUR_URL`: Conjur Vault URL
- `MONITORING_AUTH_TOKEN`: Authentication token for monitoring services
- `SLACK_WEBHOOK_URL`: Webhook URL for Slack notifications
- `PAGERDUTY_SERVICE_KEY`: PagerDuty service key for alerts
- `EMAIL_RECIPIENTS`: Comma-separated list of email recipients for alerts

These can be loaded from a .env file using the provided dotenv configuration.

### Alert Thresholds

Alert thresholds can be configured in the config.py file:

```python
# Example of setting custom alert thresholds
ALERT_THRESHOLDS = {
    'security': {
        'authentication_failures': {'warning': 5, 'critical': 10},
        'token_validation_failures': {'warning': 5, 'critical': 10}
    },
    'performance': {
        'api_response_time': {'warning': 300, 'critical': 500}
    }
}
```

### Notification Channels

Configure the notification channels for different alert severities:

```python
NOTIFICATION_CHANNELS = {
    'critical': ['pagerduty', 'slack', 'email'],
    'warning': ['slack', 'email'],
    'info': ['slack']
}

CHANNEL_CONFIG = {
    'slack': {
        'webhook_url': os.environ.get('SLACK_WEBHOOK_URL', ''),
        'channel': '#payment-api-alerts',
        'username': 'Monitoring Bot'
    },
    'pagerduty': {
        'service_key': os.environ.get('PAGERDUTY_SERVICE_KEY', ''),
        'client': 'Payment API Monitoring'
    },
    'email': {
        'recipients': os.environ.get('EMAIL_RECIPIENTS', '').split(','),
        'sender': 'monitoring@example.com',
        'smtp_server': 'smtp.example.com'
    }
}
```

## Usage

### Health Checks

To run health checks continuously with a 60-second interval:

```bash
python health_check.py --interval 60 --verbose
```

For a single health check run (suitable for cron jobs):

```bash
python health_check.py --single-run
```

Health check results can be output to a file:

```bash
python health_check.py --output health_results.json
```

### Token Metrics Collection

To collect token usage metrics:

```bash
python token_usage_metrics.py --output token_metrics.json --interval 300
```

To analyze token usage patterns and generate a report:

```bash
python token_usage_metrics.py --analyze --lookback 7d
```

### Credential Metrics Collection

To monitor credential usage:

```bash
python credential_usage_metrics.py --output credential_metrics.json
```

To check for credential rotation status:

```bash
python credential_usage_metrics.py --check-rotation
```

### Scheduling

For regular monitoring, set up cron jobs to run the scripts at appropriate intervals:

```bash
*/5 * * * * cd /path/to/scripts && python health_check.py --single-run >> /var/log/monitoring/health_check.log 2>&1
```

## Integration

### Prometheus Integration

The scripts can expose metrics in Prometheus format for integration with enterprise monitoring:

```bash
python health_check.py --prometheus-port 9090
```

This will start an HTTP server on port 9090 that exposes metrics in Prometheus format. Configure your Prometheus server to scrape this endpoint.

Key metrics exposed include:
- `payment_api_health_status`: Health status of each component (0=down, 1=up)
- `payment_api_response_time_ms`: Response time in milliseconds
- `payment_api_authentication_failures_total`: Total authentication failures
- `payment_api_token_generation_rate`: Token generation rate
- `payment_api_token_validation_failures_total`: Token validation failures

### ELK Stack Integration

Logs can be sent to ELK Stack for centralized logging and analysis:

1. Configure Filebeat to collect logs from the monitoring scripts
2. Ship logs to Logstash for processing
3. Store in Elasticsearch and visualize in Kibana

Example Filebeat configuration:

```yaml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /var/log/monitoring/*.log
  fields:
    application: payment-api-monitoring
  fields_under_root: true
  json.keys_under_root: true
  json.message_key: message

output.logstash:
  hosts: ["logstash:5044"]
```

### Alert Manager Integration

The scripts can integrate with Alert Manager for centralized alert management:

1. Configure the scripts to send alerts to Alert Manager API
2. Set up Alert Manager rules for routing and notification

Example Alert Manager configuration in config.py:

```python
ALERT_MANAGER = {
    'url': 'http://alertmanager:9093/api/v1/alerts',
    'enabled': True
}
```

## Dashboards

### Security Dashboard

The Security Dashboard provides visibility into authentication and security metrics:

- Authentication success/failure rates
- Token validation metrics
- Credential rotation status
- Security anomalies

Access the dashboard at: http://grafana:3000/d/payment-api-security

### Service Health Dashboard

The Service Health Dashboard shows the overall health and performance of system components:

- Component availability
- Response times
- Error rates
- Resource utilization

Access the dashboard at: http://grafana:3000/d/payment-api-health

### Authentication Dashboard

The Authentication Dashboard provides detailed insights into the authentication system:

- Token generation and validation rates
- Token lifecycle metrics
- Client authentication patterns
- Authentication latency

Access the dashboard at: http://grafana:3000/d/payment-api-auth

## Troubleshooting

### Common Issues

#### Cannot connect to Redis

- Verify Redis connection settings in config.py
- Ensure Redis server is running and accessible
- Check firewall rules and network connectivity

#### PagerDuty alerts not being received

- Verify the PagerDuty service key is correct
- Check PagerDuty integration status
- Ensure the script has internet connectivity

#### Health checks reporting false positives

- Adjust the timeout settings in config.py
- Check for network issues affecting response times
- Verify service endpoints are correctly configured

### Logging

Logs are written to stdout/stderr by default and can be redirected to files. The log level can be configured:

```bash
python health_check.py --log-level DEBUG
```

Log files use a structured JSON format for easier processing and analysis.

### Debugging

For detailed debugging information, run scripts with increased verbosity:

```bash
python health_check.py --verbose --log-level DEBUG
```

The `--debug` flag enables additional diagnostic information:

```bash
python token_usage_metrics.py --debug
```

## Contributing

We welcome contributions to improve the monitoring scripts! Please follow these guidelines:

1. Fork the repository and create a feature branch
2. Write clear, documented code with appropriate tests
3. Follow the project's coding style and conventions
4. Submit a pull request with a clear description of the changes

Before submitting, please ensure:
- All existing tests pass
- New functionality includes appropriate tests
- Documentation is updated to reflect changes

## License

Copyright © 2023 Payment API Security Enhancement Team

This software is provided under [LICENSE INFORMATION]. See the LICENSE file for details.