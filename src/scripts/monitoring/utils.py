#!/usr/bin/env python3
"""
Utility module providing common functions for monitoring the Payment API Security Enhancement system.
Includes functions for service health checks, metrics collection, alert generation, notification handling,
and SLA compliance calculation.
"""

import requests
import redis
import logging
import json
import datetime
import time
import smtplib
import uuid
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

from . import config
from .config import (
    PAYMENT_EAPI_URL, PAYMENT_SAPI_URL, CONJUR_VAULT_URL,
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL,
    CONNECTION_TIMEOUT, READ_TIMEOUT,
    HEALTH_CHECK_ENDPOINTS, METRICS_ENDPOINTS, ALERT_THRESHOLDS,
    NOTIFICATION_CHANNELS, SLA_TARGETS,
    get_alert_threshold, get_notification_channels_for_alert
)

# Configure logger
logger = logging.getLogger(__name__)


class MonitoringError(Exception):
    """Base exception class for monitoring-related errors"""
    
    def __init__(self, message, original_exception=None):
        """Initializes a new MonitoringError instance
        
        Args:
            message (str): Error message
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message)
        self.message = message
        self.original_exception = original_exception


class ServiceHealthError(MonitoringError):
    """Exception raised for service health check errors"""
    
    def __init__(self, message, service_name, original_exception=None):
        """Initializes a new ServiceHealthError instance
        
        Args:
            message (str): Error message
            service_name (str): Name of the service that had a health check error
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message, original_exception)
        self.service_name = service_name


class MetricsCollectionError(MonitoringError):
    """Exception raised for metrics collection errors"""
    
    def __init__(self, message, service_name, original_exception=None):
        """Initializes a new MetricsCollectionError instance
        
        Args:
            message (str): Error message
            service_name (str): Name of the service that had a metrics collection error
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message, original_exception)
        self.service_name = service_name


class NotificationError(MonitoringError):
    """Exception raised for notification delivery errors"""
    
    def __init__(self, message, channel, original_exception=None):
        """Initializes a new NotificationError instance
        
        Args:
            message (str): Error message
            channel (str): Notification channel that had an error
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message, original_exception)
        self.channel = channel


def check_service_health(service_name, base_url, health_endpoint, timeout=None):
    """Checks the health of a service by making a request to its health endpoint
    
    Args:
        service_name (str): Name of the service to check
        base_url (str): Base URL of the service
        health_endpoint (str): Health check endpoint path
        timeout (int, optional): Request timeout in seconds
        
    Returns:
        dict: Health check result containing status, response time, and details
    """
    logger.info(f"Checking health of {service_name} service at {base_url}{health_endpoint}")
    
    # Use default timeout if not specified
    if timeout is None:
        timeout = CONNECTION_TIMEOUT
    
    result = {
        "service_name": service_name,
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "status": "unhealthy",  # Default to unhealthy until proven otherwise
        "response_time_ms": None,
        "details": {}
    }
    
    try:
        url = f"{base_url.rstrip('/')}/{health_endpoint.lstrip('/')}"
        start_time = time.time()
        response = requests.get(url, timeout=timeout)
        response_time = time.time() - start_time
        result["response_time_ms"] = int(response_time * 1000)
        
        if response.status_code == 200:
            result["status"] = "healthy"
            try:
                result["details"] = response.json()
            except ValueError:
                result["details"] = {"message": response.text}
        else:
            result["details"] = {
                "status_code": response.status_code,
                "message": f"Unhealthy response: {response.text}"
            }
            
    except requests.exceptions.Timeout as e:
        logger.warning(f"Health check timeout for {service_name}: {str(e)}")
        result["details"] = {"error": "timeout", "message": str(e)}
        
    except requests.exceptions.ConnectionError as e:
        logger.warning(f"Connection error during health check for {service_name}: {str(e)}")
        result["details"] = {"error": "connection_error", "message": str(e)}
        
    except Exception as e:
        logger.error(f"Unexpected error during health check for {service_name}: {str(e)}", exc_info=True)
        result["details"] = {"error": "unexpected_error", "message": str(e)}
    
    logger.info(f"Health check result for {service_name}: {result['status']} "
                f"(response time: {result.get('response_time_ms', 'N/A')}ms)")
    return result


