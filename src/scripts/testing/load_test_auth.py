#!/usr/bin/env python3
"""
Load testing script for the Payment API Security Enhancement project.

This script simulates concurrent authentication requests to measure performance,
throughput, and stability of the authentication mechanism under load.
"""

import argparse
import sys
import os
import json
import time
import uuid
from datetime import datetime
import statistics
import concurrent.futures
import requests
import matplotlib.pyplot as plt
import numpy as np

# Import local modules
from .config import (
    LOGGER,
    PerformanceTestConfig,
    setup_logging,
    get_performance_test_config,
    generate_test_report_path,
    PERFORMANCE_TEST_THRESHOLDS
)
from .utils import (
    TestRunner,
    TestResult,
    authenticate_client,
    create_http_session,
    save_test_results,
    generate_test_report
)

# Default configuration values
DEFAULT_CONFIG_FILE = os.environ.get('PERF_CONFIG_FILE', None)
DEFAULT_ENVIRONMENT = os.environ.get('TEST_ENV', 'dev')
DEFAULT_OUTPUT_FORMAT = os.environ.get('TEST_OUTPUT_FORMAT', 'json')
DEFAULT_DURATION_SECONDS = 60
DEFAULT_CONCURRENT_USERS = 10
DEFAULT_RAMP_UP_SECONDS = 5


