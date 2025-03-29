#!/usr/bin/env python3
"""
Health Check Script for Payment API Security Enhancement

This script performs health checks on all components of the Payment API Security
Enhancement system, including Payment-EAPI, Payment-SAPI, Conjur Vault, and Redis Cache.
It monitors service availability, response times, and generates alerts for any detected issues.
"""

import argparse
import logging
import time
import json
import datetime
import requests

from . import config
from .config import (
    PAYMENT_EAPI_URL, PAYMENT_SAPI_URL, CONJUR_VAULT_URL,
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL,
    CONNECTION_TIMEOUT, READ_TIMEOUT, HEALTH_CHECK_INTERVAL,
    HEALTH_CHECK_ENDPOINTS, ALERT_THRESHOLDS, SLA_TARGETS
)
from .utils import (
    check_service_health, check_redis_health, generate_alert,
    send_alert_notification, calculate_sla_compliance, ServiceHealthError
)

# Configure logger
logger = logging.getLogger(__name__)


def check_all_services_health():
    """
    Checks the health of all services in the Payment API Security Enhancement system
    
    Returns:
        dict: Dictionary containing health check results for all services
    """
    logger.info("Starting health check for all services")
    
    # Initialize empty dictionary to store health check results
    results = {
        "timestamp": datetime.datetime.now().isoformat(),
        "overall_status": "healthy",  # Default to healthy unless any service is unhealthy
        "services": {}
    }
    
    # Check Payment-EAPI health using check_service_health function
    try:
        eapi_result = check_service_health(
            "payment-eapi",
            PAYMENT_EAPI_URL,
            HEALTH_CHECK_ENDPOINTS["payment-eapi"],
            CONNECTION_TIMEOUT
        )
        results["services"]["payment-eapi"] = eapi_result
    except Exception as e:
        logger.error(f"Error checking Payment-EAPI health: {str(e)}")
        results["services"]["payment-eapi"] = {
            "service_name": "payment-eapi",
            "timestamp": datetime.datetime.now().isoformat(),
            "status": "unhealthy",
            "response_time_ms": None,
            "details": {"error": str(e)}
        }
    
    # Check Payment-SAPI health using check_service_health function
    try:
        sapi_result = check_service_health(
            "payment-sapi",
            PAYMENT_SAPI_URL,
            HEALTH_CHECK_ENDPOINTS["payment-sapi"],
            CONNECTION_TIMEOUT
        )
        results["services"]["payment-sapi"] = sapi_result
    except Exception as e:
        logger.error(f"Error checking Payment-SAPI health: {str(e)}")
        results["services"]["payment-sapi"] = {
            "service_name": "payment-sapi",
            "timestamp": datetime.datetime.now().isoformat(),
            "status": "unhealthy",
            "response_time_ms": None,
            "details": {"error": str(e)}
        }
    
    # Check Conjur Vault health using check_service_health function
    try:
        conjur_result = check_service_health(
            "conjur-vault",
            CONJUR_VAULT_URL,
            HEALTH_CHECK_ENDPOINTS["conjur-vault"],
            CONNECTION_TIMEOUT
        )
        results["services"]["conjur-vault"] = conjur_result
    except Exception as e:
        logger.error(f"Error checking Conjur Vault health: {str(e)}")
        results["services"]["conjur-vault"] = {
            "service_name": "conjur-vault",
            "timestamp": datetime.datetime.now().isoformat(),
            "status": "unhealthy",
            "response_time_ms": None,
            "details": {"error": str(e)}
        }
    
    # Check Redis Cache health using check_redis_health function
    try:
        redis_result = check_redis_health(
            REDIS_HOST,
            REDIS_PORT,
            REDIS_PASSWORD,
            REDIS_SSL,
            CONNECTION_TIMEOUT
        )
        results["services"]["redis-cache"] = redis_result
    except Exception as e:
        logger.error(f"Error checking Redis Cache health: {str(e)}")
        results["services"]["redis-cache"] = {
            "service_name": "redis-cache",
            "timestamp": datetime.datetime.now().isoformat(),
            "status": "unhealthy",
            "response_time_ms": None,
            "details": {"error": str(e)}
        }
    
    # Calculate overall system health status based on individual service statuses
    for service_name, service_result in results["services"].items():
        if service_result["status"] != "healthy":
            results["overall_status"] = "unhealthy"
            break
    
    # Log summary of health check results
    logger.info(f"Health check completed. Overall status: {results['overall_status']}")
    for service_name, service_result in results["services"].items():
        status = service_result["status"]
        response_time = service_result.get("response_time_ms", "N/A")
        logger.info(f"  {service_name}: {status} (response time: {response_time}ms)")
    
    return results