def check_redis_health(host, port, password, ssl, timeout=None):
    """Checks the health of Redis cache by attempting to connect and perform basic operations
    
    Args:
        host (str): Redis host address
        port (int): Redis port
        password (str): Redis password
        ssl (bool): Whether to use SSL for Redis connection
        timeout (int, optional): Connection timeout in seconds
        
    Returns:
        dict: Health check result containing status, response time, and details
    """
    logger.info(f"Checking health of Redis cache at {host}:{port}")
    
    # Use default timeout if not specified
    if timeout is None:
        timeout = CONNECTION_TIMEOUT
    
    result = {
        "service_name": "redis-cache",
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "status": "unhealthy",  # Default to unhealthy until proven otherwise
        "response_time_ms": None,
        "details": {}
    }
    
    redis_client = None
    try:
        start_time = time.time()
        
        redis_client = redis.Redis(
            host=host,
            port=port,
            password=password,
            ssl=ssl,
            socket_timeout=timeout,
            decode_responses=True
        )
        
        # Simple PING command to verify connection
        response = redis_client.ping()
        response_time = time.time() - start_time
        result["response_time_ms"] = int(response_time * 1000)
        
        if response:
            result["status"] = "healthy"
            result["details"] = {
                "connection": "successful",
                "ping_response": str(response)
            }
            
            # Add basic Redis info if available
            try:
                info = redis_client.info()
                result["details"]["redis_version"] = info.get("redis_version")
                result["details"]["connected_clients"] = info.get("connected_clients")
                result["details"]["used_memory_human"] = info.get("used_memory_human")
            except:
                # Not critical if we can't get info
                pass
        else:
            result["details"] = {"error": "ping_failed", "message": "Redis PING command failed"}
            
    except redis.exceptions.TimeoutError as e:
        logger.warning(f"Redis connection timeout: {str(e)}")
        result["details"] = {"error": "timeout", "message": str(e)}
        
    except redis.exceptions.ConnectionError as e:
        logger.warning(f"Redis connection error: {str(e)}")
        result["details"] = {"error": "connection_error", "message": str(e)}
        
    except redis.exceptions.AuthenticationError as e:
        logger.warning(f"Redis authentication error: {str(e)}")
        result["details"] = {"error": "authentication_error", "message": str(e)}
        
    except Exception as e:
        logger.error(f"Unexpected error checking Redis health: {str(e)}", exc_info=True)
        result["details"] = {"error": "unexpected_error", "message": str(e)}
        
    finally:
        # Ensure Redis connection is closed
        if redis_client:
            try:
                redis_client.close()
            except:
                pass
    
    logger.info(f"Redis health check result: {result['status']} "
                f"(response time: {result.get('response_time_ms', 'N/A')}ms)")
    return result


