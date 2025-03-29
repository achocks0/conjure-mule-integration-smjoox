#!/usr/bin/env python3
"""
Token Usage Metrics Monitoring Script

This script collects and analyzes JWT token usage metrics from Redis cache for the
Payment API Security Enhancement project. It tracks token generation rates, validation 
success/failure, expiration patterns, and client usage statistics to provide insights 
into authentication system health and performance.
"""

import argparse
import logging
import time
import datetime
import json
import sys
from redis import Redis  # version 4.3.4

# Internal imports
from config import (
    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL,
    CONNECTION_TIMEOUT, ALERT_THRESHOLDS, METRICS_COLLECTION_INTERVAL,
    get_alert_threshold, get_notification_channels_for_alert
)
from utils import (
    collect_token_metrics, check_metric_thresholds, generate_alert,
    send_alert_notification, log_metrics, calculate_sla_compliance,
    format_timestamp_iso
)

# Configure logger
logger = logging.getLogger(__name__)


def setup_argument_parser():
    """Sets up the command-line argument parser for the script
    
    Returns:
        argparse.ArgumentParser: Configured argument parser
    """
    parser = argparse.ArgumentParser(
        description='Monitor JWT token usage metrics from Redis cache'
    )
    
    # Add --config argument for specifying a custom config file
    parser.add_argument(
        '--config',
        help='Path to custom configuration file'
    )
    
    # Add --interval argument for specifying collection interval in seconds
    parser.add_argument(
        '--interval',
        type=int,
        help='Interval between metric collections in seconds (default: from config)'
    )
    
    # Add --output argument for specifying output file for metrics
    parser.add_argument(
        '--output',
        help='Output file path for storing metrics history'
    )
    
    # Add --verbose flag for enabling verbose output
    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Enable verbose output'
    )
    
    # Add --once flag for running the collection only once
    parser.add_argument(
        '--once',
        action='store_true',
        help='Run the collection only once and exit'
    )
    
    # Add --no-alerts flag for disabling alert generation
    parser.add_argument(
        '--no-alerts',
        action='store_true',
        help='Disable alert generation'
    )
    
    return parser


def analyze_token_metrics(metrics):
    """Analyzes token metrics to identify patterns and anomalies
    
    Args:
        metrics (dict): Metrics data to analyze
        
    Returns:
        dict: Analysis results with insights and potential issues
    """
    results = {
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "token_usage_summary": {},
        "client_usage_summary": {},
        "expiration_patterns": {},
        "cache_efficiency": {},
        "potential_issues": []
    }
    
    # Initialize token usage summary
    token_count = metrics.get("token_count", 0)
    active_tokens = metrics.get("active_tokens", 0)
    
    if token_count > 0:
        active_percentage = (active_tokens / token_count) * 100
    else:
        active_percentage = 0
    
    results["token_usage_summary"] = {
        "total_tokens": token_count,
        "active_tokens": active_tokens,
        "active_percentage": round(active_percentage, 2),
        "token_generation_rate": metrics.get("token_generation_rate", 0),
        "token_expiration_rate": metrics.get("token_expiration_rate", 0)
    }
    
    # Client usage patterns
    tokens_by_client = metrics.get("tokens_by_client", {})
    if tokens_by_client:
        top_clients = sorted(tokens_by_client.items(), key=lambda x: x[1], reverse=True)[:5]
        client_distribution = {}
        
        for client_id, token_count in top_clients:
            client_distribution[client_id] = token_count
            
        total_clients = len(tokens_by_client)
        avg_tokens_per_client = sum(tokens_by_client.values()) / total_clients if total_clients > 0 else 0
        
        results["client_usage_summary"] = {
            "total_clients": total_clients,
            "avg_tokens_per_client": round(avg_tokens_per_client, 2),
            "top_clients": client_distribution
        }
    
    # Analyze token expiration patterns
    if "token_expiration_rate" in metrics and "token_generation_rate" in metrics:
        results["expiration_patterns"] = {
            "generation_to_expiration_ratio": round(metrics["token_generation_rate"] / metrics["token_expiration_rate"], 2) 
                if metrics["token_expiration_rate"] > 0 else "∞",
            "estimated_token_lifetime": "Unknown"  # Would need more data to calculate
        }
    
    # Analyze cache efficiency
    if "redis_memory_used" in metrics or "redis_evicted_keys" in metrics:
        cache_data = {}
        if "redis_memory_used" in metrics:
            cache_data["memory_usage"] = metrics["redis_memory_used"]
        if "redis_evicted_keys" in metrics:
            cache_data["evicted_keys"] = metrics["redis_evicted_keys"]
        results["cache_efficiency"] = cache_data
    
    # Identify potential issues
    if active_percentage < 50 and token_count > 10:
        results["potential_issues"].append({
            "severity": "warning",
            "issue": "Low active token percentage",
            "description": f"Only {active_percentage:.2f}% of tokens are active, which might indicate token expiration issues"
        })
    
    if "token_generation_rate" in metrics and "token_expiration_rate" in metrics:
        gen_rate = metrics["token_generation_rate"]
        exp_rate = metrics["token_expiration_rate"]
        
        if gen_rate > 0 and exp_rate == 0:
            results["potential_issues"].append({
                "severity": "warning",
                "issue": "No token expirations",
                "description": "Tokens are being generated but none are expiring, which might indicate token expiration configuration issues"
            })
            
        if gen_rate > exp_rate * 3 and gen_rate > 10:
            results["potential_issues"].append({
                "severity": "warning",
                "issue": "Token accumulation",
                "description": f"Token generation rate ({gen_rate}/min) significantly exceeds expiration rate ({exp_rate}/min), which might lead to token accumulation"
            })
    
    # Redis cache efficiency
    if "redis_evicted_keys" in metrics and metrics["redis_evicted_keys"] > 0:
        results["potential_issues"].append({
            "severity": "warning",
            "issue": "Redis key evictions",
            "description": f"Redis has evicted {metrics['redis_evicted_keys']} keys, which might affect token validation"
        })
    
    return results


