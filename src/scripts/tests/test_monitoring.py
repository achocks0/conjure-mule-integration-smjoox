import pytest
import unittest.mock
import json
import datetime
import requests_mock

from src.scripts.monitoring.config import (
    PAYMENT_EAPI_URL, PAYMENT_SAPI_URL, CONJUR_VAULT_URL,
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL,
    HEALTH_CHECK_ENDPOINTS, ALERT_THRESHOLDS,
    get_alert_threshold, get_notification_channels_for_alert
)

from src.scripts.monitoring.utils import (
    check_service_health, check_redis_health, collect_service_metrics,
    collect_token_metrics, check_metric_thresholds, generate_alert,
    send_alert_notification, calculate_sla_compliance, MonitoringError,
    ServiceHealthError
)

from src.scripts.monitoring.health_check import (
    check_all_services_health, analyze_health_results,
    generate_health_alerts, calculate_availability_sla
)

from src.scripts.monitoring.token_usage_metrics import (
    analyze_token_metrics, detect_token_anomalies, calculate_token_usage_trends
)

from src.scripts.monitoring.credential_usage_metrics import (
    collect_credential_usage_metrics, check_credential_metrics_thresholds,
    detect_credential_anomalies, CredentialMetricsCollector,
    CredentialAnomaly
)


@pytest.fixture
def fake_redis():
    """Fixture providing a mocked Redis client for testing"""
    redis_mock = unittest.mock.MagicMock()
    # Configure basic responses for common Redis commands
    redis_mock.ping.return_value = True
    redis_mock.info.return_value = {
        "redis_version": "6.2.0",
        "connected_clients": 10,
        "used_memory_human": "1.5M",
        "evicted_keys": 0
    }
    redis_mock.hgetall.return_value = {"status": "active", "timestamp": "1623761445"}
    redis_mock.keys.return_value = ["token:client1:12345", "token:client2:67890"]
    
    return redis_mock


def test_check_service_health_success(requests_mock):
    """Tests the check_service_health function with a successful health check response"""
    # Set up mock response for service health endpoint with 200 status code
    test_url = f"{PAYMENT_EAPI_URL}/health"
    test_response = {
        "status": "UP",
        "components": {
            "db": {"status": "UP"},
            "redis": {"status": "UP"}
        },
        "version": "1.0.0"
    }
    requests_mock.get(test_url, json=test_response, status_code=200)
    
    # Call check_service_health function with test parameters
    result = check_service_health("payment-eapi", PAYMENT_EAPI_URL, "/health")
    
    # Assert that the result contains expected keys
    assert "service_name" in result
    assert "status" in result
    assert "response_time_ms" in result
    assert "details" in result
    
    # Assert that the status is 'healthy'
    assert result["status"] == "healthy"
    
    # Assert that response_time is a positive number
    assert result["response_time_ms"] > 0
    
    # Assert that details contains expected information from the mock response
    assert result["details"]["status"] == "UP"
    assert "components" in result["details"]


def test_check_service_health_failure(requests_mock):
    """Tests the check_service_health function with a failed health check response"""
    # Set up mock response for service health endpoint with 500 status code
    test_url = f"{PAYMENT_EAPI_URL}/health"
    test_response = {"error": "Internal server error"}
    requests_mock.get(test_url, json=test_response, status_code=500)
    
    # Call check_service_health function with test parameters
    result = check_service_health("payment-eapi", PAYMENT_EAPI_URL, "/health")
    
    # Assert that the result contains expected keys
    assert "service_name" in result
    assert "status" in result
    assert "response_time_ms" in result
    assert "details" in result
    
    # Assert that the status is 'unhealthy'
    assert result["status"] == "unhealthy"
    
    # Assert that response_time is a positive number
    assert result["response_time_ms"] > 0
    
    # Assert that details contains error information
    assert "status_code" in result["details"]
    assert result["details"]["status_code"] == 500


def test_check_service_health_timeout(requests_mock):
    """Tests the check_service_health function with a connection timeout"""
    # Set up mock response for service health endpoint that raises a Timeout exception
    test_url = f"{PAYMENT_EAPI_URL}/health"
    requests_mock.get(test_url, exc=requests.exceptions.Timeout("Connection timed out"))
    
    # Call check_service_health function with test parameters
    result = check_service_health("payment-eapi", PAYMENT_EAPI_URL, "/health")
    
    # Assert that the result contains expected keys
    assert "service_name" in result
    assert "status" in result
    assert "details" in result
    
    # Assert that the status is 'unhealthy'
    assert result["status"] == "unhealthy"
    
    # Assert that details contains timeout error information
    assert "error" in result["details"]
    assert result["details"]["error"] == "timeout"


def test_check_redis_health_success(fake_redis):
    """Tests the check_redis_health function with a successful Redis connection"""
    # Mock Redis connection to return successful PING response
    with unittest.mock.patch('src.scripts.monitoring.utils.redis.Redis', return_value=fake_redis):
        # Call check_redis_health function with test parameters
        result = check_redis_health(REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL)
        
        # Assert that the result contains expected keys
        assert "service_name" in result
        assert "status" in result
        assert "response_time_ms" in result
        assert "details" in result
        
        # Assert that the status is 'healthy'
        assert result["status"] == "healthy"
        
        # Assert that response_time is a positive number
        assert result["response_time_ms"] > 0