def collect_service_metrics(service_name, base_url, metrics_endpoint, timeout=None):
    """Collects metrics from a service by making a request to its metrics endpoint
    
    Args:
        service_name (str): Name of the service
        base_url (str): Base URL of the service
        metrics_endpoint (str): Metrics endpoint path
        timeout (int, optional): Request timeout in seconds
        
    Returns:
        dict: Collected metrics from the service
    """
    logger.info(f"Collecting metrics from {service_name} service at {base_url}{metrics_endpoint}")
    
    # Use default timeout if not specified
    if timeout is None:
        timeout = READ_TIMEOUT
    
    try:
        url = f"{base_url.rstrip('/')}/{metrics_endpoint.lstrip('/')}"
        response = requests.get(url, timeout=timeout)
        
        if response.status_code == 200:
            metrics = response.json()
            
            # Add metadata to metrics
            metrics["service_name"] = service_name
            metrics["timestamp"] = format_timestamp_iso(datetime.datetime.now())
            
            # Log successful metrics collection
            log_metrics(metrics, service_name)
            return metrics
        else:
            error_message = f"Failed to collect metrics from {service_name}: HTTP {response.status_code}"
            logger.warning(error_message)
            return {
                "service_name": service_name,
                "timestamp": format_timestamp_iso(datetime.datetime.now()),
                "error": "http_error",
                "status_code": response.status_code,
                "message": error_message
            }
            
    except requests.exceptions.Timeout as e:
        error_message = f"Timeout collecting metrics from {service_name}: {str(e)}"
        logger.warning(error_message)
        return {
            "service_name": service_name,
            "timestamp": format_timestamp_iso(datetime.datetime.now()),
            "error": "timeout",
            "message": error_message
        }
        
    except requests.exceptions.ConnectionError as e:
        error_message = f"Connection error collecting metrics from {service_name}: {str(e)}"
        logger.warning(error_message)
        return {
            "service_name": service_name,
            "timestamp": format_timestamp_iso(datetime.datetime.now()),
            "error": "connection_error",
            "message": error_message
        }
        
    except Exception as e:
        error_message = f"Unexpected error collecting metrics from {service_name}: {str(e)}"
        logger.error(error_message, exc_info=True)
        return {
            "service_name": service_name,
            "timestamp": format_timestamp_iso(datetime.datetime.now()),
            "error": "unexpected_error",
            "message": error_message
        }


def collect_token_metrics(host, port, password, ssl, timeout=None):
    """Collects token-related metrics from Redis cache
    
    Args:
        host (str): Redis host address
        port (int): Redis port
        password (str): Redis password
        ssl (bool): Whether to use SSL for Redis connection
        timeout (int, optional): Connection timeout in seconds
        
    Returns:
        dict: Token metrics from Redis cache
    """
    logger.info(f"Collecting token metrics from Redis cache at {host}:{port}")
    
    # Use default timeout if not specified
    if timeout is None:
        timeout = READ_TIMEOUT
    
    metrics = {
        "service_name": "token-service",
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "token_count": 0,
        "active_tokens": 0,
        "token_generation_rate": 0,
        "token_expiration_rate": 0,
        "tokens_by_client": {}
    }
    
    redis_client = None
    try:
        redis_client = redis.Redis(
            host=host,
            port=port,
            password=password,
            ssl=ssl,
            socket_timeout=timeout,
            decode_responses=True
        )
        
        # Count total tokens
        token_keys = redis_client.keys("token:*")
        metrics["token_count"] = len(token_keys)
        
        # Count active (non-expired) tokens
        now = time.time()
        active_tokens = 0
        tokens_by_client = {}
        
        # Track generation and expiration rates
        tokens_generated_last_minute = 0
        tokens_expired_last_minute = 0
        one_minute_ago = now - 60
        
        for key in token_keys:
            # Get token data
            token_data = redis_client.hgetall(key)
            
            # Parse client ID from key (format: token:{client_id}:{token_id})
            parts = key.split(":")
            if len(parts) >= 2:
                client_id = parts[1]
                
                # Count tokens by client
                if client_id not in tokens_by_client:
                    tokens_by_client[client_id] = 0
                tokens_by_client[client_id] += 1
            
            # Check if token is still active
            if "exp" in token_data:
                expiration = float(token_data["exp"])
                if expiration > now:
                    active_tokens += 1
                
                # Check if token expires in the next minute
                if now < expiration < now + 60:
                    tokens_expired_last_minute += 1
            
            # Check if token was generated in the last minute
            if "iat" in token_data:
                issued_at = float(token_data["iat"])
                if issued_at > one_minute_ago:
                    tokens_generated_last_minute += 1
        
        metrics["active_tokens"] = active_tokens
        metrics["tokens_by_client"] = tokens_by_client
        metrics["token_generation_rate"] = tokens_generated_last_minute
        metrics["token_expiration_rate"] = tokens_expired_last_minute
        
        # Add additional metrics from Redis info
        try:
            info = redis_client.info()
            metrics["redis_memory_used"] = info.get("used_memory_human")
            metrics["redis_connected_clients"] = info.get("connected_clients")
            metrics["redis_evicted_keys"] = info.get("evicted_keys")
        except:
            # Not critical if we can't get info
            pass
            
        # Log successful metrics collection
        log_metrics(metrics, "token-metrics")
        
    except Exception as e:
        error_message = f"Error collecting token metrics: {str(e)}"
        logger.error(error_message, exc_info=True)
        metrics["error"] = str(e)
        
    finally:
        # Ensure Redis connection is closed
        if redis_client:
            try:
                redis_client.close()
            except:
                pass
    
    return metrics


