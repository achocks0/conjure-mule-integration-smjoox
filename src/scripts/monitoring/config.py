import os
import logging

# Configure logger
logger = logging.getLogger(__name__)

# Service URLs
PAYMENT_EAPI_URL = os.getenv('PAYMENT_EAPI_URL', 'https://payment-eapi.example.com')
PAYMENT_SAPI_URL = os.getenv('PAYMENT_SAPI_URL', 'https://payment-sapi.example.com')
CONJUR_VAULT_URL = os.getenv('CONJUR_VAULT_URL', 'https://conjur.example.com')

# Redis configuration
REDIS_HOST = os.getenv('REDIS_HOST', 'redis.example.com')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))
REDIS_PASSWORD = os.getenv('REDIS_PASSWORD', '')
REDIS_SSL = os.getenv('REDIS_SSL', 'true').lower() == 'true'

# Timeout settings
CONNECTION_TIMEOUT = int(os.getenv('CONNECTION_TIMEOUT', '5'))
READ_TIMEOUT = int(os.getenv('READ_TIMEOUT', '10'))

# Monitoring intervals
HEALTH_CHECK_INTERVAL = int(os.getenv('HEALTH_CHECK_INTERVAL', '60'))
METRICS_COLLECTION_INTERVAL = int(os.getenv('METRICS_COLLECTION_INTERVAL', '300'))

# Alert endpoint
ALERT_ENDPOINT = os.getenv('ALERT_ENDPOINT', 'https://monitoring.example.com/alerts')

# Health check and metrics endpoints
HEALTH_CHECK_ENDPOINTS = {
    'payment-eapi': '/api/health',
    'payment-sapi': '/internal/health',
    'conjur-vault': '/health'
}

METRICS_ENDPOINTS = {
    'payment-eapi': '/api/metrics',
    'payment-sapi': '/internal/metrics'
}

# Alert thresholds for different metrics
ALERT_THRESHOLDS = {
    'security': {
        'authentication_failures': {'warning': 5, 'critical': 10},
        'token_validation_failures': {'warning': 5, 'critical': 10},
        'credential_access_anomalies': {'warning': 50, 'critical': 100},
        'unauthorized_access_attempts': {'warning': 5, 'critical': 10}
    },
    'performance': {
        'api_response_time': {'warning': 300, 'critical': 500},
        'authentication_time': {'warning': 200, 'critical': 400},
        'token_generation_time': {'warning': 100, 'critical': 200},
        'conjur_vault_response_time': {'warning': 150, 'critical': 300}
    },
    'availability': {
        'api_availability': {'warning': 99.5, 'critical': 99.0},
        'conjur_vault_availability': {'warning': 99.9, 'critical': 99.5},
        'redis_cache_availability': {'warning': 99.9, 'critical': 99.5},
        'database_availability': {'warning': 99.9, 'critical': 99.5}
    }
}

# Notification channels configuration
NOTIFICATION_CHANNELS = {
    'pagerduty': {
        'enabled': os.getenv('PAGERDUTY_ENABLED', 'true').lower() == 'true',
        'service_key': os.getenv('PAGERDUTY_SERVICE_KEY', ''),
        'severity_mapping': {
            'critical': 'critical',
            'warning': 'warning'
        }
    },
    'email': {
        'enabled': os.getenv('EMAIL_NOTIFICATIONS_ENABLED', 'true').lower() == 'true',
        'smtp_server': os.getenv('SMTP_SERVER', 'smtp.example.com'),
        'smtp_port': int(os.getenv('SMTP_PORT', '587')),
        'smtp_user': os.getenv('SMTP_USER', ''),
        'smtp_password': os.getenv('SMTP_PASSWORD', ''),
        'from_address': os.getenv('EMAIL_FROM', 'monitoring@example.com'),
        'recipients': {
            'security': os.getenv('SECURITY_EMAIL_RECIPIENTS', 'security@example.com').split(','),
            'operations': os.getenv('OPERATIONS_EMAIL_RECIPIENTS', 'operations@example.com').split(',')
        },
        'severity_mapping': {
            'critical': 'security,operations',
            'warning': 'operations'
        }
    },
    'slack': {
        'enabled': os.getenv('SLACK_NOTIFICATIONS_ENABLED', 'true').lower() == 'true',
        'webhook_url': os.getenv('SLACK_WEBHOOK_URL', ''),
        'channels': {
            'security': os.getenv('SLACK_SECURITY_CHANNEL', '#security-alerts'),
            'operations': os.getenv('SLACK_OPERATIONS_CHANNEL', '#operations-alerts')
        },
        'severity_mapping': {
            'critical': 'security,operations',
            'warning': 'operations'
        }
    }
}