def test_check_redis_health_failure():
    """Tests the check_redis_health function with a failed Redis connection"""
    # Mock Redis connection to raise an exception
    with unittest.mock.patch('src.scripts.monitoring.utils.redis.Redis') as mock_redis:
        mock_redis.side_effect = Exception("Connection refused")
        
        # Call check_redis_health function with test parameters
        result = check_redis_health(REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL)
        
        # Assert that the result contains expected keys
        assert "service_name" in result
        assert "status" in result
        assert "details" in result
        
        # Assert that the status is 'unhealthy'
        assert result["status"] == "unhealthy"
        
        # Assert that details contains error information
        assert "error" in result["details"]
        assert "message" in result["details"]


def test_collect_service_metrics_success(requests_mock):
    """Tests the collect_service_metrics function with a successful metrics response"""
    # Set up mock response for service metrics endpoint with 200 status code and sample metrics
    test_url = f"{PAYMENT_EAPI_URL}/metrics"
    test_response = {
        "authentication_success_rate": 99.8,
        "authentication_failures": 2,
        "token_generation_rate": 45.6,
        "token_validation_failures": 1,
        "average_response_time_ms": 85
    }
    requests_mock.get(test_url, json=test_response, status_code=200)
    
    # Call collect_service_metrics function with test parameters
    result = collect_service_metrics("payment-eapi", PAYMENT_EAPI_URL, "/metrics")
    
    # Assert that the result contains expected metrics from the mock response
    assert "authentication_success_rate" in result
    assert result["authentication_success_rate"] == 99.8
    assert "token_generation_rate" in result
    assert result["token_generation_rate"] == 45.6
    
    # Assert that the result contains service_name and timestamp
    assert "service_name" in result
    assert result["service_name"] == "payment-eapi"
    assert "timestamp" in result


def test_collect_service_metrics_failure(requests_mock):
    """Tests the collect_service_metrics function with a failed metrics response"""
    # Set up mock response for service metrics endpoint with 500 status code
    test_url = f"{PAYMENT_EAPI_URL}/metrics"
    test_response = {"error": "Internal server error"}
    requests_mock.get(test_url, json=test_response, status_code=500)
    
    # Call collect_service_metrics function with test parameters
    result = collect_service_metrics("payment-eapi", PAYMENT_EAPI_URL, "/metrics")
    
    # Assert that the result contains error information
    assert "error" in result
    assert "status_code" in result
    assert result["status_code"] == 500
    
    # Assert that the result contains service_name
    assert "service_name" in result
    assert result["service_name"] == "payment-eapi"


def test_collect_token_metrics(fake_redis):
    """Tests the collect_token_metrics function with a mocked Redis instance"""
    # Set up fake Redis with sample token data
    token_data = {
        "exp": str(int(time.time()) + 3600),  # Expires in 1 hour
        "iat": str(int(time.time()) - 300)   # Issued 5 minutes ago
    }
    fake_redis.hgetall.return_value = token_data
    fake_redis.keys.return_value = [
        "token:client1:12345", 
        "token:client1:67890", 
        "token:client2:54321"
    ]
    
    # Mock Redis client to return our fake Redis
    with unittest.mock.patch('src.scripts.monitoring.utils.redis.Redis', return_value=fake_redis):
        # Call collect_token_metrics function with test parameters
        result = collect_token_metrics(REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL)
        
        # Assert that the result contains expected token metrics
        assert "token_count" in result
        assert result["token_count"] == 3
        assert "active_tokens" in result
        assert result["active_tokens"] == 3  # All tokens are active
        assert "tokens_by_client" in result
        assert result["tokens_by_client"] == {"client1": 2, "client2": 1}
        
        # Assert that metrics values are consistent with the sample data
        assert "service_name" in result
        assert result["service_name"] == "token-service"
        assert "timestamp" in result


def test_check_metric_thresholds():
    """Tests the check_metric_thresholds function for detecting threshold violations"""
    # Create sample metrics with some values exceeding warning thresholds
    warning_metrics = {
        "service_name": "payment-eapi",
        "timestamp": "2023-06-15T12:00:00Z",
        "authentication_failures": 6,  # Exceeds warning threshold of 5
        "token_validation_failures": 3,  # Below threshold
        "api_response_time": 350  # Exceeds warning threshold of 300
    }
    
    # Create sample metrics with some values exceeding critical thresholds
    critical_metrics = {
        "service_name": "payment-eapi",
        "timestamp": "2023-06-15T12:00:00Z",
        "authentication_failures": 12,  # Exceeds critical threshold of 10
        "token_validation_failures": 3,  # Below threshold
        "api_response_time": 550  # Exceeds critical threshold of 500
    }
    
    # Call check_metric_thresholds function with warning-level sample metrics
    warning_alerts = check_metric_thresholds(warning_metrics, "security")
    
    # Assert that the result contains alerts for metrics exceeding warning thresholds
    assert len(warning_alerts) > 0
    assert any(alert["metric_name"] == "authentication_failures" for alert in warning_alerts)
    
    # Assert that the alerts have 'warning' severity
    assert all(alert["severity"] == "warning" for alert in warning_alerts)
    
    # Call check_metric_thresholds function with critical-level sample metrics
    critical_alerts = check_metric_thresholds(critical_metrics, "security")
    
    # Assert that the result contains alerts for metrics exceeding critical thresholds
    assert len(critical_alerts) > 0
    assert any(alert["metric_name"] == "authentication_failures" for alert in critical_alerts)
    
    # Assert that the alerts have 'critical' severity
    assert all(alert["severity"] == "critical" for alert in critical_alerts)