def check_metric_thresholds(metrics, category):
    """Checks if metrics exceed defined alert thresholds
    
    Args:
        metrics (dict): Dictionary of metrics to check
        category (str): Category of metrics (security, performance, availability)
        
    Returns:
        list: List of alerts for metrics exceeding thresholds
    """
    alerts = []
    
    for metric_name, metric_value in metrics.items():
        # Skip metadata fields and non-numeric values
        if metric_name in ["service_name", "timestamp", "error", "message"] or not isinstance(metric_value, (int, float)):
            continue
        
        # Get warning and critical thresholds
        warning_threshold = get_alert_threshold(category, metric_name, "warning")
        critical_threshold = get_alert_threshold(category, metric_name, "critical")
        
        if warning_threshold is None and critical_threshold is None:
            # No thresholds defined for this metric
            continue
        
        # Determine if value exceeds thresholds
        # Note: For some metrics like availability, lower is worse. For others like response time, higher is worse.
        exceeds_threshold = False
        severity = None
        threshold_value = None
        
        # For availability metrics, lower is worse
        if category == "availability":
            if critical_threshold is not None and metric_value < critical_threshold:
                exceeds_threshold = True
                severity = "critical"
                threshold_value = critical_threshold
            elif warning_threshold is not None and metric_value < warning_threshold:
                exceeds_threshold = True
                severity = "warning"
                threshold_value = warning_threshold
        # For other metrics like response time or error counts, higher is worse
        else:
            if critical_threshold is not None and metric_value > critical_threshold:
                exceeds_threshold = True
                severity = "critical"
                threshold_value = critical_threshold
            elif warning_threshold is not None and metric_value > warning_threshold:
                exceeds_threshold = True
                severity = "warning"
                threshold_value = warning_threshold
        
        # Generate alert if threshold is exceeded
        if exceeds_threshold:
            service_name = metrics.get("service_name", "unknown")
            alert = generate_alert(
                category,
                service_name,
                metric_name,
                severity,
                metric_value,
                threshold_value,
                {"timestamp": metrics.get("timestamp")}
            )
            alerts.append(alert)
    
    return alerts


def generate_alert(alert_type, service_name, metric_name, severity, value, threshold, details=None):
    """Generates an alert based on monitoring data
    
    Args:
        alert_type (str): Type of alert (security, performance, availability)
        service_name (str): Name of the affected service
        metric_name (str): Name of the metric that triggered the alert
        severity (str): Alert severity (warning, critical)
        value (any): Current value that triggered the alert
        threshold (any): Threshold that was exceeded
        details (dict, optional): Additional alert details
        
    Returns:
        dict: Alert data structure with all relevant information
    """
    if details is None:
        details = {}
    
    alert_id = str(uuid.uuid4())
    timestamp = format_timestamp_iso(datetime.datetime.now())
    
    alert = {
        "id": alert_id,
        "timestamp": timestamp,
        "type": alert_type,
        "service_name": service_name,
        "metric_name": metric_name,
        "severity": severity,
        "value": value,
        "threshold": threshold,
        "details": details
    }
    
    logger.info(f"Generated {severity} alert for {service_name}: {metric_name} = {value} "
                f"(threshold: {threshold})")
    
    return alert