def calculate_token_usage_trends(historical_metrics, current_metrics):
    """Calculates trends in token usage based on historical data
    
    Args:
        historical_metrics (list): List of historical metrics data
        current_metrics (dict): Current metrics data
        
    Returns:
        dict: Token usage trends and patterns
    """
    trends = {
        "timestamp": format_timestamp_iso(datetime.datetime.now()),
        "token_generation_trend": "stable",
        "active_tokens_trend": "stable",
        "client_usage_trend": "stable",
        "change_rates": {},
        "trend_details": {}
    }
    
    # Need at least one historical data point for comparison
    if not historical_metrics:
        return trends
    
    # Get the most recent historical metrics
    previous_metrics = historical_metrics[-1]
    
    # Token generation rate trend
    current_gen_rate = current_metrics.get("token_generation_rate", 0)
    previous_gen_rate = previous_metrics.get("token_generation_rate", 0)
    
    if previous_gen_rate > 0:
        gen_rate_change = ((current_gen_rate - previous_gen_rate) / previous_gen_rate) * 100
        trends["change_rates"]["token_generation_rate"] = round(gen_rate_change, 2)
        
        if gen_rate_change > 30:
            trends["token_generation_trend"] = "increasing_rapidly"
        elif gen_rate_change > 10:
            trends["token_generation_trend"] = "increasing"
        elif gen_rate_change < -30:
            trends["token_generation_trend"] = "decreasing_rapidly"
        elif gen_rate_change < -10:
            trends["token_generation_trend"] = "decreasing"
        else:
            trends["token_generation_trend"] = "stable"
    
    # Active tokens trend
    current_active = current_metrics.get("active_tokens", 0)
    previous_active = previous_metrics.get("active_tokens", 0)
    
    if previous_active > 0:
        active_change = ((current_active - previous_active) / previous_active) * 100
        trends["change_rates"]["active_tokens"] = round(active_change, 2)
        
        if active_change > 30:
            trends["active_tokens_trend"] = "increasing_rapidly"
        elif active_change > 10:
            trends["active_tokens_trend"] = "increasing"
        elif active_change < -30:
            trends["active_tokens_trend"] = "decreasing_rapidly"
        elif active_change < -10:
            trends["active_tokens_trend"] = "decreasing"
        else:
            trends["active_tokens_trend"] = "stable"
    
    # Client usage trend
    current_clients = len(current_metrics.get("tokens_by_client", {}))
    previous_clients = len(previous_metrics.get("tokens_by_client", {}))
    
    if previous_clients > 0:
        client_change = ((current_clients - previous_clients) / previous_clients) * 100
        trends["change_rates"]["active_clients"] = round(client_change, 2)
        
        if client_change > 30:
            trends["client_usage_trend"] = "increasing_rapidly"
        elif client_change > 10:
            trends["client_usage_trend"] = "increasing"
        elif client_change < -30:
            trends["client_usage_trend"] = "decreasing_rapidly"
        elif client_change < -10:
            trends["client_usage_trend"] = "decreasing"
        else:
            trends["client_usage_trend"] = "stable"
    
    # Add trend details
    trends["trend_details"] = {
        "current_generation_rate": current_gen_rate,
        "previous_generation_rate": previous_gen_rate,
        "current_active_tokens": current_active,
        "previous_active_tokens": previous_active,
        "current_active_clients": current_clients,
        "previous_active_clients": previous_clients
    }
    
    return trends