def test_generate_alert():
    """Tests the generate_alert function for creating alert objects"""
    # Call generate_alert function with test parameters
    alert = generate_alert(
        "security",
        "payment-eapi",
        "authentication_failures",
        "warning",
        6,
        5,
        {"additional_info": "Test alert"}
    )
    
    # Assert that the result contains expected alert fields
    assert "id" in alert
    assert "timestamp" in alert
    assert "type" in alert
    assert "service_name" in alert
    assert "metric_name" in alert
    assert "severity" in alert
    assert "value" in alert
    assert "threshold" in alert
    assert "details" in alert
    
    # Assert that the alert contains the provided severity, value, and threshold
    assert alert["severity"] == "warning"
    assert alert["value"] == 6
    assert alert["threshold"] == 5
    assert alert["type"] == "security"
    assert alert["service_name"] == "payment-eapi"
    assert alert["metric_name"] == "authentication_failures"
    
    # Assert that the alert contains a timestamp and alert_id
    assert isinstance(alert["id"], str)
    assert isinstance(alert["timestamp"], str)


def test_send_alert_notification_success():
    """Tests the send_alert_notification function with successful notification delivery"""
    # Create sample alert data
    alert = {
        "id": "test-alert-1",
        "timestamp": "2023-06-15T12:00:00Z",
        "type": "security",
        "service_name": "payment-eapi",
        "metric_name": "authentication_failures",
        "severity": "warning",
        "value": 6,
        "threshold": 5,
        "details": {"additional_info": "Test alert"}
    }
    
    # Mock notification channels and sending functions
    with unittest.mock.patch('src.scripts.monitoring.utils.get_notification_channels_for_alert') as mock_get_notification_channels:
        mock_get_notification_channels.return_value = {
            "pagerduty": {"type": "pagerduty", "service_key": "test_key"},
            "email": {"type": "email", "recipients": ["alerts@example.com"]}
        }
        
        with unittest.mock.patch('src.scripts.monitoring.utils.send_pagerduty_alert') as mock_send_pagerduty:
            with unittest.mock.patch('src.scripts.monitoring.utils.send_email_alert') as mock_send_email:
                with unittest.mock.patch('src.scripts.monitoring.utils.send_slack_alert') as mock_send_slack:
                    # Configure mocks to return success
                    mock_send_pagerduty.return_value = True
                    mock_send_email.return_value = True
                    mock_send_slack.return_value = True
                    
                    # Call send_alert_notification function with sample alert
                    result = send_alert_notification(alert)
                    
                    # Assert that the function returns True
                    assert result is True
                    
                    # Assert that the appropriate notification functions were called with correct parameters
                    mock_send_pagerduty.assert_called_once()
                    mock_send_email.assert_called_once()
                    mock_send_slack.assert_not_called()  # Slack wasn't in our mocked channels


def test_send_alert_notification_failure():
    """Tests the send_alert_notification function with failed notification delivery"""
    # Create sample alert data
    alert = {
        "id": "test-alert-1",
        "timestamp": "2023-06-15T12:00:00Z",
        "type": "security",
        "service_name": "payment-eapi",
        "metric_name": "authentication_failures",
        "severity": "warning",
        "value": 6,
        "threshold": 5,
        "details": {"additional_info": "Test alert"}
    }
    
    # Mock notification channels and sending functions
    with unittest.mock.patch('src.scripts.monitoring.utils.get_notification_channels_for_alert') as mock_get_notification_channels:
        mock_get_notification_channels.return_value = {
            "pagerduty": {"type": "pagerduty", "service_key": "test_key"},
            "email": {"type": "email", "recipients": ["alerts@example.com"]}
        }
        
        with unittest.mock.patch('src.scripts.monitoring.utils.send_pagerduty_alert') as mock_send_pagerduty:
            with unittest.mock.patch('src.scripts.monitoring.utils.send_email_alert') as mock_send_email:
                with unittest.mock.patch('src.scripts.monitoring.utils.send_slack_alert') as mock_send_slack:
                    # Configure mocks to return failure
                    mock_send_pagerduty.return_value = False
                    mock_send_email.return_value = False
                    mock_send_slack.return_value = False
                    
                    # Call send_alert_notification function with sample alert
                    result = send_alert_notification(alert)
                    
                    # Assert that the function returns False
                    assert result is False
                    
                    # Assert that the appropriate notification functions were called with correct parameters
                    mock_send_pagerduty.assert_called_once()
                    mock_send_email.assert_called_once()
                    mock_send_slack.assert_not_called()  # Slack wasn't in our mocked channels