def send_alert_notification(alert):
    """Sends alert notifications through configured channels
    
    Args:
        alert (dict): Alert data to send
        
    Returns:
        bool: True if notification was sent successfully, False otherwise
    """
    logger.info(f"Sending notifications for alert ID {alert.get('id')} "
                f"({alert.get('severity')} {alert.get('type')} alert for {alert.get('service_name')})")
    
    # Get appropriate notification channels based on alert type and severity
    notification_channels = get_notification_channels_for_alert(
        alert.get("type", "unknown"),
        alert.get("severity", "warning")
    )
    
    if not notification_channels:
        logger.warning(f"No notification channels configured for {alert.get('severity')} "
                      f"{alert.get('type')} alerts")
        return False
    
    notification_sent = False
    
    # Send alert through each configured channel
    for channel_name, channel_config in notification_channels.items():
        try:
            if channel_name == "pagerduty":
                success = send_pagerduty_alert(alert, channel_config)
            elif channel_name == "email":
                success = send_email_alert(alert, channel_config)
            elif channel_name == "slack":
                success = send_slack_alert(alert, channel_config)
            else:
                logger.warning(f"Unknown notification channel: {channel_name}")
                success = False
            
            if success:
                logger.info(f"Successfully sent alert notification via {channel_name}")
                notification_sent = True
            else:
                logger.warning(f"Failed to send alert notification via {channel_name}")
                
        except Exception as e:
            logger.error(f"Error sending {channel_name} notification: {str(e)}", exc_info=True)
    
    return notification_sent


