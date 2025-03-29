#!/usr/bin/env python3
"""
Script for collecting and monitoring credential usage metrics from the Payment API Security Enhancement system.
Tracks credential access patterns, rotation status, and potential security anomalies related to credential usage.
"""

import redis  # version 4.3.4
import requests  # version 2.28.1
import logging  # standard library
import json  # standard library
import datetime  # standard library
import time  # standard library
import argparse  # standard library
import sys  # standard library

# Internal imports
from . import config
from .config import (
    CONJUR_VAULT_URL, REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL,
    CONNECTION_TIMEOUT, ALERT_THRESHOLDS, get_alert_threshold
)
from .utils import (
    check_service_health, check_redis_health,
    generate_alert, send_alert_notification,
    log_metrics, format_timestamp_iso
)
from ..conjur.utils import (
    create_http_session, build_conjur_url, RetryHandler
)
from ..conjur.retrieve_credentials import get_cached_credential

# Configure logger
logger = logging.getLogger(__name__)

# Constants
METRICS_COLLECTION_INTERVAL = 300  # Default: collect metrics every 5 minutes
DEFAULT_OUTPUT_FORMAT = 'json'


def collect_credential_usage_metrics(redis_host, redis_port, redis_password, redis_ssl, conjur_url, timeout=None):
    """
    Collects credential usage metrics from Redis cache and Conjur vault.

    Args:
        redis_host (str): Redis host address
        redis_port (int): Redis port
        redis_password (str): Redis password
        redis_ssl (bool): Whether to use SSL for Redis connection
        conjur_url (str): Conjur vault URL
        timeout (int, optional): Connection timeout in seconds

    Returns:
        dict: Collected credential usage metrics
    """
    # Initialize metrics dictionary with timestamp
    metrics = {
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "service_name": "credential-management"
    }

    # Check Redis health to ensure connectivity
    redis_health = check_redis_health(redis_host, redis_port, redis_password, redis_ssl, timeout)
    if redis_health["status"] != "healthy":
        logger.warning(f"Redis cache is not healthy, metrics may be incomplete: {redis_health['details']}")
        metrics["redis_health"] = redis_health["status"]
        metrics["redis_error"] = redis_health["details"].get("error")
        return metrics

    # Connect to Redis cache
    try:
        redis_client = redis.Redis(
            host=redis_host,
            port=redis_port,
            password=redis_password,
            ssl=redis_ssl,
            socket_timeout=timeout or CONNECTION_TIMEOUT,
            decode_responses=True
        )

        # Collect credential access metrics from Redis
        access_metrics = collect_credential_access_metrics(redis_client)
        metrics.update(access_metrics)

        # Collect credential rotation metrics from Redis
        rotation_metrics = collect_credential_rotation_metrics(redis_client)
        metrics.update(rotation_metrics)

        # Check Conjur vault health
        conjur_health = check_service_health("conjur-vault", conjur_url, "/health", timeout)
        metrics["conjur_health"] = conjur_health["status"]
        
        # Collect credential metadata from Conjur if available
        if conjur_health["status"] == "healthy":
            # We're not fetching the actual credentials, just metadata about them
            metrics["conjur_available"] = True
        else:
            metrics["conjur_available"] = False
            metrics["conjur_error"] = conjur_health["details"].get("error")

        # Calculate credential usage patterns and statistics
        metrics["total_credentials"] = len(metrics.get("credentials_by_client", {}))
        metrics["active_credentials"] = sum(1 for status in metrics.get("credential_status", {}).values() 
                                           if status == "active")
        metrics["rotating_credentials"] = sum(1 for status in metrics.get("credential_status", {}).values() 
                                             if status == "rotating")
        
        # Close Redis connection
        redis_client.close()

    except Exception as e:
        logger.error(f"Error collecting credential usage metrics: {str(e)}", exc_info=True)
        metrics["error"] = str(e)

    return metrics