def test_calculate_sla_compliance():
    """Tests the calculate_sla_compliance function for different metric types"""
    # Test response time metric (lower is better)
    # Call calculate_sla_compliance with response time metric below target
    response_time_result_compliant = calculate_sla_compliance(
        "api_response_time", 
        400,  # Actual value
        500   # Target value (threshold)
    )
    
    # Assert that compliance percentage is above 100%
    assert response_time_result_compliant["compliance"] > 100
    # Assert that status is 'compliant'
    assert response_time_result_compliant["status"] == "compliant"
    
    # Call calculate_sla_compliance with response time metric above target
    response_time_result_non_compliant = calculate_sla_compliance(
        "api_response_time", 
        600,  # Actual value
        500   # Target value (threshold)
    )
    
    # Assert that compliance percentage is below 100%
    assert response_time_result_non_compliant["compliance"] < 100
    # Assert that status is 'non-compliant'
    assert response_time_result_non_compliant["status"] == "non-compliant"
    
    # Test availability metric (higher is better)
    # Call calculate_sla_compliance with availability metric above target
    availability_result_compliant = calculate_sla_compliance(
        "availability", 
        99.95,  # Actual value
        99.9    # Target value (threshold)
    )
    
    # Assert that compliance percentage is above 100%
    assert availability_result_compliant["compliance"] > 100
    # Assert that status is 'compliant'
    assert availability_result_compliant["status"] == "compliant"
    
    # Call calculate_sla_compliance with availability metric below target
    availability_result_non_compliant = calculate_sla_compliance(
        "availability", 
        99.8,   # Actual value
        99.9    # Target value (threshold)
    )
    
    # Assert that compliance percentage is below 100%
    assert availability_result_non_compliant["compliance"] < 100
    # Assert that status is 'non-compliant'
    assert availability_result_non_compliant["status"] == "non-compliant"


def test_check_all_services_health():
    """Tests the check_all_services_health function for comprehensive health checking"""
    # Mock check_service_health to return predefined results for each service
    with unittest.mock.patch('src.scripts.monitoring.health_check.check_service_health') as mock_check_service_health:
        # Configure mock to return different results based on service name
        def mock_check_service_side_effect(service_name, *args, **kwargs):
            if service_name == "payment-eapi":
                return {
                    "service_name": "payment-eapi",
                    "status": "healthy",
                    "response_time_ms": 50,
                    "details": {"version": "1.0.0"}
                }
            elif service_name == "payment-sapi":
                return {
                    "service_name": "payment-sapi",
                    "status": "healthy",
                    "response_time_ms": 45,
                    "details": {"version": "1.0.0"}
                }
            else:  # conjur-vault
                return {
                    "service_name": "conjur-vault",
                    "status": "healthy",
                    "response_time_ms": 60,
                    "details": {"version": "1.0.0"}
                }
        
        mock_check_service_health.side_effect = mock_check_service_side_effect
        
        # Mock check_redis_health to return predefined result
        with unittest.mock.patch('src.scripts.monitoring.health_check.check_redis_health') as mock_check_redis_health:
            mock_check_redis_health.return_value = {
                "service_name": "redis-cache",
                "status": "healthy",
                "response_time_ms": 20,
                "details": {"connected_clients": 5}
            }
            
            # Call check_all_services_health function
            results = check_all_services_health()
            
            # Assert that the result contains health data for all expected services
            assert "services" in results
            assert "payment-eapi" in results["services"]
            assert "payment-sapi" in results["services"]
            assert "conjur-vault" in results["services"]
            assert "redis-cache" in results["services"]
            
            # Assert that the result contains an overall system health status
            assert "overall_status" in results
            assert results["overall_status"] == "healthy"
            
            # Assert that the result contains a timestamp
            assert "timestamp" in results