# SLA targets
SLA_TARGETS = {
    'api_response_time': 500,  # milliseconds
    'authentication_success_rate': 99.97,  # percentage
    'token_validation_time': 50,  # milliseconds
    'availability': 99.9  # percentage
}

def get_environment():
    """
    Returns the current environment (development, staging, production)
    
    Returns:
        str: Current environment name
    """
    return os.getenv('ENVIRONMENT', 'development')

def load_environment_config():
    """
    Loads environment-specific configuration overrides
    
    Returns:
        dict: Environment-specific configuration values
    """
    environment = get_environment()
    env_config = {}
    
    if environment == 'development':
        env_config = {
            'HEALTH_CHECK_INTERVAL': 300,  # Check every 5 minutes in development
            'METRICS_COLLECTION_INTERVAL': 600,  # Collect metrics every 10 minutes
            'ALERT_THRESHOLDS': {
                'performance': {
                    'api_response_time': {'warning': 500, 'critical': 1000}  # Relaxed thresholds for dev
                }
            }
        }
    elif environment == 'staging':
        env_config = {
            'HEALTH_CHECK_INTERVAL': 120,  # Check every 2 minutes in staging
            'METRICS_COLLECTION_INTERVAL': 300,  # Collect metrics every 5 minutes
        }
    elif environment == 'production':
        env_config = {
            'HEALTH_CHECK_INTERVAL': 60,  # Check every minute in production
            'METRICS_COLLECTION_INTERVAL': 300,  # Collect metrics every 5 minutes
        }
    
    logger.info(f"Loaded configuration for environment: {environment}")
    return env_config

def get_alert_threshold(category, metric_name, severity):
    """
    Gets the threshold value for a specific metric and severity
    
    Args:
        category (str): Category of the metric (security, performance, availability)
        metric_name (str): Name of the metric
        severity (str): Severity level (warning, critical)
    
    Returns:
        float: Threshold value for the specified metric and severity
    """
    if category not in ALERT_THRESHOLDS:
        logger.warning(f"Category {category} not found in alert thresholds")
        return None
    
    if metric_name not in ALERT_THRESHOLDS[category]:
        logger.warning(f"Metric {metric_name} not found in category {category}")
        return None
    
    if severity not in ALERT_THRESHOLDS[category][metric_name]:
        logger.warning(f"Severity {severity} not found for metric {metric_name}")
        return None
    
    return ALERT_THRESHOLDS[category][metric_name][severity]

def get_notification_channels_for_alert(alert_type, severity):
    """
    Gets the appropriate notification channels for an alert based on its type and severity
    
    Args:
        alert_type (str): Type of alert (security, performance, availability)
        severity (str): Severity level (warning, critical)
    
    Returns:
        dict: Dictionary of notification channels to use
    """
    channels = {}
    
    for channel_name, channel_config in NOTIFICATION_CHANNELS.items():
        if not channel_config.get('enabled', False):
            continue
        
        severity_mapping = channel_config.get('severity_mapping', {})
        if severity not in severity_mapping:
            continue
        
        if channel_name == 'email':
            recipient_groups = severity_mapping[severity].split(',')
            recipients = []
            for group in recipient_groups:
                group_recipients = channel_config.get('recipients', {}).get(group, [])
                recipients.extend(group_recipients)
            
            if recipients:
                channels[channel_name] = {
                    'type': 'email',
                    'recipients': recipients,
                    'smtp_server': channel_config.get('smtp_server'),
                    'smtp_port': channel_config.get('smtp_port'),
                    'smtp_user': channel_config.get('smtp_user'),
                    'smtp_password': channel_config.get('smtp_password'),
                    'from_address': channel_config.get('from_address')
                }
        
        elif channel_name == 'slack':
            channel_groups = severity_mapping[severity].split(',')
            slack_channels = []
            for group in channel_groups:
                channel = channel_config.get('channels', {}).get(group)
                if channel:
                    slack_channels.append(channel)
            
            if slack_channels:
                channels[channel_name] = {
                    'type': 'slack',
                    'webhook_url': channel_config.get('webhook_url'),
                    'channels': slack_channels
                }
        
        elif channel_name == 'pagerduty':
            channels[channel_name] = {
                'type': 'pagerduty',
                'service_key': channel_config.get('service_key'),
                'severity': severity_mapping.get(severity, severity)
            }
    
    return channels