def collect_credential_access_metrics(redis_client):
    """
    Collects metrics related to credential access patterns.

    Args:
        redis_client (redis.Redis): Redis client instance

    Returns:
        dict: Credential access metrics
    """
    metrics = {
        "credential_access_count": 0,
        "credentials_by_client": {},
        "access_frequency": {},
        "unusual_access_patterns": []
    }

    try:
        # Get the monitoring period start time (last 5 minutes by default)
        monitoring_period_start = int(time.time()) - METRICS_COLLECTION_INTERVAL
        
        # Count total credential access events in the monitoring period
        # We're looking for keys like "credential:access:{client_id}:{timestamp}"
        access_keys = redis_client.keys("credential:access:*")
        recent_access_count = 0
        
        # Track access by client ID
        access_by_client = {}
        access_by_hour = {}
        
        for key in access_keys:
            parts = key.split(":")
            if len(parts) >= 4:
                client_id = parts[2]
                
                # Get access timestamp if available
                access_data = redis_client.hgetall(key)
                access_time = int(access_data.get("timestamp", 0))
                
                # Only count recent accesses
                if access_time >= monitoring_period_start:
                    recent_access_count += 1
                    
                    # Count by client
                    if client_id not in access_by_client:
                        access_by_client[client_id] = 0
                    access_by_client[client_id] += 1
                    
                    # Track access by hour of day (for pattern detection)
                    if access_time > 0:
                        hour = datetime.datetime.fromtimestamp(access_time).hour
                        if hour not in access_by_hour:
                            access_by_hour[hour] = 0
                        access_by_hour[hour] += 1
        
        # Calculate access frequency (accesses per minute)
        minutes = max(1, METRICS_COLLECTION_INTERVAL / 60)  # Avoid division by zero
        overall_frequency = recent_access_count / minutes
        
        # Calculate frequency by client
        client_frequency = {}
        for client_id, count in access_by_client.items():
            client_frequency[client_id] = count / minutes
        
        # Identify unusual access patterns
        # 1. Unusual time of day (outside business hours)
        business_hours = range(8, 18)  # 8 AM - 6 PM
        after_hours_access = sum(access_by_hour.get(hour, 0) for hour in range(24) if hour not in business_hours)
        
        if after_hours_access > 0:
            metrics["unusual_access_patterns"].append({
                "type": "after_hours_access",
                "count": after_hours_access,
                "details": {
                    "after_hours_distribution": {str(hour): access_by_hour.get(hour, 0) 
                                               for hour in range(24) if hour not in business_hours 
                                               and hour in access_by_hour}
                }
            })
        
        # 2. Unusually high frequency for a client
        # Define a threshold as 2x the overall average frequency
        high_frequency_threshold = overall_frequency * 2
        high_frequency_clients = {}
        
        for client_id, frequency in client_frequency.items():
            if frequency > high_frequency_threshold and frequency > 1:  # At least 1 access per minute
                high_frequency_clients[client_id] = frequency
        
        if high_frequency_clients:
            metrics["unusual_access_patterns"].append({
                "type": "high_frequency_access",
                "count": len(high_frequency_clients),
                "details": {
                    "high_frequency_clients": high_frequency_clients
                }
            })
        
        # Update metrics dictionary
        metrics["credential_access_count"] = recent_access_count
        metrics["credentials_by_client"] = access_by_client
        metrics["access_frequency"] = {
            "overall": overall_frequency,
            "by_client": client_frequency
        }
        
    except Exception as e:
        logger.error(f"Error collecting credential access metrics: {str(e)}", exc_info=True)
        metrics["error"] = str(e)
    
    return metrics