def parse_arguments():
    """
    Parses command line arguments for the load test script.
    
    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    parser = argparse.ArgumentParser(
        description='Load testing for Payment API authentication mechanism'
    )
    
    parser.add_argument(
        '--config', 
        dest='config_file',
        default=DEFAULT_CONFIG_FILE,
        help='Path to the configuration file'
    )
    
    parser.add_argument(
        '--env', 
        dest='environment',
        default=DEFAULT_ENVIRONMENT,
        help='Test environment (dev, test, staging, prod)'
    )
    
    parser.add_argument(
        '--output-format', 
        dest='output_format',
        default=DEFAULT_OUTPUT_FORMAT,
        help='Output format for test results (json, csv, html)'
    )
    
    parser.add_argument(
        '--duration', 
        dest='duration_seconds',
        type=int,
        default=DEFAULT_DURATION_SECONDS,
        help='Test duration in seconds'
    )
    
    parser.add_argument(
        '--users', 
        dest='concurrent_users',
        type=int,
        default=DEFAULT_CONCURRENT_USERS,
        help='Number of concurrent users'
    )
    
    parser.add_argument(
        '--ramp-up', 
        dest='ramp_up_seconds',
        type=int,
        default=DEFAULT_RAMP_UP_SECONDS,
        help='Ramp-up period in seconds'
    )
    
    parser.add_argument(
        '--verbose', 
        dest='verbose',
        action='store_true',
        help='Enable verbose logging'
    )
    
    parser.add_argument(
        '--graphs', 
        dest='generate_graphs',
        action='store_true',
        help='Generate performance graphs'
    )
    
    return parser.parse_args()


def calculate_percentile(values, percentile):
    """
    Calculates the specified percentile from a list of values.
    
    Args:
        values (list): List of numeric values
        percentile (float): Percentile to calculate (0-100)
        
    Returns:
        float: Calculated percentile value
    """
    if not values:
        return 0
    
    # Sort the values in ascending order
    sorted_values = sorted(values)
    
    # Calculate the index position for the percentile
    index = (len(sorted_values) - 1) * percentile / 100
    
    # If the index is an integer, return the value at that index
    if index.is_integer():
        return sorted_values[int(index)]
    
    # Otherwise, interpolate between the values at the floor and ceiling of the index
    lower_index = int(index)
    upper_index = lower_index + 1
    
    lower_value = sorted_values[lower_index]
    upper_value = sorted_values[upper_index]
    
    # Interpolate between the two values
    fraction = index - lower_index
    return lower_value + (upper_value - lower_value) * fraction


def generate_performance_graphs(results, output_path):
    """
    Generates performance graphs from test results.
    
    Args:
        results (list): List of test results
        output_path (str): Base path to save the graphs
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Ensure the directory exists
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        # Extract data from results
        response_times = []
        timestamps = []
        status_codes = []
        
        base_time = None
        
        for result in results:
            if hasattr(result, 'to_dict'):
                result_dict = result.to_dict()
            else:
                result_dict = result
                
            response_times.append(result_dict.get('response_time', 0))
            
            # Convert timestamp to relative seconds
            ts = result_dict.get('timestamp')
            if ts:
                if isinstance(ts, str):
                    dt = datetime.fromisoformat(ts.replace('Z', '+00:00'))
                else:
                    dt = ts
                    
                if base_time is None:
                    base_time = dt
                
                rel_time = (dt - base_time).total_seconds()
                timestamps.append(rel_time)
            else:
                timestamps.append(0)
                
            status_codes.append(result_dict.get('status_code', 0))
        
        # Set up matplotlib for non-interactive backend if running on a server
        plt.switch_backend('agg')
        
        # 1. Response Time Distribution Histogram
        plt.figure(figsize=(10, 6))
        plt.hist(response_times, bins=20, alpha=0.7, color='blue')
        plt.title('Response Time Distribution')
        plt.xlabel('Response Time (ms)')
        plt.ylabel('Frequency')
        plt.grid(True, linestyle='--', alpha=0.7)
        plt.savefig(f"{output_path}_response_time_histogram.png", dpi=300, bbox_inches='tight')
        plt.close()
        
        # 2. Response Time over Time Scatter Plot
        plt.figure(figsize=(10, 6))
        plt.scatter(timestamps, response_times, alpha=0.5, color='green')
        plt.title('Response Time over Test Duration')
        plt.xlabel('Time (seconds)')
        plt.ylabel('Response Time (ms)')
        plt.grid(True, linestyle='--', alpha=0.7)
        plt.savefig(f"{output_path}_response_time_scatter.png", dpi=300, bbox_inches='tight')
        plt.close()
        
        # 3. Throughput over Time
        if timestamps:
            # Group timestamps into bins
            max_time = max(timestamps)
            bin_size = max(1, max_time / 20)  # Create about 20 bins or 1-second bins, whichever is larger
            bins = np.arange(0, max_time + bin_size, bin_size)
            
            # Count requests in each bin
            hist, bin_edges = np.histogram(timestamps, bins=bins)
            
            # Calculate throughput (requests per second)
            throughput = hist / bin_size
            bin_centers = (bin_edges[:-1] + bin_edges[1:]) / 2
            
            plt.figure(figsize=(10, 6))
            plt.plot(bin_centers, throughput, '-o', alpha=0.7, color='purple')
            plt.title('Throughput over Test Duration')
            plt.xlabel('Time (seconds)')
            plt.ylabel('Requests per Second')
            plt.grid(True, linestyle='--', alpha=0.7)
            plt.savefig(f"{output_path}_throughput.png", dpi=300, bbox_inches='tight')
            plt.close()
        
        # 4. Error Rate over Time
        if timestamps and status_codes:
            # Create a boolean array where True represents errors (status code >= 400)
            errors = [code >= 400 for code in status_codes]
            
            # Group into bins like throughput
            success_counts = []
            error_counts = []
            
            for i in range(len(bins) - 1):
                start_time = bins[i]
                end_time = bins[i+1]
                
                # Count successes and errors in this time bin
                bin_success = 0
                bin_error = 0
                
                for j in range(len(timestamps)):
                    if start_time <= timestamps[j] < end_time:
                        if errors[j]:
                            bin_error += 1
                        else:
                            bin_success += 1
                
                success_counts.append(bin_success)
                error_counts.append(bin_error)
            
            # Calculate error rates
            total_counts = [s + e for s, e in zip(success_counts, error_counts)]
            error_rates = [e / t if t > 0 else 0 for e, t in zip(error_counts, total_counts)]
            
            plt.figure(figsize=(10, 6))
            plt.plot(bin_centers, error_rates, '-o', alpha=0.7, color='red')
            plt.title('Error Rate over Test Duration')
            plt.xlabel('Time (seconds)')
            plt.ylabel('Error Rate')
            plt.grid(True, linestyle='--', alpha=0.7)
            plt.savefig(f"{output_path}_error_rate.png", dpi=300, bbox_inches='tight')
            plt.close()
        
        LOGGER.info(f"Performance graphs generated and saved to {output_path}_*.png")
        return True
        
    except Exception as e:
        LOGGER.error(f"Error generating performance graphs: {str(e)}")
        return False


