package com.payment.monitoring.service;

import com.payment.common.monitoring.MetricsService;
import com.payment.monitoring.service.AlertService;

import java.util.Map;
import java.util.List;

/**
 * Interface defining specialized metrics collection and monitoring services for the Payment API Security Enhancement project.
 * This service extends the basic metrics functionality with methods for monitoring authentication, token operations,
 * API performance, Conjur vault operations, and system health, as well as checking metrics against defined thresholds for alerting.
 */
public interface MetricsCollectionService {
    
    /**
     * Monitors authentication metrics and triggers alerts if thresholds are exceeded.
     * This method records authentication attempts, calculates success/failure rates,
     * checks security thresholds, and detects potential brute force attempts.
     *
     * @param clientId The ID of the client making the authentication attempt
     * @param success Whether the authentication attempt was successful
     */
    void monitorAuthenticationMetrics(String clientId, boolean success);
    
    /**
     * Monitors token generation and validation metrics and triggers alerts if thresholds are exceeded.
     * This method records token operations, checks performance and security thresholds,
     * and logs token operation metrics.
     *
     * @param clientId The ID of the client for the token operation
     * @param operation The type of operation (e.g., "generation", "validation")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the operation in milliseconds
     */
    void monitorTokenMetrics(String clientId, String operation, boolean success, long durationMs);
    
    /**
     * Monitors API performance metrics and triggers alerts if thresholds are exceeded.
     * This method records API requests, checks performance and availability thresholds,
     * calculates SLA compliance metrics, and logs API performance metrics.
     *
     * @param endpoint The API endpoint that was called
     * @param method The HTTP method used (GET, POST, etc.)
     * @param statusCode The HTTP status code returned
     * @param durationMs The duration of the API request processing in milliseconds
     */
    void monitorApiPerformance(String endpoint, String method, int statusCode, long durationMs);
    
    /**
     * Monitors Conjur vault operations and triggers alerts if thresholds are exceeded.
     * This method records Conjur operations, checks performance and availability thresholds,
     * monitors for unusual credential access patterns, and logs Conjur operation metrics.
     *
     * @param operationType The type of operation performed (e.g., "retrieve", "authenticate")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the Conjur operation in milliseconds
     */
    void monitorConjurOperations(String operationType, boolean success, long durationMs);
    
    /**
     * Monitors credential rotation events and triggers alerts if issues are detected.
     * This method records rotation metrics, tracks rotation status, checks security thresholds,
     * monitors rotation duration and success rates, and logs rotation metrics.
     *
     * @param clientId The ID of the client whose credentials are being rotated
     * @param status The status of the rotation (e.g., "started", "completed", "failed")
     * @param durationMs The duration of the credential rotation operation in milliseconds
     */
    void monitorCredentialRotation(String clientId, String status, long durationMs);
    
    /**
     * Monitors system health metrics including CPU, memory, and connection pools.
     * This method collects system resource metrics, JVM metrics, connection pool metrics,
     * checks performance and availability thresholds, and logs system health metrics.
     */
    void monitorSystemHealth();
    
    /**
     * Monitors Redis cache performance metrics and triggers alerts if thresholds are exceeded.
     * This method records cache operations, calculates hit/miss rates, checks performance
     * and availability thresholds, and logs cache performance metrics.
     *
     * @param operation The type of cache operation (e.g., "get", "set", "delete")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the cache operation in milliseconds
     */
    void monitorCachePerformance(String operation, boolean success, long durationMs);
    
    /**
     * Collects a custom metric with the specified name, value, and tags.
     * This method allows recording of arbitrary metrics that don't fit the predefined categories.
     *
     * @param name The name of the metric
     * @param value The value of the metric
     * @param tags Additional tags to categorize the metric
     */
    void collectCustomMetric(String name, double value, Map<String, String> tags);
    
    /**
     * Retrieves the current value of a metric with the specified name and tags.
     * This method provides access to previously collected metric values.
     *
     * @param name The name of the metric
     * @param tags Additional tags to identify the specific metric
     * @return The current value of the metric, or 0.0 if not found
     */
    double getMetricValue(String name, Map<String, String> tags);
    
    /**
     * Exports all collected metrics in Prometheus exposition format.
     * This method formats all metrics for scraping by Prometheus or compatible systems.
     *
     * @return Metrics in Prometheus exposition format
     */
    String exportMetrics();
    
    /**
     * Checks all metrics against their defined thresholds and triggers alerts if needed.
     * This method performs a comprehensive check of security, performance, and availability metrics.
     *
     * @return List of alert IDs that were triggered
     */
    List<String> checkThresholds();
}