def analyze_health_results(health_results):
    """
    Analyzes health check results to identify issues and calculate availability metrics
    
    Args:
        health_results (dict): Results from health check
    
    Returns:
        dict: Analyzed health data with availability metrics
    """
    logger.info("Analyzing health check results")
    
    # Initialize dictionary for analyzed health data
    analysis = {
        "timestamp": health_results.get("timestamp", datetime.datetime.now().isoformat()),
        "overall_status": health_results.get("overall_status", "unknown"),
        "availability": {},
        "response_times": {},
        "issues": []
    }
    
    # Calculate availability percentage for each service
    for service_name, service_data in health_results.get("services", {}).items():
        # Calculate availability (1 for healthy, 0 for unhealthy)
        availability = 1 if service_data.get("status") == "healthy" else 0
        analysis["availability"][service_name] = availability * 100  # Convert to percentage
        
        # Calculate average response time for each service
        response_time = service_data.get("response_time_ms")
        if response_time is not None:
            analysis["response_times"][service_name] = response_time
        
        # Identify services with response times exceeding thresholds
        if service_data.get("status") != "healthy":
            issue = {
                "service_name": service_name,
                "issue_type": "availability",
                "details": service_data.get("details", {})
            }
            analysis["issues"].append(issue)
        
        # Identify unhealthy services
        if response_time is not None:
            warning_threshold = None
            critical_threshold = None
            
            # Determine appropriate thresholds based on service name
            if service_name == "payment-eapi":
                warning_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["warning"]
                critical_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["critical"]
            elif service_name == "payment-sapi":
                warning_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["warning"]
                critical_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["critical"]
            elif service_name == "conjur-vault":
                warning_threshold = ALERT_THRESHOLDS["performance"]["conjur_vault_response_time"]["warning"]
                critical_threshold = ALERT_THRESHOLDS["performance"]["conjur_vault_response_time"]["critical"]
            
            if critical_threshold and response_time > critical_threshold:
                issue = {
                    "service_name": service_name,
                    "issue_type": "performance",
                    "severity": "critical",
                    "metric": "response_time",
                    "value": response_time,
                    "threshold": critical_threshold
                }
                analysis["issues"].append(issue)
            elif warning_threshold and response_time > warning_threshold:
                issue = {
                    "service_name": service_name,
                    "issue_type": "performance",
                    "severity": "warning",
                    "metric": "response_time",
                    "value": response_time,
                    "threshold": warning_threshold
                }
                analysis["issues"].append(issue)
    
    # Add all calculated metrics to the analyzed data dictionary
    logger.info(f"Analysis completed. Found {len(analysis['issues'])} issues.")
    
    return analysis