class PerformanceResult:
    """
    Class representing the result of a single authentication request in the load test.
    """
    
    def __init__(self, success, response_time, status_code, response_data, worker_id):
        """
        Initializes a new PerformanceResult instance.
        
        Args:
            success (bool): Whether the authentication was successful
            response_time (float): Response time in milliseconds
            status_code (int): HTTP status code
            response_data (dict): Response data from the server
            worker_id (int): ID of the worker that executed the request
        """
        self.request_id = str(uuid.uuid4())
        self.success = success
        self.response_time = response_time
        self.status_code = status_code
        self.response_data = response_data
        self.timestamp = datetime.now()
        self.worker_id = worker_id
    
    def to_dict(self):
        """
        Converts the performance result to a dictionary.
        
        Returns:
            dict: Dictionary representation of the performance result
        """
        return {
            'request_id': self.request_id,
            'success': self.success,
            'response_time': self.response_time,
            'status_code': self.status_code,
            'response_data': self.response_data,
            'timestamp': self.timestamp.isoformat(),
            'worker_id': self.worker_id
        }
    
    @staticmethod
    def from_dict(data):
        """
        Creates a PerformanceResult instance from a dictionary.
        
        Args:
            data (dict): Dictionary containing result data
            
        Returns:
            PerformanceResult: PerformanceResult instance
        """
        result = PerformanceResult(
            success=data.get('success', False),
            response_time=data.get('response_time', 0),
            status_code=data.get('status_code', 0),
            response_data=data.get('response_data', {}),
            worker_id=data.get('worker_id', 0)
        )
        
        # Set additional properties if present
        if 'request_id' in data:
            result.request_id = data['request_id']
            
        if 'timestamp' in data:
            try:
                if isinstance(data['timestamp'], str):
                    result.timestamp = datetime.fromisoformat(data['timestamp'].replace('Z', '+00:00'))
                else:
                    result.timestamp = data['timestamp']
            except ValueError:
                pass
                
        return result