def test_analyze_health_results():
    """Tests the analyze_health_results function for health data analysis"""
    # Create sample health results with mixed healthy and unhealthy services
    health_results = {
        "timestamp": "2023-06-15T12:00:00Z",
        "overall_status": "unhealthy",
        "services": {
            "payment-eapi": {
                "service_name": "payment-eapi",
                "status": "healthy",
                "response_time_ms": 50,
                "details": {"version": "1.0.0"}
            },
            "payment-sapi": {
                "service_name": "payment-sapi",
                "status": "unhealthy",
                "response_time_ms": 550,  # High response time
                "details": {"error": "Database connection timeout"}
            },
            "conjur-vault": {
                "service_name": "conjur-vault",
                "status": "healthy",
                "response_time_ms": 60,
                "details": {"version": "1.0.0"}
            },
            "redis-cache": {
                "service_name": "redis-cache",
                "status": "healthy",
                "response_time_ms": 20,
                "details": {"connected_clients": 5}
            }
        }
    }
    
    # Call analyze_health_results function with sample data
    analysis = analyze_health_results(health_results)
    
    # Assert that the result contains availability percentages for each service
    assert "availability" in analysis
    assert "payment-eapi" in analysis["availability"]
    assert "payment-sapi" in analysis["availability"]
    assert analysis["availability"]["payment-eapi"] == 100  # Healthy = 100%
    assert analysis["availability"]["payment-sapi"] == 0    # Unhealthy = 0%
    
    # Assert that the result contains average response times
    assert "response_times" in analysis
    assert "payment-eapi" in analysis["response_times"]
    assert "payment-sapi" in analysis["response_times"]
    assert analysis["response_times"]["payment-eapi"] == 50
    assert analysis["response_times"]["payment-sapi"] == 550
    
    # Assert that the result correctly identifies unhealthy services
    assert "issues" in analysis
    assert any(issue["service_name"] == "payment-sapi" and issue["issue_type"] == "availability" for issue in analysis["issues"])
    
    # Assert that the result correctly identifies services with response time issues
    assert any(issue["service_name"] == "payment-sapi" and issue["issue_type"] == "performance" for issue in analysis["issues"])


def test_generate_health_alerts():
    """Tests the generate_health_alerts function for creating alerts from health data"""
    # Create sample health results with unhealthy services and response time issues
    health_results = {
        "timestamp": "2023-06-15T12:00:00Z",
        "overall_status": "unhealthy",
        "services": {
            "payment-eapi": {
                "service_name": "payment-eapi",
                "status": "healthy",
                "response_time_ms": 550,  # Exceeds warning threshold
                "details": {"version": "1.0.0"}
            },
            "payment-sapi": {
                "service_name": "payment-sapi",
                "status": "unhealthy",
                "response_time_ms": 200,
                "details": {"error": "Database connection timeout"}
            },
            "conjur-vault": {
                "service_name": "conjur-vault",
                "status": "healthy",
                "response_time_ms": 60,
                "details": {"version": "1.0.0"}
            },
            "redis-cache": {
                "service_name": "redis-cache",
                "status": "healthy",
                "response_time_ms": 20,
                "details": {"connected_clients": 5}
            }
        }
    }
    
    # Mock generate_alert to return predefined alert objects
    with unittest.mock.patch('src.scripts.monitoring.health_check.generate_alert') as mock_generate_alert:
        # Configure mock to return an alert with the same parameters it receives
        def mock_generate_alert_side_effect(alert_type, service_name, metric_name, severity, value, threshold, details=None):
            return {
                "id": "test-alert",
                "timestamp": "2023-06-15T12:00:00Z",
                "type": alert_type,
                "service_name": service_name,
                "metric_name": metric_name,
                "severity": severity,
                "value": value,
                "threshold": threshold,
                "details": details or {}
            }
        
        mock_generate_alert.side_effect = mock_generate_alert_side_effect
        
        # Call generate_health_alerts function with sample data
        alerts = generate_health_alerts(health_results)
        
        # Assert that the function generates alerts for unhealthy services
        assert any(alert["service_name"] == "payment-sapi" and alert["type"] == "availability" for alert in alerts)
        
        # Assert that the function generates alerts for response time issues
        assert any(alert["service_name"] == "payment-eapi" and alert["metric_name"] == "response_time" for alert in alerts)
        
        # Assert that the alerts have appropriate severity levels
        assert any(alert["service_name"] == "payment-sapi" and alert["severity"] == "critical" for alert in alerts)
        
        # Assert that generate_alert was called with correct parameters
        assert mock_generate_alert.call_count > 0


def test_calculate_availability_sla():
    """Tests the calculate_availability_sla function for SLA compliance calculation"""
    # Create sample health results with various availability levels
    health_results = {
        "timestamp": "2023-06-15T12:00:00Z",
        "overall_status": "unhealthy",
        "services": {
            "payment-eapi": {
                "service_name": "payment-eapi",
                "status": "healthy",
                "response_time_ms": 50,
                "details": {"version": "1.0.0"}
            },
            "payment-sapi": {
                "service_name": "payment-sapi",
                "status": "unhealthy",
                "response_time_ms": 200,
                "details": {"error": "Database connection timeout"}
            },
            "conjur-vault": {
                "service_name": "conjur-vault",
                "status": "healthy",
                "response_time_ms": 60,
                "details": {"version": "1.0.0"}
            },
            "redis-cache": {
                "service_name": "redis-cache",
                "status": "healthy",
                "response_time_ms": 20,
                "details": {"connected_clients": 5}
            }
        }
    }
    
    # Mock calculate_sla_compliance to return predefined compliance results
    with unittest.mock.patch('src.scripts.monitoring.health_check.calculate_sla_compliance') as mock_calculate_sla_compliance:
        # Configure mock to return a compliance result with the value passed to it
        def mock_calculate_sla_compliance_side_effect(metric_name, actual_value, target_value=None):
            compliance = (actual_value / 99.9) * 100 if target_value is None else (actual_value / target_value) * 100
            status = "compliant" if compliance >= 100 else "non-compliant"
            return {
                "metric": metric_name,
                "actual": actual_value,
                "target": target_value or 99.9,
                "compliance": round(compliance, 2),
                "status": status
            }
        
        mock_calculate_sla_compliance.side_effect = mock_calculate_sla_compliance_side_effect
        
        # Call calculate_availability_sla function with sample data
        sla_data = calculate_availability_sla(health_results)
        
        # Assert that the result contains SLA compliance data for each service
        assert "services" in sla_data
        assert "payment-eapi" in sla_data["services"]
        assert "payment-sapi" in sla_data["services"]
        
        # Assert that the result contains overall system SLA compliance
        assert "overall" in sla_data
        
        # Assert that calculate_sla_compliance was called with correct parameters
        assert mock_calculate_sla_compliance.call_count >= len(health_results["services"])