def detect_token_anomalies(metrics, historical_metrics):
    """Detects anomalies in token metrics that might indicate security issues
    
    Args:
        metrics (dict): Current metrics data
        historical_metrics (list): List of historical metrics data
        
    Returns:
        list: List of detected anomalies with severity and description
    """
    anomalies = []
    
    # Need historical data for anomaly detection
    if not historical_metrics or len(historical_metrics) < 3:
        return anomalies
    
    # Calculate average values from historical data
    avg_token_count = sum(m.get("token_count", 0) for m in historical_metrics) / len(historical_metrics)
    avg_active_tokens = sum(m.get("active_tokens", 0) for m in historical_metrics) / len(historical_metrics)
    avg_gen_rate = sum(m.get("token_generation_rate", 0) for m in historical_metrics) / len(historical_metrics)
    avg_exp_rate = sum(m.get("token_expiration_rate", 0) for m in historical_metrics) / len(historical_metrics)
    
    # Get current values
    current_token_count = metrics.get("token_count", 0)
    current_active_tokens = metrics.get("active_tokens", 0)
    current_gen_rate = metrics.get("token_generation_rate", 0)
    current_exp_rate = metrics.get("token_expiration_rate", 0)
    
    # Check for sudden spike in token generation rate
    if avg_gen_rate > 0 and current_gen_rate > avg_gen_rate * 3:
        anomalies.append({
            "severity": "high",
            "type": "token_generation_spike",
            "description": f"Sudden spike in token generation rate (current: {current_gen_rate}/min, avg: {avg_gen_rate:.2f}/min)",
            "current_value": current_gen_rate,
            "avg_value": avg_gen_rate
        })
    
    # Check for unusual drop in token expiration rate
    if avg_exp_rate > 5 and current_exp_rate < avg_exp_rate * 0.3:
        anomalies.append({
            "severity": "medium",
            "type": "token_expiration_drop",
            "description": f"Unusual drop in token expiration rate (current: {current_exp_rate}/min, avg: {avg_exp_rate:.2f}/min)",
            "current_value": current_exp_rate,
            "avg_value": avg_exp_rate
        })
    
    # Check for unusual accumulation of tokens
    if avg_token_count > 0 and current_token_count > avg_token_count * 2:
        anomalies.append({
            "severity": "medium",
            "type": "token_accumulation",
            "description": f"Unusual accumulation of tokens (current: {current_token_count}, avg: {avg_token_count:.2f})",
            "current_value": current_token_count,
            "avg_value": avg_token_count
        })
    
    # Check for unusual client activity
    current_clients = metrics.get("tokens_by_client", {})
    
    # Aggregate historical client activity
    historical_clients = {}
    for m in historical_metrics:
        for client_id, count in m.get("tokens_by_client", {}).items():
            if client_id in historical_clients:
                historical_clients[client_id] += count
            else:
                historical_clients[client_id] = count
    
    # Normalize historical data
    for client_id in historical_clients:
        historical_clients[client_id] /= len(historical_metrics)
    
    # Check for new clients with high activity
    for client_id, count in current_clients.items():
        avg_count = historical_clients.get(client_id, 0)
        
        # New client with high activity
        if avg_count == 0 and count > 10:
            anomalies.append({
                "severity": "medium",
                "type": "new_client_high_activity",
                "description": f"New client {client_id} with unusually high token count ({count})",
                "client_id": client_id,
                "current_value": count
            })
        # Existing client with unusual activity
        elif avg_count > 5 and count > avg_count * 3:
            anomalies.append({
                "severity": "medium",
                "type": "client_activity_spike",
                "description": f"Client {client_id} has unusual token activity (current: {count}, avg: {avg_count:.2f})",
                "client_id": client_id,
                "current_value": count,
                "avg_value": avg_count
            })
    
    # Check for tokens being generated but not used
    if current_gen_rate > 5 and current_active_tokens / current_token_count < 0.3:
        anomalies.append({
            "severity": "medium",
            "type": "unused_tokens",
            "description": f"Tokens are being generated but not used (active: {current_active_tokens}, total: {current_token_count})",
            "current_value": current_active_tokens / current_token_count if current_token_count > 0 else 0
        })
    
    return anomalies