class AuthLoadTestRunner(TestRunner):
    """
    Load test runner class for authentication that extends the base TestRunner.
    """
    
    def __init__(self, config):
        """
        Initializes a new AuthLoadTestRunner instance.
        
        Args:
            config (PerformanceTestConfig): Configuration for the load test
        """
        super().__init__(config)
        self.performance_metrics = {}
        self.session_pool = []
    
    def setup(self):
        """
        Sets up the load test environment.
        
        Returns:
            bool: True if setup successful, False otherwise
        """
        try:
            # Create HTTP session
            self.session = create_http_session()
            
            # Verify connectivity to required endpoints
            eapi_url = self.config.additional_config.get('eapi_url')
            if not eapi_url:
                LOGGER.error("EAPI URL is not configured")
                return False
            
            try:
                # Simple health check to verify connectivity
                response = self.session.get(f"{eapi_url}/api/v1/health", timeout=5)
                if response.status_code != 200:
                    LOGGER.warning(f"Health check failed: {response.status_code}")
            except requests.RequestException as e:
                LOGGER.warning(f"Health check failed: {str(e)}")
                # Continue anyway, as the endpoint might not be available
            
            # Initialize session pool for concurrent users
            LOGGER.info(f"Initializing session pool for {self.config.concurrent_users} concurrent users")
            self.session_pool = [create_http_session() for _ in range(self.config.concurrent_users)]
            
            LOGGER.info("Test environment setup completed successfully")
            return True
            
        except Exception as e:
            LOGGER.error(f"Error setting up test environment: {str(e)}")
            return False
    
    def teardown(self):
        """
        Tears down the load test environment.
        
        Returns:
            bool: True if teardown successful, False otherwise
        """
        try:
            # Close all HTTP sessions in the session pool
            for session in self.session_pool:
                session.close()
            self.session_pool = []
            
            # Close the main session if it exists
            if self.session:
                self.session.close()
                self.session = None
                
            LOGGER.info("Test environment teardown completed successfully")
            return True
            
        except Exception as e:
            LOGGER.error(f"Error tearing down test environment: {str(e)}")
            return False
    
    def run_load_test(self):
        """
        Runs the authentication load test.
        
        Returns:
            list: List of test results
        """
        if not self.setup():
            LOGGER.error("Failed to set up test environment. Aborting load test.")
            return []
        
        try:
            LOGGER.info(f"Starting authentication load test with {self.config.concurrent_users} concurrent users "
                       f"for {self.config.duration_seconds} seconds")
            
            # Calculate the number of requests to execute based on duration and concurrent users
            start_time = time.time()
            end_time = start_time + self.config.duration_seconds
            
            results = []
            
            # Create executor for concurrent requests
            with concurrent.futures.ThreadPoolExecutor(max_workers=self.config.concurrent_users) as executor:
                futures = []
                
                # Get client credentials from config
                client_id = self.config.additional_config.get('test_client_id', 'test-client')
                client_secret = self.config.additional_config.get('test_client_secret', 'test-secret')
                eapi_url = self.config.additional_config.get('eapi_url', 'http://localhost:8080')
                
                # Submit initial batch of tasks
                active_workers = 0
                ramp_up_step = self.config.concurrent_users / self.config.ramp_up_seconds if self.config.ramp_up_seconds > 0 else self.config.concurrent_users
                
                worker_id = 0
                
                while time.time() < end_time:
                    current_time = time.time()
                    elapsed_time = current_time - start_time
                    
                    # Calculate how many workers should be active based on ramp-up
                    if elapsed_time < self.config.ramp_up_seconds:
                        target_workers = int(min(self.config.concurrent_users, ramp_up_step * elapsed_time))
                    else:
                        target_workers = self.config.concurrent_users
                    
                    # Add more workers if needed
                    while active_workers < target_workers:
                        if worker_id < len(self.session_pool):
                            session = self.session_pool[worker_id]
                        else:
                            session = create_http_session()
                            
                        future = executor.submit(
                            self.authentication_worker,
                            worker_id,
                            session,
                            client_id,
                            client_secret,
                            eapi_url
                        )
                        futures.append(future)
                        active_workers += 1
                        worker_id += 1
                    
                    # Check for completed futures
                    done_futures = []
                    for future in futures:
                        if future.done():
                            done_futures.append(future)
                            active_workers -= 1
                            
                            # Get result
                            try:
                                result = future.result()
                                results.append(result)
                            except Exception as e:
                                LOGGER.error(f"Error in worker: {str(e)}")
                    
                    # Remove completed futures
                    for future in done_futures:
                        futures.remove(future)
                    
                    # Short sleep to prevent busy waiting
                    time.sleep(0.1)
                
                # Wait for remaining futures to complete
                for future in concurrent.futures.as_completed(futures):
                    try:
                        result = future.result()
                        results.append(result)
                    except Exception as e:
                        LOGGER.error(f"Error in worker: {str(e)}")
            
            # Calculate performance metrics
            self.calculate_metrics(results)
            
            LOGGER.info(f"Load test completed with {len(results)} requests")
            return results
            
        finally:
            self.teardown()
    
    def authentication_worker(self, worker_id, session, client_id, client_secret, eapi_url):
        """
        Worker function that performs authentication requests.
        
        Args:
            worker_id (int): ID of the worker
            session (requests.Session): HTTP session to use
            client_id (str): Client ID for authentication
            client_secret (str): Client Secret for authentication
            eapi_url (str): URL of the Payment-EAPI service
            
        Returns:
            PerformanceResult: Authentication result with timing and status information
        """
        try:
            # Record start time
            start_time = time.time()
            
            # Perform authentication request
            response_data = authenticate_client(session, eapi_url, client_id, client_secret)
            
            # Record end time and calculate duration
            end_time = time.time()
            duration_ms = (end_time - start_time) * 1000  # Convert to milliseconds
            
            # Check if authentication was successful
            success = response_data is not None
            status_code = 200 if success else 401
            
            # Create result
            result = PerformanceResult(
                success=success,
                response_time=duration_ms,
                status_code=status_code,
                response_data=response_data or {},
                worker_id=worker_id
            )
            
            return result
            
        except Exception as e:
            LOGGER.error(f"Error in authentication worker {worker_id}: {str(e)}")
            
            # Create error result
            return PerformanceResult(
                success=False,
                response_time=0,
                status_code=500,
                response_data={"error": str(e)},
                worker_id=worker_id
            )
    
    def calculate_metrics(self, results):
        """
        Calculates performance metrics from test results.
        
        Args:
            results (list): List of test results
            
        Returns:
            dict: Dictionary of calculated performance metrics
        """
        if not results:
            LOGGER.warning("No results to calculate metrics from")
            self.performance_metrics = {
                "total_requests": 0,
                "successful_requests": 0,
                "failed_requests": 0,
                "success_rate": 0,
                "error_rate": 0,
                "min_response_time": 0,
                "max_response_time": 0,
                "mean_response_time": 0,
                "median_response_time": 0,
                "p90_response_time": 0,
                "p95_response_time": 0,
                "p99_response_time": 0,
                "std_dev_response_time": 0,
                "throughput": 0
            }
            return self.performance_metrics
        
        # Extract response times
        response_times = []
        for result in results:
            if hasattr(result, 'response_time'):
                response_times.append(result.response_time)
            elif isinstance(result, dict) and 'response_time' in result:
                response_times.append(result['response_time'])
        
        # Count successful and failed requests
        successful_requests = 0
        failed_requests = 0
        
        for result in results:
            success = False
            if hasattr(result, 'success'):
                success = result.success
            elif isinstance(result, dict) and 'success' in result:
                success = result['success']
                
            if success:
                successful_requests += 1
            else:
                failed_requests += 1
        
        total_requests = len(results)
        success_rate = (successful_requests / total_requests) * 100 if total_requests > 0 else 0
        error_rate = (failed_requests / total_requests) if total_requests > 0 else 0
        
        # Calculate timing metrics
        if response_times:
            min_response_time = min(response_times)
            max_response_time = max(response_times)
            mean_response_time = statistics.mean(response_times)
            median_response_time = statistics.median(response_times)
            std_dev_response_time = statistics.stdev(response_times) if len(response_times) > 1 else 0
            
            # Calculate percentiles
            p90_response_time = calculate_percentile(response_times, 90)
            p95_response_time = calculate_percentile(response_times, 95)
            p99_response_time = calculate_percentile(response_times, 99)
        else:
            min_response_time = 0
            max_response_time = 0
            mean_response_time = 0
            median_response_time = 0
            std_dev_response_time = 0
            p90_response_time = 0
            p95_response_time = 0
            p99_response_time = 0
        
        # Calculate throughput (requests per second)
        if results:
            # Find first and last timestamp
            first_timestamp = None
            last_timestamp = None
            
            for result in results:
                if hasattr(result, 'timestamp'):
                    timestamp = result.timestamp
                elif isinstance(result, dict) and 'timestamp' in result:
                    timestamp_str = result['timestamp']
                    timestamp = datetime.fromisoformat(timestamp_str.replace('Z', '+00:00'))
                else:
                    continue
                
                if first_timestamp is None or timestamp < first_timestamp:
                    first_timestamp = timestamp
                
                if last_timestamp is None or timestamp > last_timestamp:
                    last_timestamp = timestamp
            
            if first_timestamp and last_timestamp:
                duration_seconds = (last_timestamp - first_timestamp).total_seconds()
                if duration_seconds > 0:
                    throughput = total_requests / duration_seconds
                else:
                    throughput = total_requests  # All requests processed instantly
            else:
                throughput = 0
        else:
            throughput = 0
        
        # Store metrics
        metrics = {
            "total_requests": total_requests,
            "successful_requests": successful_requests,
            "failed_requests": failed_requests,
            "success_rate": success_rate,
            "error_rate": error_rate,
            "min_response_time": min_response_time,
            "max_response_time": max_response_time,
            "mean_response_time": mean_response_time,
            "median_response_time": median_response_time,
            "p90_response_time": p90_response_time,
            "p95_response_time": p95_response_time,
            "p99_response_time": p99_response_time,
            "std_dev_response_time": std_dev_response_time,
            "throughput": throughput
        }
        
        self.performance_metrics = metrics
        return metrics
    
    def evaluate_sla(self, metrics, thresholds):
        """
        Evaluates test results against SLA thresholds.
        
        Args:
            metrics (dict): Dictionary of performance metrics
            thresholds (dict): Dictionary of SLA thresholds
            
        Returns:
            dict: SLA evaluation results with pass/fail status
        """
        evaluation = {
            "metrics": metrics,
            "thresholds": thresholds,
            "evaluation": {}
        }
        
        # Compare p95 response time
        p95_threshold = thresholds.get('p95_response_time', float('inf'))
        p95_actual = metrics.get('p95_response_time', 0)
        p95_pass = p95_actual <= p95_threshold
        
        evaluation["evaluation"]["p95_response_time"] = {
            "threshold": p95_threshold,
            "actual": p95_actual,
            "pass": p95_pass
        }
        
        # Compare p99 response time
        p99_threshold = thresholds.get('p99_response_time', float('inf'))
        p99_actual = metrics.get('p99_response_time', 0)
        p99_pass = p99_actual <= p99_threshold
        
        evaluation["evaluation"]["p99_response_time"] = {
            "threshold": p99_threshold,
            "actual": p99_actual,
            "pass": p99_pass
        }
        
        # Compare error rate
        error_threshold = thresholds.get('error_rate', float('inf'))
        error_actual = metrics.get('error_rate', 1)
        error_pass = error_actual <= error_threshold
        
        evaluation["evaluation"]["error_rate"] = {
            "threshold": error_threshold,
            "actual": error_actual,
            "pass": error_pass
        }
        
        # Compare throughput
        throughput_threshold = thresholds.get('min_throughput', 0)
        throughput_actual = metrics.get('throughput', 0)
        throughput_pass = throughput_actual >= throughput_threshold
        
        evaluation["evaluation"]["throughput"] = {
            "threshold": throughput_threshold,
            "actual": throughput_actual,
            "pass": throughput_pass
        }
        
        # Overall SLA compliance
        evaluation["overall_pass"] = all([
            p95_pass,
            p99_pass,
            error_pass,
            throughput_pass
        ])
        
        return evaluation
    
    def get_performance_report(self):
        """
        Generates a detailed performance report.
        
        Returns:
            dict: Performance report with metrics and SLA evaluation
        """
        # Get metrics from the most recent test run
        metrics = self.performance_metrics
        
        # Get thresholds from configuration
        thresholds = self.config.thresholds
        
        # Evaluate SLA compliance
        sla_evaluation = self.evaluate_sla(metrics, thresholds)
        
        # Create report
        report = {
            "timestamp": datetime.now().isoformat(),
            "config": {
                "duration_seconds": self.config.duration_seconds,
                "concurrent_users": self.config.concurrent_users,
                "ramp_up_seconds": self.config.ramp_up_seconds
            },
            "metrics": metrics,
            "sla_evaluation": sla_evaluation
        }
        
        return report