def test_analyze_token_metrics():
    """Tests the analyze_token_metrics function for token usage analysis"""
    # Create sample token metrics data
    token_metrics = {
        "service_name": "token-service",
        "timestamp": "2023-06-15T12:00:00Z",
        "token_count": 100,
        "active_tokens": 80,
        "token_generation_rate": 5,
        "token_expiration_rate": 4,
        "tokens_by_client": {
            "client1": 50,
            "client2": 30,
            "client3": 20
        },
        "redis_memory_used": "1.5M",
        "redis_connected_clients": 10,
        "redis_evicted_keys": 0
    }
    
    # Call analyze_token_metrics function with sample data
    analysis = analyze_token_metrics(token_metrics)
    
    # Assert that the result contains token usage trends
    assert "token_usage_summary" in analysis
    assert "active_percentage" in analysis["token_usage_summary"]
    assert analysis["token_usage_summary"]["active_percentage"] == 80.0  # 80 out of 100 tokens are active
    
    # Assert that the result contains client usage patterns
    assert "client_usage_summary" in analysis
    assert "total_clients" in analysis["client_usage_summary"]
    assert analysis["client_usage_summary"]["total_clients"] == 3
    assert "top_clients" in analysis["client_usage_summary"]
    assert "client1" in analysis["client_usage_summary"]["top_clients"]
    
    # Assert that the result contains token expiration patterns
    assert "expiration_patterns" in analysis
    assert "generation_to_expiration_ratio" in analysis["expiration_patterns"]
    
    # Assert that the analysis results are consistent with the sample data
    assert analysis["token_usage_summary"]["total_tokens"] == 100
    assert analysis["token_usage_summary"]["active_tokens"] == 80


def test_detect_token_anomalies():
    """Tests the detect_token_anomalies function for identifying unusual token patterns"""
    # Create sample token metrics with normal patterns
    normal_metrics = {
        "service_name": "token-service",
        "timestamp": "2023-06-15T12:00:00Z",
        "token_count": 100,
        "active_tokens": 80,
        "token_generation_rate": 5,
        "token_expiration_rate": 4,
        "tokens_by_client": {
            "client1": 50,
            "client2": 30,
            "client3": 20
        }
    }
    
    # Create sample token metrics with anomalous patterns
    anomalous_metrics = {
        "service_name": "token-service",
        "timestamp": "2023-06-15T12:00:00Z",
        "token_count": 500,  # Unusually high
        "active_tokens": 100,
        "token_generation_rate": 50,  # Spike in generation rate
        "token_expiration_rate": 2,    # Low expiration rate
        "tokens_by_client": {
            "client1": 50,
            "client2": 30,
            "client3": 20,
            "new_client": 400  # Unusual activity from new client
        }
    }
    
    # Create sample historical metrics
    historical_metrics = [
        {
            "service_name": "token-service",
            "timestamp": "2023-06-15T11:00:00Z",
            "token_count": 90,
            "active_tokens": 75,
            "token_generation_rate": 4,
            "token_expiration_rate": 3,
            "tokens_by_client": {
                "client1": 45,
                "client2": 25,
                "client3": 20
            }
        },
        {
            "service_name": "token-service",
            "timestamp": "2023-06-15T10:00:00Z",
            "token_count": 85,
            "active_tokens": 70,
            "token_generation_rate": 5,
            "token_expiration_rate": 4,
            "tokens_by_client": {
                "client1": 40,
                "client2": 25,
                "client3": 20
            }
        },
        {
            "service_name": "token-service",
            "timestamp": "2023-06-15T09:00:00Z",
            "token_count": 80,
            "active_tokens": 65,
            "token_generation_rate": 4,
            "token_expiration_rate": 3,
            "tokens_by_client": {
                "client1": 35,
                "client2": 25,
                "client3": 20
            }
        }
    ]
    
    # Call detect_token_anomalies with normal metrics
    normal_anomalies = detect_token_anomalies(normal_metrics, historical_metrics)
    
    # Assert that no anomalies are detected
    assert len(normal_anomalies) == 0
    
    # Call detect_token_anomalies with anomalous metrics
    anomalous_results = detect_token_anomalies(anomalous_metrics, historical_metrics)
    
    # Assert that anomalies are detected
    assert len(anomalous_results) > 0
    
    # Assert that detected anomalies have appropriate severity and description
    token_generation_anomaly = next((a for a in anomalous_results if a["type"] == "token_generation_spike"), None)
    assert token_generation_anomaly is not None
    assert token_generation_anomaly["severity"] == "high"
    
    new_client_anomaly = next((a for a in anomalous_results if a["type"] == "new_client_high_activity"), None)
    assert new_client_anomaly is not None