def collect_credential_rotation_metrics(redis_client):
    """
    Collects metrics related to credential rotation status.

    Args:
        redis_client (redis.Redis): Redis client instance

    Returns:
        dict: Credential rotation metrics
    """
    metrics = {
        "credential_rotation": {
            "active": 0,
            "rotating": 0,
            "completed": 0,
            "failed": 0
        },
        "credential_status": {},
        "credential_last_rotation": {},
        "credential_rotation_failures": [],
        "credentials_due_rotation": []
    }

    try:
        # Get credential rotation status from Redis
        # Keys like "credential:rotation:{client_id}"
        rotation_keys = redis_client.keys("credential:rotation:*")
        
        # Current time for age calculations
        current_time = int(time.time())
        
        # Standard rotation interval: 90 days (in seconds)
        rotation_interval = 90 * 24 * 3600
        
        # Threshold for due rotation: 7 days before expiration
        due_threshold = rotation_interval - (7 * 24 * 3600)
        
        for key in rotation_keys:
            parts = key.split(":")
            if len(parts) >= 3:
                client_id = parts[2]
                
                # Get rotation data
                rotation_data = redis_client.hgetall(key)
                
                # Extract status and timestamps
                status = rotation_data.get("status", "unknown")
                last_rotation = int(rotation_data.get("last_rotation", 0))
                next_rotation = int(rotation_data.get("next_rotation", 0))
                
                # Store status by client ID
                metrics["credential_status"][client_id] = status
                
                # Store last rotation time by client ID
                if last_rotation > 0:
                    metrics["credential_last_rotation"][client_id] = last_rotation
                
                # Count by status
                if status in metrics["credential_rotation"]:
                    metrics["credential_rotation"][status] += 1
                
                # Check for failed rotations
                if status == "failed":
                    failure_reason = rotation_data.get("failure_reason", "unknown")
                    failure_time = int(rotation_data.get("failure_time", 0))
                    
                    metrics["credential_rotation_failures"].append({
                        "client_id": client_id,
                        "failure_reason": failure_reason,
                        "failure_time": failure_time,
                        "age_seconds": current_time - failure_time if failure_time > 0 else 0
                    })
                
                # Check credentials approaching rotation deadline
                if status == "active" and last_rotation > 0:
                    age_seconds = current_time - last_rotation
                    
                    # If credential age is approaching rotation interval
                    if age_seconds > due_threshold:
                        metrics["credentials_due_rotation"].append({
                            "client_id": client_id,
                            "age_seconds": age_seconds,
                            "days_until_rotation": max(0, (rotation_interval - age_seconds) // 86400)
                        })
        
        # Calculate average time since last rotation (for active credentials)
        if metrics["credential_last_rotation"]:
            rotation_ages = [current_time - ts for ts in metrics["credential_last_rotation"].values()]
            metrics["average_days_since_rotation"] = sum(rotation_ages) / len(rotation_ages) / 86400
        
    except Exception as e:
        logger.error(f"Error collecting credential rotation metrics: {str(e)}", exc_info=True)
        metrics["error"] = str(e)
    
    return metrics


def check_credential_metrics_thresholds(metrics):
    """
    Checks if credential metrics exceed defined alert thresholds.

    Args:
        metrics (dict): Collected metrics

    Returns:
        list: List of alerts for metrics exceeding thresholds
    """
    alerts = []
    
    # Check access frequency against thresholds
    if "access_frequency" in metrics:
        overall_frequency = metrics["access_frequency"].get("overall", 0)
        
        # Check against security thresholds - unusual access frequency
        warning_threshold = get_alert_threshold("security", "credential_access_anomalies", "warning")
        critical_threshold = get_alert_threshold("security", "credential_access_anomalies", "critical")
        
        if warning_threshold is not None and critical_threshold is not None:
            if overall_frequency > critical_threshold:
                alert = generate_alert(
                    "security",
                    "credential-management",
                    "credential_access_frequency",
                    "critical",
                    overall_frequency,
                    critical_threshold,
                    {"timestamp": metrics.get("timestamp")}
                )
                alerts.append(alert)
            elif overall_frequency > warning_threshold:
                alert = generate_alert(
                    "security",
                    "credential-management",
                    "credential_access_frequency",
                    "warning",
                    overall_frequency,
                    warning_threshold,
                    {"timestamp": metrics.get("timestamp")}
                )
                alerts.append(alert)
    
    # Check unusual access patterns against thresholds
    if metrics.get("unusual_access_patterns"):
        for pattern in metrics["unusual_access_patterns"]:
            pattern_type = pattern.get("type")
            pattern_count = pattern.get("count", 0)
            
            if pattern_type == "after_hours_access":
                # Thresholds for after-hours access
                warning_threshold = 5  # 5 accesses after hours
                critical_threshold = 15  # 15 accesses after hours
                
                if pattern_count > critical_threshold:
                    alert = generate_alert(
                        "security",
                        "credential-management",
                        "after_hours_access",
                        "critical",
                        pattern_count,
                        critical_threshold,
                        {"timestamp": metrics.get("timestamp"), "details": pattern.get("details")}
                    )
                    alerts.append(alert)
                elif pattern_count > warning_threshold:
                    alert = generate_alert(
                        "security",
                        "credential-management",
                        "after_hours_access",
                        "warning",
                        pattern_count,
                        warning_threshold,
                        {"timestamp": metrics.get("timestamp"), "details": pattern.get("details")}
                    )
                    alerts.append(alert)
    
    # Check rotation status against thresholds
    if "credential_rotation" in metrics:
        failed_rotations = metrics["credential_rotation"].get("failed", 0)
        
        # Any failed rotation should generate at least a warning
        if failed_rotations > 0:
            alert = generate_alert(
                "security",
                "credential-management",
                "failed_rotations",
                "warning" if failed_rotations == 1 else "critical",
                failed_rotations,
                1,  # Threshold of 1 failed rotation
                {"timestamp": metrics.get("timestamp"), "failures": metrics.get("credential_rotation_failures")}
            )
            alerts.append(alert)
    
    # Check credentials due for rotation
    if metrics.get("credentials_due_rotation"):
        due_count = len(metrics["credentials_due_rotation"])
        
        # Warning if any credentials are due for rotation
        if due_count > 0:
            alert = generate_alert(
                "security",
                "credential-management",
                "credentials_due_rotation",
                "warning",
                due_count,
                1,  # Threshold of 1 credential due for rotation
                {"timestamp": metrics.get("timestamp"), "credentials": metrics.get("credentials_due_rotation")}
            )
            alerts.append(alert)
    
    return alerts


def format_credential_metrics(metrics, format_type):
    """
    Formats credential metrics for output.

    Args:
        metrics (dict): Metrics to format
        format_type (str): Format type ('json', 'text', 'csv')

    Returns:
        str: Formatted metrics
    """
    if format_type == 'json':
        return json.dumps(metrics, indent=2)
    
    elif format_type == 'text':
        # Format as human-readable text
        text_output = [
            f"Credential Usage Metrics - {metrics.get('timestamp', 'N/A')}",
            f"Service: {metrics.get('service_name', 'credential-management')}",
            "-" * 50
        ]
        
        # Access metrics
        text_output.append("\nCredential Access Metrics:")
        text_output.append(f"  Total Access Count: {metrics.get('credential_access_count', 0)}")
        text_output.append(f"  Overall Access Frequency: {metrics.get('access_frequency', {}).get('overall', 0):.2f} per minute")
        
        # Client-specific access
        if metrics.get('credentials_by_client'):
            text_output.append("\n  Access by Client:")
            for client_id, count in metrics.get('credentials_by_client', {}).items():
                freq = metrics.get('access_frequency', {}).get('by_client', {}).get(client_id, 0)
                text_output.append(f"    {client_id}: {count} accesses ({freq:.2f} per minute)")
        
        # Unusual patterns
        if metrics.get('unusual_access_patterns'):
            text_output.append("\n  Unusual Access Patterns:")
            for pattern in metrics.get('unusual_access_patterns', []):
                text_output.append(f"    {pattern.get('type')}: {pattern.get('count')} occurrences")
        
        # Rotation metrics
        text_output.append("\nCredential Rotation Metrics:")
        rotation = metrics.get('credential_rotation', {})
        text_output.append(f"  Active: {rotation.get('active', 0)}")
        text_output.append(f"  Rotating: {rotation.get('rotating', 0)}")
        text_output.append(f"  Completed: {rotation.get('completed', 0)}")
        text_output.append(f"  Failed: {rotation.get('failed', 0)}")
        
        if 'average_days_since_rotation' in metrics:
            text_output.append(f"  Average Days Since Last Rotation: {metrics.get('average_days_since_rotation', 0):.1f}")
        
        # Due for rotation
        if metrics.get('credentials_due_rotation'):
            text_output.append("\n  Credentials Due for Rotation:")
            for cred in metrics.get('credentials_due_rotation', []):
                text_output.append(f"    {cred.get('client_id')}: {cred.get('days_until_rotation')} days until rotation")
        
        # Failed rotations
        if metrics.get('credential_rotation_failures'):
            text_output.append("\n  Failed Rotations:")
            for failure in metrics.get('credential_rotation_failures', []):
                text_output.append(f"    {failure.get('client_id')}: {failure.get('failure_reason')}")
        
        # Anomalies
        if metrics.get('anomalies'):
            text_output.append("\nDetected Anomalies:")
            for anomaly in metrics.get('anomalies', []):
                text_output.append(f"  {anomaly.get('anomaly_type')}: {anomaly.get('description')}")
                text_output.append(f"    Client: {anomaly.get('client_id')}")
                text_output.append(f"    Severity: {anomaly.get('severity')}")
        
        return "\n".join(text_output)
    
    elif format_type == 'csv':
        # Simplified CSV output focusing on key metrics
        csv_lines = [
            "timestamp,service_name,credential_access_count,overall_frequency,active_credentials,rotating_credentials,failed_rotations,credentials_due_rotation,anomaly_count",
            f"{metrics.get('timestamp', '')},{metrics.get('service_name', '')},{metrics.get('credential_access_count', 0)},{metrics.get('access_frequency', {}).get('overall', 0)},{metrics.get('credential_rotation', {}).get('active', 0)},{metrics.get('credential_rotation', {}).get('rotating', 0)},{metrics.get('credential_rotation', {}).get('failed', 0)},{len(metrics.get('credentials_due_rotation', []))},{metrics.get('anomaly_count', 0)}"
        ]
        return "\n".join(csv_lines)
    
    # Default to JSON if format not recognized
    return json.dumps(metrics, indent=2)


def detect_credential_anomalies(metrics):
    """
    Detects anomalies in credential usage patterns.

    Args:
        metrics (dict): Collected metrics

    Returns:
        list: List of detected anomalies
    """
    anomalies = []
    
    # Check for unusual access times
    if metrics.get("unusual_access_patterns"):
        for pattern in metrics.get("unusual_access_patterns", []):
            if pattern.get("type") == "after_hours_access" and pattern.get("count", 0) > 0:
                # Extract the client with most after-hours access
                most_access_client = None
                most_access_count = 0
                
                if pattern.get("details", {}).get("after_hours_distribution"):
                    for client_id, count in metrics.get("credentials_by_client", {}).items():
                        client_accesses = sum(
                            hour_count for hour, hour_count in 
                            pattern.get("details", {}).get("after_hours_distribution", {}).items()
                        )
                        if client_accesses > most_access_count:
                            most_access_count = client_accesses
                            most_access_client = client_id
                
                # Create anomaly for after-hours access
                anomaly = CredentialAnomaly(
                    anomaly_type="after_hours_access",
                    client_id=most_access_client or "multiple",
                    description=f"Detected {pattern.get('count')} credential accesses outside business hours",
                    details=pattern.get("details", {}),
                    severity="warning" if pattern.get("count") < 10 else "critical"
                )
                anomalies.append(anomaly)
    
    # Check for unusual access frequency
    access_frequency = metrics.get("access_frequency", {}).get("overall", 0)
    if access_frequency > 10:  # More than 10 accesses per minute
        # Find client with highest frequency
        highest_freq_client = None
        highest_freq = 0
        
        for client_id, freq in metrics.get("access_frequency", {}).get("by_client", {}).items():
            if freq > highest_freq:
                highest_freq = freq
                highest_freq_client = client_id
        
        # Create anomaly for high access frequency
        anomaly = CredentialAnomaly(
            anomaly_type="high_access_frequency",
            client_id=highest_freq_client or "multiple",
            description=f"Unusual credential access frequency: {access_frequency:.2f} accesses per minute",
            details={"overall_frequency": access_frequency, "client_frequencies": metrics.get("access_frequency", {}).get("by_client", {})},
            severity="warning" if access_frequency < 20 else "critical"
        )
        anomalies.append(anomaly)
    
    # Check for unusual access patterns by client
    for pattern in metrics.get("unusual_access_patterns", []):
        if pattern.get("type") == "high_frequency_access" and pattern.get("count", 0) > 0:
            # Get client with highest frequency
            highest_freq_client = None
            highest_freq = 0
            
            for client_id, freq in pattern.get("details", {}).get("high_frequency_clients", {}).items():
                if freq > highest_freq:
                    highest_freq = freq
                    highest_freq_client = client_id
            
            if highest_freq_client:
                # Create anomaly for high frequency client
                anomaly = CredentialAnomaly(
                    anomaly_type="client_high_frequency",
                    client_id=highest_freq_client,
                    description=f"Client {highest_freq_client} has unusually high access frequency: {highest_freq:.2f} per minute",
                    details={"client_id": highest_freq_client, "frequency": highest_freq},
                    severity="warning" if highest_freq < 30 else "critical"
                )
                anomalies.append(anomaly)
    
    # Check for unusual rotation patterns
    rotation_metrics = metrics.get("credential_rotation", {})
    
    # Failed rotations
    if rotation_metrics.get("failed", 0) > 0:
        # Get details of failed rotations
        failures = metrics.get("credential_rotation_failures", [])
        for failure in failures:
            anomaly = CredentialAnomaly(
                anomaly_type="rotation_failure",
                client_id=failure.get("client_id", "unknown"),
                description=f"Credential rotation failed: {failure.get('failure_reason', 'unknown reason')}",
                details=failure,
                severity="critical"  # Failed rotations are critical security issues
            )
            anomalies.append(anomaly)
    
    # Credentials due for rotation
    for cred in metrics.get("credentials_due_rotation", []):
        days_until = cred.get("days_until_rotation", 0)
        
        # Critical if less than 1 day, warning otherwise
        severity = "critical" if days_until < 1 else "warning"
        
        anomaly = CredentialAnomaly(
            anomaly_type="rotation_due",
            client_id=cred.get("client_id", "unknown"),
            description=f"Credential due for rotation in {days_until} days",
            details={"days_until_rotation": days_until, "age_seconds": cred.get("age_seconds", 0)},
            severity=severity
        )
        anomalies.append(anomaly)
    
    return anomalies


class CredentialMetricsCollector:
    """Class for collecting and analyzing credential usage metrics."""
    
    def __init__(self, redis_host, redis_port, redis_password, redis_ssl, conjur_url, timeout=None):
        """
        Initializes a new CredentialMetricsCollector instance.

        Args:
            redis_host (str): Redis host address
            redis_port (int): Redis port
            redis_password (str): Redis password
            redis_ssl (bool): Whether to use SSL for Redis connection
            conjur_url (str): Conjur vault URL
            timeout (int, optional): Connection timeout in seconds
        """
        self.redis_host = redis_host
        self.redis_port = redis_port
        self.redis_password = redis_password
        self.redis_ssl = redis_ssl
        self.conjur_url = conjur_url
        self.timeout = timeout
        self.redis_client = None
        
        # Configure logging
        self.logger = logging.getLogger("credential_metrics_collector")
    
    def connect(self):
        """
        Establishes connections to Redis and Conjur.

        Returns:
            bool: True if connections successful, False otherwise
        """
        try:
            # Connect to Redis
            self.redis_client = redis.Redis(
                host=self.redis_host,
                port=self.redis_port,
                password=self.redis_password,
                ssl=self.redis_ssl,
                socket_timeout=self.timeout or CONNECTION_TIMEOUT,
                decode_responses=True
            )
            
            # Check Redis health
            redis_health = check_redis_health(
                self.redis_host, 
                self.redis_port, 
                self.redis_password, 
                self.redis_ssl, 
                self.timeout
            )
            
            if redis_health["status"] != "healthy":
                self.logger.error(f"Redis connection failed: {redis_health['details']}")
                return False
            
            # Check Conjur vault health if URL is provided
            if self.conjur_url:
                conjur_health = check_service_health("conjur-vault", self.conjur_url, "/health", self.timeout)
                
                if conjur_health["status"] != "healthy":
                    self.logger.warning(f"Conjur vault connection failed: {conjur_health['details']}")
                    # We can still proceed without Conjur, so return True
            
            return True
        
        except Exception as e:
            self.logger.error(f"Error connecting to services: {str(e)}", exc_info=True)
            return False
    
    def collect_metrics(self):
        """
        Collects all credential usage metrics.

        Returns:
            dict: Collected metrics
        """
        # Connect to Redis if not already connected
        if self.redis_client is None:
            if not self.connect():
                return {"error": "Failed to connect to required services"}
        
        # Collect metrics using the standalone functions
        metrics = collect_credential_usage_metrics(
            self.redis_host,
            self.redis_port,
            self.redis_password,
            self.redis_ssl,
            self.conjur_url,
            self.timeout
        )
        
        return metrics
    
    def check_thresholds(self, metrics):
        """
        Checks metrics against defined thresholds.

        Args:
            metrics (dict): Collected metrics

        Returns:
            list: List of alerts for exceeded thresholds
        """
        return check_credential_metrics_thresholds(metrics)
    
    def close(self):
        """
        Closes connections and releases resources.
        """
        if self.redis_client:
            self.redis_client.close()
            self.redis_client = None


class CredentialAnomaly:
    """Class representing an anomaly in credential usage."""
    
    def __init__(self, anomaly_type, client_id, description, details, severity):
        """
        Initializes a new CredentialAnomaly instance.

        Args:
            anomaly_type (str): Type of anomaly
            client_id (str): Client ID associated with the anomaly
            description (str): Human-readable description of the anomaly
            details (dict): Additional details about the anomaly
            severity (str): Severity level (warning, critical)
        """
        self.anomaly_type = anomaly_type
        self.client_id = client_id
        self.description = description
        self.details = details
        self.severity = severity
        self.timestamp = datetime.datetime.now()
        
        # Validate severity level
        if severity not in ["warning", "critical"]:
            self.severity = "warning"  # Default to warning if invalid
    
    def to_dict(self):
        """
        Converts the anomaly to a dictionary representation.

        Returns:
            dict: Dictionary representation of the anomaly
        """
        return {
            "anomaly_type": self.anomaly_type,
            "client_id": self.client_id,
            "description": self.description,
            "details": self.details,
            "severity": self.severity,
            "timestamp": format_timestamp_iso(self.timestamp)
        }
    
    def to_alert(self):
        """
        Converts the anomaly to an alert format.

        Returns:
            dict: Alert representation of the anomaly
        """
        return {
            "type": "credential_anomaly",
            "service_name": "credential-management",
            "metric_name": self.anomaly_type,
            "severity": self.severity,
            "timestamp": format_timestamp_iso(self.timestamp),
            "details": {
                "client_id": self.client_id,
                "description": self.description,
                **self.details
            }
        }


def main():
    """
    Main function for CLI usage.

    Returns:
        int: Exit code
    """
    parser = argparse.ArgumentParser(
        description="Collect and monitor credential usage metrics"
    )
    parser.add_argument(
        "--redis-host", 
        default=REDIS_HOST,
        help=f"Redis host address (default: {REDIS_HOST})"
    )
    parser.add_argument(
        "--redis-port", 
        type=int, 
        default=REDIS_PORT,
        help=f"Redis port (default: {REDIS_PORT})"
    )
    parser.add_argument(
        "--redis-password", 
        default=REDIS_PASSWORD,
        help="Redis password"
    )
    parser.add_argument(
        "--redis-ssl", 
        type=bool, 
        default=REDIS_SSL,
        help=f"Whether to use SSL for Redis connection (default: {REDIS_SSL})"
    )
    parser.add_argument(
        "--conjur-url", 
        default=CONJUR_VAULT_URL,
        help=f"Conjur vault URL (default: {CONJUR_VAULT_URL})"
    )
    parser.add_argument(
        "--timeout", 
        type=int, 
        default=CONNECTION_TIMEOUT,
        help=f"Connection timeout in seconds (default: {CONNECTION_TIMEOUT})"
    )
    parser.add_argument(
        "--format", 
        choices=["json", "text", "csv"], 
        default=DEFAULT_OUTPUT_FORMAT,
        help=f"Output format (default: {DEFAULT_OUTPUT_FORMAT})"
    )
    parser.add_argument(
        "--output", 
        help="Output file path (default: stdout)"
    )
    parser.add_argument(
        "--alert", 
        action="store_true",
        help="Generate alerts for metrics exceeding thresholds"
    )
    parser.add_argument(
        "--debug", 
        action="store_true",
        help="Enable debug logging"
    )
    
    args = parser.parse_args()
    
    # Configure logging
    log_level = logging.DEBUG if args.debug else logging.INFO
    logging.basicConfig(
        level=log_level,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    )
    
    try:
        # Collect metrics
        metrics = collect_credential_usage_metrics(
            args.redis_host,
            args.redis_port,
            args.redis_password,
            args.redis_ssl,
            args.conjur_url,
            args.timeout
        )
        
        # Check for alerts if requested
        if args.alert:
            alerts = check_credential_metrics_thresholds(metrics)
            
            if alerts:
                logger.info(f"Generated {len(alerts)} alerts for exceeded thresholds")
                
                # Include alert count in metrics
                metrics["alert_count"] = len(alerts)
                metrics["alerts"] = alerts
                
                # Send alerts if configured
                for alert in alerts:
                    try:
                        send_alert_notification(alert)
                    except Exception as e:
                        logger.error(f"Failed to send alert notification: {str(e)}", exc_info=True)
        
        # Format metrics for output
        formatted_metrics = format_credential_metrics(metrics, args.format)
        
        # Output metrics
        if args.output:
            with open(args.output, 'w') as f:
                f.write(formatted_metrics)
            logger.info(f"Metrics written to {args.output}")
        else:
            print(formatted_metrics)
        
        return 0
    
    except Exception as e:
        logger.error(f"Error collecting credential metrics: {str(e)}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())