def generate_health_alerts(health_results):
    """
    Generates alerts for unhealthy services or performance issues
    
    Args:
        health_results (dict): Results from health check
    
    Returns:
        list: List of generated alerts
    """
    logger.info("Generating alerts based on health check results")
    alerts = []
    
    # Initialize empty list to store alerts
    for service_name, service_data in health_results.get("services", {}).items():
        # If service status is 'unhealthy':
        if service_data.get("status") != "healthy":
            # Generate critical alert using generate_alert function
            alert = generate_alert(
                "availability",
                service_name,
                "service_status",
                "critical",
                "unhealthy",
                "healthy",
                service_data.get("details", {})
            )
            # Add alert to alerts list
            alerts.append(alert)
            logger.info(f"Generated critical availability alert for {service_name}")
        
        # If service response time exceeds critical threshold:
        response_time = service_data.get("response_time_ms")
        if response_time is not None:
            warning_threshold = None
            critical_threshold = None
            
            # Determine appropriate thresholds based on service name
            if service_name == "payment-eapi" or service_name == "payment-sapi":
                warning_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["warning"]
                critical_threshold = ALERT_THRESHOLDS["performance"]["api_response_time"]["critical"]
            elif service_name == "conjur-vault":
                warning_threshold = ALERT_THRESHOLDS["performance"]["conjur_vault_response_time"]["warning"]
                critical_threshold = ALERT_THRESHOLDS["performance"]["conjur_vault_response_time"]["critical"]
            
            if critical_threshold and response_time > critical_threshold:
                # Generate critical alert for response time
                alert = generate_alert(
                    "performance",
                    service_name,
                    "response_time",
                    "critical",
                    response_time,
                    critical_threshold
                )
                # Add alert to alerts list
                alerts.append(alert)
                logger.info(f"Generated critical performance alert for {service_name}: response time {response_time}ms > {critical_threshold}ms")
            # If service response time exceeds warning threshold:
            elif warning_threshold and response_time > warning_threshold:
                # Generate warning alert for response time
                alert = generate_alert(
                    "performance",
                    service_name,
                    "response_time",
                    "warning",
                    response_time,
                    warning_threshold
                )
                # Add alert to alerts list
                alerts.append(alert)
                logger.info(f"Generated warning performance alert for {service_name}: response time {response_time}ms > {warning_threshold}ms")
    
    logger.info(f"Generated {len(alerts)} alerts")
    return alerts


def report_health_status(health_results):
    """
    Reports health status to a central monitoring endpoint
    
    Args:
        health_results (dict): Results from health check
    
    Returns:
        bool: True if report was sent successfully, False otherwise
    """
    logger.info("Reporting health status to monitoring endpoint")
    
    # Format health results as JSON
    report_data = {
        "timestamp": datetime.datetime.now().isoformat(),
        "source": "health_check.py",
        "health_results": health_results
    }
    
    # Add timestamp to the report
    monitoring_endpoint = getattr(config, "ALERT_ENDPOINT", None)
    
    if not monitoring_endpoint:
        logger.warning("No monitoring endpoint configured, skipping report")
        return False
    
    # Try to send report to configured monitoring endpoint using requests
    try:
        response = requests.post(
            monitoring_endpoint,
            json=report_data,
            timeout=10
        )
        
        # If request is successful, log success and return True
        if response.status_code == 200:
            logger.info("Health status report sent successfully")
            return True
        # If request fails, log error and return False
        else:
            logger.warning(f"Failed to send health status report: HTTP {response.status_code} - {response.text}")
            return False
            
    except Exception as e:
        logger.error(f"Error sending health status report: {str(e)}", exc_info=True)
        return False


def calculate_availability_sla(health_results):
    """
    Calculates SLA compliance for service availability
    
    Args:
        health_results (dict): Results from health check
    
    Returns:
        dict: SLA compliance data for availability
    """
    logger.info("Calculating availability SLA compliance")
    
    # Initialize dictionary for SLA compliance data
    sla_data = {
        "timestamp": health_results.get("timestamp", datetime.datetime.now().isoformat()),
        "services": {},
        "overall": {}
    }
    
    # For each service in health results:
    overall_availability = 0
    service_count = 0
    
    for service_name, service_data in health_results.get("services", {}).items():
        # Calculate availability percentage
        availability = 1 if service_data.get("status") == "healthy" else 0
        availability_percentage = availability * 100  # Convert to percentage
        
        # Use calculate_sla_compliance function to determine SLA compliance
        sla_compliance = calculate_sla_compliance(
            "availability",
            availability_percentage,
            SLA_TARGETS["availability"]
        )
        
        # Add service SLA compliance to the compliance dictionary
        sla_data["services"][service_name] = sla_compliance
        
        # Track for overall calculation
        overall_availability += availability_percentage
        service_count += 1
    
    # Calculate overall system SLA compliance
    if service_count > 0:
        overall_availability_percentage = overall_availability / service_count
        overall_sla_compliance = calculate_sla_compliance(
            "availability",
            overall_availability_percentage,
            SLA_TARGETS["availability"]
        )
        sla_data["overall"] = overall_sla_compliance
    
    logger.info(f"Overall availability SLA compliance: {sla_data['overall'].get('compliance', 'N/A')}%")
    
    return sla_data