def main():
    """
    Main function that orchestrates the load testing process.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    # Parse command line arguments
    args = parse_arguments()
    
    # Set up logging
    log_level = logging.DEBUG if args.verbose else logging.INFO
    logger = setup_logging(log_level)
    
    logger.info("Starting Authentication Load Testing")
    
    try:
        # Load performance test configuration
        logger.info(f"Loading configuration from {args.config_file or 'default settings'}")
        config = get_performance_test_config(args.config_file, args.environment)
        
        # Override configuration with command line arguments if provided
        if args.duration_seconds:
            config.duration_seconds = args.duration_seconds
        if args.concurrent_users:
            config.concurrent_users = args.concurrent_users
        if args.ramp_up_seconds:
            config.ramp_up_seconds = args.ramp_up_seconds
        
        # Validate configuration
        if not config.validate():
            logger.error("Invalid configuration. Please check your settings.")
            return 1
        
        # Create load test runner
        test_runner = AuthLoadTestRunner(config)
        
        # Run load test
        logger.info(f"Running load test with {config.concurrent_users} concurrent users for {config.duration_seconds} seconds")
        results = test_runner.run_load_test()
        
        if not results:
            logger.error("No test results were generated. Load test failed.")
            return 1
        
        # Generate report path
        report_path = generate_test_report_path("auth_performance", args.output_format)
        
        # Save test results
        logger.info(f"Saving test results to {report_path}")
        save_test_results(results, report_path, args.output_format)
        
        # Generate and save performance report
        report = test_runner.get_performance_report()
        report_json_path = report_path.replace(f".{args.output_format}", "_report.json")
        
        with open(report_json_path, 'w') as f:
            json.dump(report, f, indent=2)
        
        logger.info(f"Performance report saved to {report_json_path}")
        
        # Generate performance graphs if requested
        if args.generate_graphs:
            graph_base_path = report_path.replace(f".{args.output_format}", "")
            generate_performance_graphs(results, graph_base_path)
        
        # Log summary results
        metrics = report["metrics"]
        sla_evaluation = report["sla_evaluation"]
        
        logger.info("=== Load Test Summary ===")
        logger.info(f"Total Requests: {metrics['total_requests']}")
        logger.info(f"Success Rate: {metrics['success_rate']:.2f}%")
        logger.info(f"Error Rate: {metrics['error_rate'] * 100:.2f}%")
        logger.info(f"Throughput: {metrics['throughput']:.2f} req/sec")
        logger.info(f"Avg Response Time: {metrics['mean_response_time']:.2f} ms")
        logger.info(f"P95 Response Time: {metrics['p95_response_time']:.2f} ms")
        logger.info(f"P99 Response Time: {metrics['p99_response_time']:.2f} ms")
        logger.info(f"SLA Compliance: {'PASS' if sla_evaluation['overall_pass'] else 'FAIL'}")
        
        # Return exit code based on SLA compliance
        return 0 if sla_evaluation['overall_pass'] else 1
        
    except Exception as e:
        logger.error(f"Error during load testing: {str(e)}")
        import traceback
        logger.debug(traceback.format_exc())
        return 1


if __name__ == "__main__":
    sys.exit(main())