def store_metrics_history(metrics, output_file):
    """Stores token metrics history for trend analysis
    
    Args:
        metrics (dict): Metrics data to store
        output_file (str): Path to output file
        
    Returns:
        bool: True if metrics were stored successfully, False otherwise
    """
    if output_file is None:
        return False
    
    try:
        with open(output_file, 'a') as f:
            # Add timestamp if not present
            if "timestamp" not in metrics:
                metrics["timestamp"] = format_timestamp_iso(datetime.datetime.now())
                
            # Write metrics as JSON
            f.write(json.dumps(metrics) + '\n')
            
        logger.info(f"Stored metrics history to {output_file}")
        return True
        
    except Exception as e:
        logger.error(f"Error storing metrics history: {str(e)}", exc_info=True)
        return False


def load_metrics_history(input_file, max_entries=None):
    """Loads historical token metrics for trend analysis
    
    Args:
        input_file (str): Path to input file
        max_entries (int, optional): Maximum number of entries to load
        
    Returns:
        list: List of historical metrics entries
    """
    historical_metrics = []
    
    if input_file is None:
        return historical_metrics
    
    try:
        with open(input_file, 'r') as f:
            for line in f:
                try:
                    metrics = json.loads(line.strip())
                    historical_metrics.append(metrics)
                except json.JSONDecodeError:
                    continue
        
        # Limit to max_entries if specified
        if max_entries is not None and len(historical_metrics) > max_entries:
            historical_metrics = historical_metrics[-max_entries:]
            
        logger.info(f"Loaded {len(historical_metrics)} historical metrics entries from {input_file}")
        
    except FileNotFoundError:
        logger.warning(f"Metrics history file not found: {input_file}")
        
    except Exception as e:
        logger.error(f"Error loading metrics history: {str(e)}", exc_info=True)
    
    return historical_metrics