def test_credential_metrics_collector():
    """Tests the CredentialMetricsCollector class for credential usage monitoring"""
    # Mock Redis connection and health check functions
    with unittest.mock.patch('src.scripts.monitoring.credential_usage_metrics.redis.Redis') as mock_redis:
        with unittest.mock.patch('src.scripts.monitoring.credential_usage_metrics.check_service_health') as mock_check_service_health:
            # Configure mocks to return successful health checks and Redis connection
            mock_redis_instance = unittest.mock.MagicMock()
            mock_redis_instance.ping.return_value = True
            mock_redis_instance.keys.return_value = ["credential:access:client1:12345", "credential:rotation:client1"]
            mock_redis_instance.hgetall.return_value = {"status": "active", "last_rotation": "1623761445"}
            
            mock_redis.return_value = mock_redis_instance
            
            mock_check_service_health.return_value = {
                "service_name": "conjur-vault",
                "status": "healthy",
                "response_time_ms": 60,
                "details": {"version": "1.0.0"}
            }
            
            # Create CredentialMetricsCollector instance with test parameters
            collector = CredentialMetricsCollector(
                REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL, CONJUR_VAULT_URL
            )
            
            # Test connect method returns True for successful connections
            assert collector.connect() is True
            
            # Test collect_metrics method returns expected credential metrics
            metrics = collector.collect_metrics()
            assert "service_name" in metrics
            assert metrics["service_name"] == "credential-management"
            assert "timestamp" in metrics
            
            # Test check_thresholds method identifies threshold violations
            with unittest.mock.patch('src.scripts.monitoring.credential_usage_metrics.check_credential_metrics_thresholds') as mock_check_thresholds:
                mock_check_thresholds.return_value = [{"id": "test-alert"}]
                alerts = collector.check_thresholds(metrics)
                assert len(alerts) == 1
            
            # Test close method properly closes connections
            collector.close()
            assert collector.redis_client is None


def test_credential_anomaly_class():
    """Tests the CredentialAnomaly class for representing credential usage anomalies"""
    # Create CredentialAnomaly instance with test parameters
    anomaly = CredentialAnomaly(
        anomaly_type="after_hours_access",
        client_id="client1",
        description="Detected credential access outside business hours",
        details={"access_count": 5, "hour": 23},
        severity="warning"
    )
    
    # Test to_dict method returns correct dictionary representation
    anomaly_dict = anomaly.to_dict()
    assert anomaly_dict["anomaly_type"] == "after_hours_access"
    assert anomaly_dict["client_id"] == "client1"
    assert anomaly_dict["description"] == "Detected credential access outside business hours"
    assert anomaly_dict["severity"] == "warning"
    assert "timestamp" in anomaly_dict
    
    # Test to_alert method converts anomaly to proper alert format
    alert = anomaly.to_alert()
    assert alert["type"] == "credential_anomaly"
    assert alert["service_name"] == "credential-management"
    assert alert["metric_name"] == "after_hours_access"
    assert alert["severity"] == "warning"
    
    # Verify that timestamp is correctly formatted
    assert "timestamp" in alert
    
    # Verify that severity is correctly set
    assert alert["severity"] == "warning"
    
    # Create another anomaly with critical severity
    critical_anomaly = CredentialAnomaly(
        anomaly_type="high_frequency_access",
        client_id="client2",
        description="Unusual high frequency credential access",
        details={"access_count": 50, "normal_average": 10},
        severity="critical"
    )
    
    critical_alert = critical_anomaly.to_alert()
    assert critical_alert["severity"] == "critical"