def run_health_checks(interval=60, single_run=False):
    """
    Runs the health check process at specified intervals
    
    Args:
        interval (int): Interval between health checks in seconds
        single_run (bool): If True, runs only once
    
    Returns:
        None
    """
    logger.info(f"Starting health check monitoring (interval: {interval}s, single run: {single_run})")
    
    # If single_run is True, run one iteration of health checks
    if single_run:
        health_results = check_all_services_health()
        analysis = analyze_health_results(health_results)
        alerts = generate_health_alerts(health_results)
        for alert in alerts:
            send_alert_notification(alert)
        report_health_status(health_results)
        calculate_availability_sla(health_results)
        logger.info("Single run completed")
        return
    
    # Otherwise, enter infinite loop:
    try:
        while True:
            # Check all services health using check_all_services_health function
            health_results = check_all_services_health()
            
            # Analyze health results using analyze_health_results function
            analysis = analyze_health_results(health_results)
            
            # Generate alerts using generate_health_alerts function
            alerts = generate_health_alerts(health_results)
            
            # Send alerts using send_alert_notification function for each alert
            for alert in alerts:
                try:
                    send_alert_notification(alert)
                except Exception as e:
                    logger.error(f"Failed to send alert notification: {str(e)}", exc_info=True)
            
            # Report health status using report_health_status function
            report_health_status(health_results)
            
            # Calculate SLA compliance using calculate_availability_sla function
            sla_data = calculate_availability_sla(health_results)
            
            # Log health check results
            status = health_results.get("overall_status", "unknown")
            issues = len(analysis.get("issues", []))
            alerts_count = len(alerts)
            logger.info(f"Health check cycle completed. Status: {status}, Issues: {issues}, Alerts: {alerts_count}")
            
            # Sleep for specified interval
            logger.debug(f"Sleeping for {interval} seconds until next check")
            time.sleep(interval)
            
    # Handle keyboard interrupt to allow clean exit
    except KeyboardInterrupt:
        logger.info("Health check monitoring stopped by user")
    except Exception as e:
        logger.error(f"Unexpected error in health check monitoring: {str(e)}", exc_info=True)
        raise
        
    logger.info("Health check monitoring ended")


def parse_arguments():
    """
    Parses command line arguments for the script
    
    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    # Create ArgumentParser instance with description
    parser = argparse.ArgumentParser(
        description="Health check script for Payment API Security Enhancement system"
    )
    
    # Add --interval argument to specify health check interval in seconds
    parser.add_argument(
        "--interval",
        type=int,
        default=HEALTH_CHECK_INTERVAL,
        help=f"Interval between health checks in seconds (default: {HEALTH_CHECK_INTERVAL})"
    )
    
    # Add --single-run flag to run health check once and exit
    parser.add_argument(
        "--single-run",
        action="store_true",
        help="Run health check once and exit"
    )
    
    # Add --verbose flag to enable verbose logging
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Enable verbose logging"
    )
    
    # Parse and return command line arguments
    return parser.parse_args()


def setup_logging(verbose=False):
    """
    Configures logging for the health check script
    
    Args:
        verbose (bool): If True, enables verbose (DEBUG) logging
    
    Returns:
        None
    """
    # Configure root logger with appropriate format
    log_level = logging.DEBUG if verbose else logging.INFO
    
    logging.basicConfig(
        level=log_level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    
    # Set level for our logger
    logger.setLevel(log_level)
    
    # Add console handler to logger
    logger.info("Logging configured")


def main():
    """
    Main function that orchestrates the health check process
    
    Returns:
        int: Exit code (0 for success, non-zero for errors)
    """
    # Parse command line arguments using parse_arguments function
    args = parse_arguments()
    
    # Setup logging using setup_logging function
    setup_logging(args.verbose)
    
    # Log script startup information
    logger.info("Health Check Script for Payment API Security Enhancement")
    logger.info(f"Interval: {args.interval}s, Single run: {args.single_run}, Verbose: {args.verbose}")
    
    # Try to run health checks with specified parameters
    try:
        run_health_checks(args.interval, args.single_run)
        return 0
    # Catch and log any exceptions
    except Exception as e:
        logger.error(f"Error running health checks: {str(e)}", exc_info=True)
        return 1


if __name__ == "__main__":
    # Call main function
    exit_code = main()
    # Exit with return code from main function
    exit(exit_code)