def generate_token_metrics_report(metrics, analysis, anomalies):
    """Generates a comprehensive report of token metrics and analysis
    
    Args:
        metrics (dict): Raw metrics data
        analysis (dict): Analysis results
        anomalies (list): Detected anomalies
        
    Returns:
        str: Formatted report as string
    """
    timestamp = metrics.get("timestamp", format_timestamp_iso(datetime.datetime.now()))
    
    # Parse timestamp if it's a string
    if isinstance(timestamp, str):
        try:
            timestamp_dt = datetime.datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            timestamp_str = timestamp_dt.strftime("%Y-%m-%d %H:%M:%S UTC")
        except ValueError:
            timestamp_str = timestamp
    else:
        timestamp_str = timestamp.strftime("%Y-%m-%d %H:%M:%S UTC")
    
    # Format report header
    report = [
        "=" * 80,
        f" TOKEN METRICS REPORT - {timestamp_str}",
        "=" * 80,
        ""
    ]
    
    # Summary section
    report.extend([
        "SUMMARY",
        "-------",
        f"Total Tokens:           {metrics.get('token_count', 0)}",
        f"Active Tokens:          {metrics.get('active_tokens', 0)}",
        f"Token Generation Rate:  {metrics.get('token_generation_rate', 0)} tokens/minute",
        f"Token Expiration Rate:  {metrics.get('token_expiration_rate', 0)} tokens/minute",
        f"Active Clients:         {len(metrics.get('tokens_by_client', {}))}",
        ""
    ])
    
    # Trends section from analysis
    if "token_usage_summary" in analysis:
        summary = analysis["token_usage_summary"]
        report.extend([
            "TOKEN USAGE",
            "----------",
            f"Active Percentage:      {summary.get('active_percentage', 0)}%",
            ""
        ])
    
    if "client_usage_summary" in analysis:
        client_summary = analysis["client_usage_summary"]
        report.extend([
            "CLIENT USAGE",
            "------------",
            f"Total Clients:          {client_summary.get('total_clients', 0)}",
            f"Avg Tokens per Client:  {client_summary.get('avg_tokens_per_client', 0)}",
            ""
        ])
        
        # Top clients section
        if "top_clients" in client_summary and client_summary["top_clients"]:
            report.append("Top Clients:")
            for client_id, token_count in client_summary["top_clients"].items():
                report.append(f"  {client_id}: {token_count} tokens")
            report.append("")
    
    # Anomalies section
    if anomalies:
        report.extend([
            "ANOMALIES DETECTED",
            "------------------"
        ])
        
        for anomaly in anomalies:
            severity = anomaly.get("severity", "medium").upper()
            anomaly_type = anomaly.get("type", "unknown")
            description = anomaly.get("description", "No description")
            
            report.append(f"[{severity}] {anomaly_type}: {description}")
        
        report.append("")
    
    # Potential issues section from analysis
    if "potential_issues" in analysis and analysis["potential_issues"]:
        report.extend([
            "POTENTIAL ISSUES",
            "----------------"
        ])
        
        for issue in analysis["potential_issues"]:
            severity = issue.get("severity", "medium").upper()
            issue_name = issue.get("issue", "unknown")
            description = issue.get("description", "No description")
            
            report.append(f"[{severity}] {issue_name}: {description}")
        
        report.append("")
    
    # Cache efficiency section
    if "cache_efficiency" in analysis:
        cache = analysis["cache_efficiency"]
        report.extend([
            "REDIS CACHE STATUS",
            "-----------------",
            f"Memory Used:            {cache.get('memory_usage', 'N/A')}",
            f"Evicted Keys:           {cache.get('evicted_keys', 'N/A')}",
            ""
        ])
    
    # Detailed metrics section
    report.extend([
        "DETAILED METRICS",
        "----------------"
    ])
    
    # Add client token distribution
    tokens_by_client = metrics.get("tokens_by_client", {})
    if tokens_by_client:
        report.append("Token Distribution by Client:")
        for client_id, token_count in sorted(tokens_by_client.items(), key=lambda x: x[1], reverse=True):
            report.append(f"  {client_id}: {token_count} tokens")
    
    report.append("")
    
    # SLA compliance section
    token_validation_success_rate = metrics.get("token_validation_success_rate", 100)
    token_generation_time = metrics.get("token_generation_time", 0)
    
    if token_validation_success_rate is not None or token_generation_time is not None:
        report.extend([
            "SLA COMPLIANCE",
            "--------------"
        ])
        
        if token_validation_success_rate is not None:
            validation_sla = calculate_sla_compliance("authentication_success_rate", token_validation_success_rate)
            report.append(f"Token Validation Success: {token_validation_success_rate}% (Target: {validation_sla['target']}%, Compliance: {validation_sla['compliance']}%)")
        
        if token_generation_time is not None:
            generation_sla = calculate_sla_compliance("token_generation_time", token_generation_time)
            report.append(f"Token Generation Time:  {token_generation_time}ms (Target: {generation_sla['target']}ms, Compliance: {generation_sla['compliance']}%)")
        
        report.append("")
    
    # End of report
    report.extend([
        "=" * 80,
        " END OF REPORT",
        "=" * 80
    ])
    
    return "\n".join(report)