def test_detect_credential_anomalies():
    """Tests the detect_credential_anomalies function for identifying unusual credential usage"""
    # Create sample credential metrics with normal patterns
    normal_metrics = {
        "service_name": "credential-management",
        "timestamp": "2023-06-15T12:00:00Z",
        "credential_access_count": 50,
        "credentials_by_client": {
            "client1": 25,
            "client2": 15,
            "client3": 10
        },
        "access_frequency": {
            "overall": 2.5,  # 2.5 accesses per minute
            "by_client": {
                "client1": 1.25,
                "client2": 0.75,
                "client3": 0.5
            }
        },
        "unusual_access_patterns": [],
        "credential_rotation": {
            "active": 3,
            "rotating": 0,
            "completed": 0,
            "failed": 0
        },
        "credentials_due_rotation": []
    }
    
    # Create sample credential metrics with anomalous patterns
    anomalous_metrics = {
        "service_name": "credential-management",
        "timestamp": "2023-06-15T12:00:00Z",
        "credential_access_count": 150,  # Unusually high
        "credentials_by_client": {
            "client1": 25,
            "client2": 15,
            "client3": 10,
            "suspicious_client": 100  # Suspicious activity
        },
        "access_frequency": {
            "overall": 7.5,  # 7.5 accesses per minute - high
            "by_client": {
                "client1": 1.25,
                "client2": 0.75,
                "client3": 0.5,
                "suspicious_client": 5.0  # High frequency
            }
        },
        "unusual_access_patterns": [
            {
                "type": "after_hours_access",
                "count": 25,
                "details": {
                    "after_hours_distribution": {
                        "22": 10,
                        "23": 15
                    }
                }
            },
            {
                "type": "high_frequency_access",
                "count": 1,
                "details": {
                    "high_frequency_clients": {
                        "suspicious_client": 5.0
                    }
                }
            }
        ],
        "credential_rotation": {
            "active": 2,
            "rotating": 0,
            "completed": 0,
            "failed": 1  # Failed rotation
        },
        "credential_rotation_failures": [
            {
                "client_id": "client3",
                "failure_reason": "Vault connectivity issue",
                "failure_time": 1623761445,
                "age_seconds": 3600
            }
        ],
        "credentials_due_rotation": [
            {
                "client_id": "client1",
                "age_seconds": 7776000,  # 90 days
                "days_until_rotation": 0
            }
        ]
    }
    
    # Call detect_credential_anomalies with normal metrics
    normal_anomalies = detect_credential_anomalies(normal_metrics)
    
    # Assert that no anomalies are detected
    assert len(normal_anomalies) == 0
    
    # Call detect_credential_anomalies with anomalous metrics
    anomalous_results = detect_credential_anomalies(anomalous_metrics)
    
    # Assert that anomalies are detected
    assert len(anomalous_results) > 0
    
    # Assert that detected anomalies have appropriate type and severity
    after_hours_anomaly = next((a for a in anomalous_results if a.anomaly_type == "after_hours_access"), None)
    assert after_hours_anomaly is not None
    
    high_freq_anomaly = next((a for a in anomalous_results if a.anomaly_type == "client_high_frequency"), None)
    assert high_freq_anomaly is not None
    
    rotation_failure_anomaly = next((a for a in anomalous_results if a.anomaly_type == "rotation_failure"), None)
    assert rotation_failure_anomaly is not None
    assert rotation_failure_anomaly.severity == "critical"
    
    rotation_due_anomaly = next((a for a in anomalous_results if a.anomaly_type == "rotation_due"), None)
    assert rotation_due_anomaly is not None
    assert rotation_due_anomaly.severity == "critical"  # 0 days until rotation


def test_check_credential_metrics_thresholds():
    """Tests the check_credential_metrics_thresholds function for threshold violations"""
    # Create sample credential metrics with some values exceeding thresholds
    metrics = {
        "service_name": "credential-management",
        "timestamp": "2023-06-15T12:00:00Z",
        "credential_access_count": 150,
        "access_frequency": {
            "overall": 15.0  # Exceeds threshold
        },
        "unusual_access_patterns": [
            {
                "type": "after_hours_access",
                "count": 20,  # Exceeds threshold
                "details": {}
            }
        ],
        "credential_rotation": {
            "failed": 2  # Failed rotations
        },
        "credential_rotation_failures": [
            {"client_id": "client1", "failure_reason": "Timeout"},
            {"client_id": "client2", "failure_reason": "Connection error"}
        ],
        "credentials_due_rotation": [
            {"client_id": "client3", "days_until_rotation": 2}
        ]
    }
    
    # Mock get_alert_threshold to return predefined threshold values
    with unittest.mock.patch('src.scripts.monitoring.credential_usage_metrics.get_alert_threshold') as mock_get_alert_threshold:
        # Configure mock to return appropriate thresholds
        def mock_get_threshold_side_effect(category, metric_name, severity):
            if category == "security" and metric_name == "credential_access_anomalies":
                return 50 if severity == "warning" else 100
            return 5 if severity == "warning" else 15  # Default thresholds
        
        mock_get_alert_threshold.side_effect = mock_get_threshold_side_effect
        
        # Call check_credential_metrics_thresholds with sample metrics
        alerts = check_credential_metrics_thresholds(metrics)
        
        # Assert that alerts are generated for metrics exceeding thresholds
        assert len(alerts) > 0
        
        # Assert that alerts have appropriate severity levels
        access_freq_alert = next((a for a in alerts if a["metric_name"] == "credential_access_frequency"), None)
        assert access_freq_alert is not None
        assert access_freq_alert["severity"] == "warning" or access_freq_alert["severity"] == "critical"
        
        # Assert that get_alert_threshold was called with correct parameters
        mock_get_alert_threshold.assert_any_call("security", "credential_access_anomalies", "warning")
        mock_get_alert_threshold.assert_any_call("security", "credential_access_anomalies", "critical")