def send_pagerduty_alert(alert, pagerduty_config):
    """Sends an alert to PagerDuty
    
    Args:
        alert (dict): Alert data to send
        pagerduty_config (dict): PagerDuty configuration
        
    Returns:
        bool: True if alert was sent successfully, False otherwise
    """
    logger.info(f"Sending PagerDuty alert for {alert.get('service_name')}")
    
    service_key = pagerduty_config.get("service_key")
    if not service_key:
        logger.error("Missing PagerDuty service key")
        return False
    
    # Map our severity to PagerDuty severity
    severity = pagerduty_config.get("severity", alert.get("severity", "warning"))
    
    # Format the alert for PagerDuty
    incident_key = f"{alert.get('type')}-{alert.get('service_name')}-{alert.get('metric_name')}"
    message = format_alert_message(alert, "pagerduty")
    
    payload = {
        "service_key": service_key,
        "event_type": "trigger",
        "incident_key": incident_key,
        "description": f"{alert.get('severity').upper()}: {alert.get('service_name')} - {alert.get('metric_name')}",
        "details": alert,
        "client": "Payment API Monitoring",
        "client_url": "",
        "contexts": [
            {
                "type": "link",
                "href": "",
                "text": "Monitoring Dashboard"
            }
        ]
    }
    
    try:
        response = requests.post(
            "https://events.pagerduty.com/generic/2010-04-15/create_event.json",
            json=payload,
            timeout=10
        )
        
        if response.status_code == 200:
            logger.info(f"PagerDuty alert sent successfully: {incident_key}")
            return True
        else:
            logger.warning(f"Failed to send PagerDuty alert: HTTP {response.status_code} - {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"Error sending PagerDuty alert: {str(e)}", exc_info=True)
        raise NotificationError(f"Error sending PagerDuty alert: {str(e)}", "pagerduty", e)


def send_email_alert(alert, email_config):
    """Sends an alert via email
    
    Args:
        alert (dict): Alert data to send
        email_config (dict): Email configuration
        
    Returns:
        bool: True if email was sent successfully, False otherwise
    """
    logger.info(f"Sending email alert for {alert.get('service_name')}")
    
    # Get email configuration
    smtp_server = email_config.get("smtp_server")
    smtp_port = email_config.get("smtp_port", 587)
    smtp_user = email_config.get("smtp_user")
    smtp_password = email_config.get("smtp_password")
    from_address = email_config.get("from_address")
    recipients = email_config.get("recipients", [])
    
    if not smtp_server or not from_address or not recipients:
        logger.error("Missing required email configuration")
        return False
    
    # Format the alert for email
    subject = f"{alert.get('severity').upper()} Alert: {alert.get('service_name')} - {alert.get('metric_name')}"
    body_html = format_alert_message(alert, "email")
    body_text = format_alert_message(alert, "text")
    
    # Create message
    msg = MIMEMultipart("alternative")
    msg["Subject"] = subject
    msg["From"] = from_address
    msg["To"] = ", ".join(recipients)
    
    # Attach parts
    part1 = MIMEText(body_text, "plain")
    part2 = MIMEText(body_html, "html")
    msg.attach(part1)
    msg.attach(part2)
    
    try:
        # Connect to SMTP server
        server = smtplib.SMTP(smtp_server, smtp_port)
        server.ehlo()
        
        # Use TLS if available
        if server.has_extn("STARTTLS"):
            server.starttls()
            server.ehlo()
        
        # Login if credentials provided
        if smtp_user and smtp_password:
            server.login(smtp_user, smtp_password)
        
        # Send email
        server.sendmail(from_address, recipients, msg.as_string())
        server.quit()
        
        logger.info(f"Email alert sent successfully to {len(recipients)} recipients")
        return True
        
    except Exception as e:
        logger.error(f"Error sending email alert: {str(e)}", exc_info=True)
        raise NotificationError(f"Error sending email alert: {str(e)}", "email", e)


def send_slack_alert(alert, slack_config):
    """Sends an alert to Slack
    
    Args:
        alert (dict): Alert data to send
        slack_config (dict): Slack configuration
        
    Returns:
        bool: True if alert was sent successfully, False otherwise
    """
    logger.info(f"Sending Slack alert for {alert.get('service_name')}")
    
    webhook_url = slack_config.get("webhook_url")
    channels = slack_config.get("channels", [])
    
    if not webhook_url:
        logger.error("Missing Slack webhook URL")
        return False
    
    # Format the alert for Slack
    message = format_alert_message(alert, "slack")
    
    # Determine color based on severity
    color = "#ff0000" if alert.get("severity") == "critical" else "#ffa500"  # Red for critical, orange for warning
    
    # Create Slack message payload
    payload = {
        "attachments": [
            {
                "fallback": f"{alert.get('severity').upper()} Alert: {alert.get('service_name')} - {alert.get('metric_name')}",
                "color": color,
                "pretext": f"*{alert.get('severity').upper()} Alert*",
                "title": f"{alert.get('service_name')} - {alert.get('metric_name')}",
                "text": message,
                "fields": [
                    {
                        "title": "Service",
                        "value": alert.get("service_name"),
                        "short": True
                    },
                    {
                        "title": "Metric",
                        "value": alert.get("metric_name"),
                        "short": True
                    },
                    {
                        "title": "Value",
                        "value": str(alert.get("value")),
                        "short": True
                    },
                    {
                        "title": "Threshold",
                        "value": str(alert.get("threshold")),
                        "short": True
                    }
                ],
                "footer": "Payment API Monitoring",
                "ts": int(time.time())
            }
        ]
    }
    
    # Include channel override if specified
    for channel in channels:
        payload["channel"] = channel
        
        try:
            response = requests.post(
                webhook_url,
                json=payload,
                timeout=10
            )
            
            if response.status_code == 200 and response.text == "ok":
                logger.info(f"Slack alert sent successfully to channel {channel}")
            else:
                logger.warning(f"Failed to send Slack alert to channel {channel}: "
                              f"HTTP {response.status_code} - {response.text}")
                
        except Exception as e:
            logger.error(f"Error sending Slack alert to channel {channel}: {str(e)}", exc_info=True)
            # Continue trying other channels even if one fails
    
    return True


def format_alert_message(alert, format_type="text"):
    """Formats an alert into a human-readable message
    
    Args:
        alert (dict): Alert data to format
        format_type (str): Format type (email, slack, pagerduty, text)
        
    Returns:
        str: Formatted alert message
    """
    severity = alert.get("severity", "unknown").upper()
    service_name = alert.get("service_name", "unknown")
    metric_name = alert.get("metric_name", "unknown")
    value = alert.get("value", "unknown")
    threshold = alert.get("threshold", "unknown")
    timestamp = alert.get("timestamp", format_timestamp_iso(datetime.datetime.now()))
    
    # Parse timestamp if it's a string
    if isinstance(timestamp, str):
        try:
            dt = parse_iso_timestamp(timestamp)
            timestamp_str = dt.strftime("%Y-%m-%d %H:%M:%S %Z")
        except:
            timestamp_str = timestamp
    else:
        timestamp_str = timestamp.strftime("%Y-%m-%d %H:%M:%S %Z")
    
    if format_type == "email":
        # HTML format for email
        message = f"""
        <h2>{severity} Alert: {service_name}</h2>
        <p><strong>Metric:</strong> {metric_name}</p>
        <p><strong>Value:</strong> {value}</p>
        <p><strong>Threshold:</strong> {threshold}</p>
        <p><strong>Timestamp:</strong> {timestamp_str}</p>
        <hr>
        <h3>Alert Details:</h3>
        <pre>{json.dumps(alert.get("details", {}), indent=2)}</pre>
        """
        return message
        
    elif format_type == "slack":
        # Markdown format for Slack
        message = f"""
*{severity} Alert: {service_name}*
*Metric:* {metric_name}
*Value:* {value}
*Threshold:* {threshold}
*Timestamp:* {timestamp_str}

*Details:*
```
{json.dumps(alert.get("details", {}), indent=2)}
```
        """
        return message
        
    elif format_type == "pagerduty":
        # Plain format for PagerDuty
        message = f"{severity} Alert: {service_name} - {metric_name}\n"
        message += f"Value: {value} (Threshold: {threshold})\n"
        message += f"Timestamp: {timestamp_str}\n\n"
        message += f"Details: {json.dumps(alert.get('details', {}))}"
        return message
        
    else:
        # Plain text format (default)
        message = f"{severity} Alert: {service_name} - {metric_name}\n"
        message += f"Value: {value} (Threshold: {threshold})\n"
        message += f"Timestamp: {timestamp_str}\n\n"
        message += "Alert Details:\n"
        for key, value in alert.get("details", {}).items():
            message += f"  {key}: {value}\n"
        return message


def log_metrics(metrics, metrics_type):
    """Logs metrics data for monitoring and debugging
    
    Args:
        metrics (dict): Metrics data to log
        metrics_type (str): Type of metrics being logged
    """
    # Create a sanitized copy of metrics for logging (remove sensitive data)
    log_safe_metrics = metrics.copy()
    
    # Remove any potentially sensitive information
    for key in ["credentials", "tokens", "secrets"]:
        if key in log_safe_metrics:
            log_safe_metrics[key] = "[REDACTED]"
    
    # Log summary at INFO level
    service_name = metrics.get("service_name", "unknown")
    timestamp = metrics.get("timestamp", "unknown")
    logger.info(f"Collected {metrics_type} metrics for {service_name} at {timestamp}")
    
    # Log details at DEBUG level
    logger.debug(f"Metrics details: {json.dumps(log_safe_metrics)}")
    
    # Check for metrics that exceed warning thresholds
    warning_metrics = []
    critical_metrics = []
    
    for metric_name, metric_value in metrics.items():
        # Skip metadata fields and non-numeric values
        if metric_name in ["service_name", "timestamp", "error", "message"] or not isinstance(metric_value, (int, float)):
            continue
        
        # Determine metric category based on naming convention
        if "authentication" in metric_name or "token" in metric_name or "credential" in metric_name or "unauthorized" in metric_name:
            category = "security"
        elif "response_time" in metric_name or "latency" in metric_name or "throughput" in metric_name:
            category = "performance"
        elif "availability" in metric_name or "uptime" in metric_name or "health" in metric_name:
            category = "availability"
        else:
            # Default to performance for unknown metrics
            category = "performance"
        
        # Get warning and critical thresholds
        warning_threshold = get_alert_threshold(category, metric_name, "warning")
        critical_threshold = get_alert_threshold(category, metric_name, "critical")
        
        if warning_threshold is not None and critical_threshold is not None:
            # For availability metrics, lower is worse
            if category == "availability":
                if metric_value < critical_threshold:
                    critical_metrics.append(f"{metric_name}={metric_value} (threshold={critical_threshold})")
                elif metric_value < warning_threshold:
                    warning_metrics.append(f"{metric_name}={metric_value} (threshold={warning_threshold})")
            # For other metrics, higher is worse
            else:
                if metric_value > critical_threshold:
                    critical_metrics.append(f"{metric_name}={metric_value} (threshold={critical_threshold})")
                elif metric_value > warning_threshold:
                    warning_metrics.append(f"{metric_name}={metric_value} (threshold={warning_threshold})")
    
    # Log warnings or errors if thresholds are exceeded
    if critical_metrics:
        logger.error(f"Critical metrics exceeded thresholds for {service_name}: {', '.join(critical_metrics)}")
    
    if warning_metrics:
        logger.warning(f"Warning metrics exceeded thresholds for {service_name}: {', '.join(warning_metrics)}")


def calculate_sla_compliance(metric_name, actual_value, target_value=None):
    """Calculates SLA compliance percentage for a metric
    
    Args:
        metric_name (str): Name of the metric
        actual_value (float): Actual measured value
        target_value (float, optional): Target SLA value (if not provided, will use SLA_TARGETS)
        
    Returns:
        dict: SLA compliance data including percentage and status
    """
    # Get target value from config if not provided
    if target_value is None:
        target_value = SLA_TARGETS.get(metric_name)
        if target_value is None:
            logger.warning(f"No SLA target defined for metric: {metric_name}")
            return {
                "metric": metric_name,
                "actual": actual_value,
                "target": None,
                "compliance": None,
                "status": "unknown"
            }
    
    # Calculate compliance percentage based on metric type
    compliance_percentage = 0
    
    # For response time metrics, lower is better
    if "response_time" in metric_name or "latency" in metric_name:
        if actual_value <= 0:
            compliance_percentage = 100  # Avoid division by zero
        else:
            compliance_percentage = min(100, (target_value / actual_value) * 100)
    
    # For success rate or availability metrics, higher is better
    elif "success" in metric_name or "availability" in metric_name or "uptime" in metric_name:
        compliance_percentage = min(100, (actual_value / target_value) * 100)
    
    # For other metrics, assume higher is better unless otherwise specified
    else:
        compliance_percentage = min(100, (actual_value / target_value) * 100)
    
    # Determine compliance status
    if compliance_percentage >= 100:
        status = "compliant"
    elif compliance_percentage >= 95:
        status = "at-risk"
    else:
        status = "non-compliant"
    
    return {
        "metric": metric_name,
        "actual": actual_value,
        "target": target_value,
        "compliance": round(compliance_percentage, 2),
        "status": status
    }


def format_timestamp_iso(timestamp=None):
    """Formats a timestamp in ISO 8601 format
    
    Args:
        timestamp (datetime.datetime, optional): Timestamp to format (if None, uses current time)
        
    Returns:
        str: ISO 8601 formatted timestamp
    """
    if timestamp is None:
        timestamp = datetime.datetime.now()
    
    # Format with timezone information
    return timestamp.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def parse_iso_timestamp(timestamp_string):
    """Parses an ISO 8601 formatted timestamp string
    
    Args:
        timestamp_string (str): ISO 8601 formatted timestamp
        
    Returns:
        datetime.datetime: Parsed datetime object
    """
    # Handle various ISO 8601 formats
    formats = [
        "%Y-%m-%dT%H:%M:%S.%fZ",
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%dT%H:%M:%S.%f%z",
        "%Y-%m-%dT%H:%M:%S%z"
    ]
    
    for fmt in formats:
        try:
            return datetime.datetime.strptime(timestamp_string, fmt)
        except ValueError:
            continue
    
    # If all formats fail, raise an exception
    raise ValueError(f"Invalid timestamp format: {timestamp_string}")