def run_token_metrics_collection(interval, output_file, run_once=False, generate_alerts=True):
    """Main function to run the token metrics collection process
    
    Args:
        interval (int): Interval between collections in seconds
        output_file (str): Path to output file for metrics history
        run_once (bool): Whether to run collection only once
        generate_alerts (bool): Whether to generate alerts for threshold violations
        
    Returns:
        int: Exit code (0 for success, non-zero for errors)
    """
    logger.info(f"Starting token metrics collection (interval: {interval}s, output: {output_file})")
    
    # Initialize historical metrics
    historical_metrics = []
    
    # Load historical metrics if output file exists
    if output_file:
        historical_metrics = load_metrics_history(output_file, max_entries=24)  # Keep last 24 entries
    
    try:
        while True:
            # Collect token metrics
            try:
                metrics = collect_token_metrics(
                    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_SSL, 
                    timeout=CONNECTION_TIMEOUT
                )
                
                if "error" in metrics:
                    logger.error(f"Error collecting token metrics: {metrics.get('error')}")
                    time.sleep(interval)
                    continue
                
            except Exception as e:
                logger.error(f"Failed to collect token metrics: {str(e)}", exc_info=True)
                time.sleep(interval)
                continue
            
            # Analyze token metrics
            analysis = analyze_token_metrics(metrics)
            
            # Detect anomalies
            anomalies = detect_token_anomalies(metrics, historical_metrics)
            
            # Generate and log report
            report = generate_token_metrics_report(metrics, analysis, anomalies)
            logger.info("\n" + report)
            
            # Check for threshold violations and generate alerts
            if generate_alerts:
                # Security alerts
                security_alerts = check_metric_thresholds(metrics, "security")
                
                # Performance alerts
                performance_alerts = check_metric_thresholds(metrics, "performance")
                
                # Availability alerts
                availability_alerts = check_metric_thresholds(metrics, "availability")
                
                # Process anomalies as alerts
                for anomaly in anomalies:
                    severity = "critical" if anomaly.get("severity") == "high" else "warning"
                    alert = generate_alert(
                        "security",
                        "token-service",
                        anomaly.get("type", "token_anomaly"),
                        severity,
                        anomaly.get("current_value", 0),
                        anomaly.get("avg_value", 0) if "avg_value" in anomaly else None,
                        {"description": anomaly.get("description"), "client_id": anomaly.get("client_id")}
                    )
                    security_alerts.append(alert)
                
                # Send alerts
                all_alerts = security_alerts + performance_alerts + availability_alerts
                for alert in all_alerts:
                    try:
                        send_alert_notification(alert)
                    except Exception as e:
                        logger.error(f"Failed to send alert notification: {str(e)}", exc_info=True)
            
            # Store metrics history
            if output_file:
                store_metrics_history(metrics, output_file)
            
            # Add to historical metrics
            historical_metrics.append(metrics)
            
            # Keep last 24 entries (assuming hourly collection)
            if len(historical_metrics) > 24:
                historical_metrics = historical_metrics[-24:]
            
            # Exit if run_once is True
            if run_once:
                break
            
            # Sleep until next collection
            time.sleep(interval)
            
    except KeyboardInterrupt:
        logger.info("Token metrics collection stopped by user")
        return 0
    except Exception as e:
        logger.error(f"Unexpected error in token metrics collection: {str(e)}", exc_info=True)
        return 1
    
    logger.info("Token metrics collection completed")
    return 0


def main():
    """Entry point for the script
    
    Returns:
        int: Exit code (0 for success, non-zero for errors)
    """
    # Set up argument parser
    parser = setup_argument_parser()
    
    # Parse command-line arguments
    args = parser.parse_args()
    
    # Configure logging based on verbosity
    log_level = logging.DEBUG if args.verbose else logging.INFO
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    
    # Determine collection interval
    interval = args.interval if args.interval is not None else METRICS_COLLECTION_INTERVAL
    
    # Get output file path from arguments
    output_file = args.output
    
    # Get run_once flag from arguments
    run_once = args.once
    
    # Get generate_alerts flag from arguments
    generate_alerts = not args.no_alerts
    
    # Run token metrics collection with specified parameters
    return run_token_metrics_collection(
        interval=interval,
        output_file=output_file,
        run_once=run_once,
        generate_alerts=generate_alerts
    )


if __name__ == "__main__":
    sys.exit(main())