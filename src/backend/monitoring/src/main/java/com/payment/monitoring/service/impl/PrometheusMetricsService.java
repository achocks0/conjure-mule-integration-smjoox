package com.payment.monitoring.service.impl;

import com.payment.monitoring.service.MetricsCollectionService;
import com.payment.common.monitoring.MetricsService;
import com.payment.monitoring.service.AlertService;

import io.micrometer.core.instrument.MeterRegistry; // 1.8.x
import io.micrometer.core.instrument.Counter; // 1.8.x
import io.micrometer.core.instrument.Timer; // 1.8.x
import io.micrometer.core.instrument.Gauge; // 1.8.x
import io.micrometer.core.instrument.Tags; // 1.8.x
import io.micrometer.prometheus.PrometheusMeterRegistry; // 1.8.x

import org.springframework.scheduling.annotation.Scheduled; // 5.6.x
import org.springframework.stereotype.Service; // 5.6.x
import org.springframework.beans.factory.annotation.Value; // 5.6.x

import org.slf4j.Logger; // 1.7.x
import org.slf4j.LoggerFactory; // 1.7.x

import java.util.Map; // JDK 11
import java.util.HashMap; // JDK 11
import java.util.List; // JDK 11
import java.util.ArrayList; // JDK 11

import java.lang.management.ManagementFactory; // JDK 11
import java.lang.management.MemoryMXBean; // JDK 11
import java.lang.management.ThreadMXBean; // JDK 11

/**
 * Implementation of the MetricsCollectionService interface that uses Prometheus for collecting and monitoring metrics
 * related to authentication, token operations, API performance, and security aspects of the Payment API Security Enhancement project.
 */
@Service
public class PrometheusMetricsService implements MetricsCollectionService {
    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsService.class);
    private static final String METRIC_PREFIX = "payment";
    
    private final MeterRegistry meterRegistry;
    private final PrometheusMeterRegistry prometheusMeterRegistry;
    private final MetricsService metricsService;
    private final AlertService alertService;
    private final Map<String, Double> metricValues;
    private final boolean monitoringEnabled;
    private final long checkIntervalSeconds;
    
    /**
     * Constructs a new PrometheusMetricsService with the provided dependencies
     * 
     * @param meterRegistry The Micrometer meter registry for metrics collection
     * @param metricsService The metrics service for recording basic metrics
     * @param alertService The alert service for checking thresholds and sending alerts
     * @param monitoringEnabled Flag indicating whether monitoring is enabled
     * @param checkIntervalSeconds Interval in seconds for scheduled threshold checks
     */
    public PrometheusMetricsService(
            MeterRegistry meterRegistry,
            MetricsService metricsService,
            AlertService alertService,
            @Value("${payment.monitoring.enabled:true}") boolean monitoringEnabled,
            @Value("${payment.monitoring.check-interval-seconds:60}") long checkIntervalSeconds) {
        this.meterRegistry = meterRegistry;
        this.prometheusMeterRegistry = (meterRegistry instanceof PrometheusMeterRegistry) 
                ? (PrometheusMeterRegistry) meterRegistry 
                : null;
        this.metricsService = metricsService;
        this.alertService = alertService;
        this.monitoringEnabled = monitoringEnabled;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.metricValues = new HashMap<>();
        logger.info("Prometheus metrics service initialized with monitoring enabled: {}", monitoringEnabled);
    }
    
    /**
     * Monitors authentication metrics and triggers alerts if thresholds are exceeded
     *
     * @param clientId The ID of the client making the authentication attempt
     * @param success Whether the authentication attempt was successful
     */
    @Override
    public void monitorAuthenticationMetrics(String clientId, boolean success) {
        if (!monitoringEnabled) {
            return;
        }
        
        // Record the authentication attempt
        metricsService.recordAuthenticationAttempt(clientId, success);
        
        // Calculate authentication success/failure rates
        double successRate = calculateSuccessRate("authentication", success);
        
        // Store metrics for threshold checks
        Map<String, String> tags = new HashMap<>();
        tags.put("clientId", clientId);
        metricValues.put(createMetricKey("authentication.success.rate", tags), successRate);
        
        if (!success) {
            // Check security thresholds for authentication failures
            alertService.checkSecurityThresholds("authentication.failure", 1.0, tags);
        }
        
        logger.debug("Monitored authentication metrics for client {}: success={}", clientId, success);
    }
    
    /**
     * Monitors token generation and validation metrics and triggers alerts if thresholds are exceeded
     *
     * @param clientId The ID of the client for the token operation
     * @param operation The type of operation (e.g., "generation", "validation")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the operation in milliseconds
     */
    @Override
    public void monitorTokenMetrics(String clientId, String operation, boolean success, long durationMs) {
        if (!monitoringEnabled) {
            return;
        }
        
        Map<String, String> tags = new HashMap<>();
        tags.put("clientId", clientId);
        tags.put("operation", operation);
        
        // Record token metrics based on operation type
        if ("generation".equals(operation)) {
            metricsService.recordTokenGeneration(clientId, durationMs);
            metricValues.put(createMetricKey("token.generation.duration", tags), (double) durationMs);
            alertService.checkPerformanceThresholds("token.generation.duration", durationMs, tags);
        } else if ("validation".equals(operation)) {
            metricsService.recordTokenValidation(clientId, success, durationMs);
            double successRate = calculateSuccessRate("token.validation", success);
            metricValues.put(createMetricKey("token.validation.success.rate", tags), successRate);
            metricValues.put(createMetricKey("token.validation.duration", tags), (double) durationMs);
            
            alertService.checkPerformanceThresholds("token.validation.duration", durationMs, tags);
            
            if (!success) {
                alertService.checkSecurityThresholds("token.validation.failure", 1.0, tags);
            }
        }
        
        logger.debug("Monitored token metrics for client {}: operation={}, success={}, duration={}ms", 
                clientId, operation, success, durationMs);
    }
    
    /**
     * Monitors API performance metrics and triggers alerts if thresholds are exceeded
     *
     * @param endpoint The API endpoint that was called
     * @param method The HTTP method used (GET, POST, etc.)
     * @param statusCode The HTTP status code returned
     * @param durationMs The duration of the API request processing in milliseconds
     */
    @Override
    public void monitorApiPerformance(String endpoint, String method, int statusCode, long durationMs) {
        if (!monitoringEnabled) {
            return;
        }
        
        // Record the API request
        metricsService.recordApiRequest(endpoint, method, statusCode, durationMs);
        
        Map<String, String> tags = new HashMap<>();
        tags.put("endpoint", endpoint);
        tags.put("method", method);
        tags.put("statusCategory", String.valueOf(statusCode / 100) + "xx");
        
        // Store metrics for threshold checks
        metricValues.put(createMetricKey("api.response.time", tags), (double) durationMs);
        
        // Calculate error rate if status code is 4xx or 5xx
        if (statusCode >= 400) {
            metricValues.put(createMetricKey("api.error.rate", tags), 1.0);
            alertService.checkAvailabilityThresholds("api.error.rate", 1.0, tags);
        }
        
        // Check performance thresholds for API response time
        alertService.checkPerformanceThresholds("api.response.time", durationMs, tags);
        
        logger.debug("Monitored API performance: endpoint={}, method={}, statusCode={}, duration={}ms", 
                endpoint, method, statusCode, durationMs);
    }
    
    /**
     * Monitors Conjur vault operations and triggers alerts if thresholds are exceeded
     *
     * @param operationType The type of operation performed (e.g., "retrieve", "authenticate")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the Conjur operation in milliseconds
     */
    @Override
    public void monitorConjurOperations(String operationType, boolean success, long durationMs) {
        if (!monitoringEnabled) {
            return;
        }
        
        // Record the Conjur operation
        metricsService.recordConjurOperation(operationType, success, durationMs);
        
        Map<String, String> tags = new HashMap<>();
        tags.put("operationType", operationType);
        
        // Store metrics for threshold checks
        double successRate = calculateSuccessRate("conjur." + operationType, success);
        metricValues.put(createMetricKey("conjur.operation.success.rate", tags), successRate);
        metricValues.put(createMetricKey("conjur.operation.duration", tags), (double) durationMs);
        
        // Check performance thresholds for Conjur response time
        alertService.checkPerformanceThresholds("conjur.operation.duration", durationMs, tags);
        
        // Check availability thresholds for Conjur availability
        if (!success) {
            alertService.checkAvailabilityThresholds("conjur.availability", 0.0, tags);
            alertService.checkSecurityThresholds("conjur.operation.failure", 1.0, tags);
        }
        
        logger.debug("Monitored Conjur operation: type={}, success={}, duration={}ms", 
                operationType, success, durationMs);
    }
    
    /**
     * Monitors credential rotation events and triggers alerts if issues are detected
     *
     * @param clientId The ID of the client whose credentials are being rotated
     * @param status The status of the rotation (e.g., "started", "completed", "failed")
     * @param durationMs The duration of the credential rotation operation in milliseconds
     */
    @Override
    public void monitorCredentialRotation(String clientId, String status, long durationMs) {
        if (!monitoringEnabled) {
            return;
        }
        
        // Record the credential rotation event
        metricsService.recordCredentialRotation(clientId, status, durationMs);
        
        Map<String, String> tags = new HashMap<>();
        tags.put("clientId", clientId);
        tags.put("status", status);
        
        // Store metrics for threshold checks
        metricValues.put(createMetricKey("credential.rotation.duration", tags), (double) durationMs);
        
        // Check security thresholds for rotation failures
        if ("failed".equals(status)) {
            alertService.checkSecurityThresholds("credential.rotation.failure", 1.0, tags);
        }
        
        // Check performance thresholds for rotation duration
        alertService.checkPerformanceThresholds("credential.rotation.duration", durationMs, tags);
        
        logger.debug("Monitored credential rotation: clientId={}, status={}, duration={}ms", 
                clientId, status, durationMs);
    }
    
    /**
     * Monitors system health metrics including CPU, memory, and connection pools
     */
    @Override
    public void monitorSystemHealth() {
        if (!monitoringEnabled) {
            return;
        }
        
        try {
            // Collect memory usage metrics
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            double heapUsagePercent = (double) heapUsed / heapMax * 100.0;
            
            // Collect thread metrics
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadBean.getThreadCount();
            
            // Store metrics for threshold checks
            Map<String, String> memoryTags = new HashMap<>();
            memoryTags.put("type", "heap");
            metricValues.put(createMetricKey("system.memory.usage.percent", memoryTags), heapUsagePercent);
            
            Map<String, String> threadTags = new HashMap<>();
            metricValues.put(createMetricKey("system.thread.count", threadTags), (double) threadCount);
            
            // Check performance thresholds for resource utilization
            alertService.checkPerformanceThresholds("system.memory.usage.percent", heapUsagePercent, memoryTags);
            
            logger.debug("Monitored system health: heapUsage={}%, threadCount={}", 
                    String.format("%.2f", heapUsagePercent), threadCount);
        } catch (Exception e) {
            logger.error("Error monitoring system health", e);
        }
    }
    
    /**
     * Monitors Redis cache performance metrics and triggers alerts if thresholds are exceeded
     *
     * @param operation The type of cache operation (e.g., "get", "set", "delete")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the cache operation in milliseconds
     */
    @Override
    public void monitorCachePerformance(String operation, boolean success, long durationMs) {
        if (!monitoringEnabled) {
            return;
        }
        
        // Record the cache operation using custom metrics
        String[] tags = { "operation", operation, "success", String.valueOf(success) };
        metricsService.recordTiming("cache.operation.duration", durationMs, tags);
        
        Map<String, String> tagMap = new HashMap<>();
        tagMap.put("operation", operation);
        
        // Store metrics for threshold checks
        double successRate = calculateSuccessRate("cache." + operation, success);
        metricValues.put(createMetricKey("cache.operation.success.rate", tagMap), successRate);
        metricValues.put(createMetricKey("cache.operation.duration", tagMap), (double) durationMs);
        
        // Check performance thresholds for cache response time
        alertService.checkPerformanceThresholds("cache.operation.duration", durationMs, tagMap);
        
        // Check availability thresholds for cache availability
        if (!success) {
            alertService.checkAvailabilityThresholds("cache.availability", 0.0, tagMap);
        }
        
        logger.debug("Monitored cache performance: operation={}, success={}, duration={}ms", 
                operation, success, durationMs);
    }
    
    /**
     * Collects a custom metric with the specified name, value, and tags
     *
     * @param name The name of the metric
     * @param value The value of the metric
     * @param tags Additional tags to categorize the metric
     */
    @Override
    public void collectCustomMetric(String name, double value, Map<String, String> tags) {
        if (!monitoringEnabled) {
            return;
        }
        
        String metricName = METRIC_PREFIX + "." + name;
        
        // Convert Map to array for MetricsService
        String[] tagsArray = new String[tags.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            tagsArray[i++] = entry.getKey();
            tagsArray[i++] = entry.getValue();
        }
        
        // Record the gauge value
        metricsService.recordGaugeValue(metricName, value, tagsArray);
        
        // Store for threshold checks
        metricValues.put(createMetricKey(name, tags), value);
        
        logger.debug("Collected custom metric: name={}, value={}", metricName, value);
    }
    
    /**
     * Retrieves the current value of a metric with the specified name and tags
     *
     * @param name The name of the metric
     * @param tags Additional tags to identify the specific metric
     * @return The current value of the metric, or 0.0 if not found
     */
    @Override
    public double getMetricValue(String name, Map<String, String> tags) {
        if (!monitoringEnabled) {
            return 0.0;
        }
        
        String metricKey = createMetricKey(name, tags);
        return metricValues.getOrDefault(metricKey, 0.0);
    }
    
    /**
     * Exports all collected metrics in Prometheus exposition format
     *
     * @return Metrics in Prometheus exposition format
     */
    @Override
    public String exportMetrics() {
        if (!monitoringEnabled || prometheusMeterRegistry == null) {
            return "";
        }
        
        try {
            String result = prometheusMeterRegistry.scrape();
            logger.debug("Exported {} bytes of metrics data in Prometheus format", result.length());
            return result;
        } catch (Exception e) {
            logger.error("Error exporting metrics", e);
            return "";
        }
    }
    
    /**
     * Checks all metrics against their defined thresholds and triggers alerts if needed
     *
     * @return List of alert IDs that were triggered
     */
    @Override
    public List<String> checkThresholds() {
        if (!monitoringEnabled) {
            return new ArrayList<>();
        }
        
        List<String> alertIds = new ArrayList<>();
        
        for (Map.Entry<String, Double> entry : metricValues.entrySet()) {
            String metricKey = entry.getKey();
            double value = entry.getValue();
            
            // Parse the metric key to get name and tags
            int tagStart = metricKey.indexOf('[');
            String name = metricKey.substring(0, tagStart > 0 ? tagStart : metricKey.length());
            Map<String, String> tags = new HashMap<>();
            
            if (tagStart > 0) {
                String tagsString = metricKey.substring(tagStart + 1, metricKey.length() - 1);
                String[] tagPairs = tagsString.split(",");
                for (String pair : tagPairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        tags.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            
            boolean triggered = false;
            
            // Determine the category based on the metric name
            if (name.startsWith("authentication.") || name.startsWith("token.validation.") || 
                    name.startsWith("credential.rotation.") || name.startsWith("conjur.operation.failure")) {
                triggered = alertService.checkSecurityThresholds(name, value, tags);
            } else if (name.contains(".duration") || name.contains(".response.time") || 
                    name.contains(".usage.percent")) {
                triggered = alertService.checkPerformanceThresholds(name, value, tags);
            } else if (name.contains(".availability") || name.contains(".error.rate")) {
                triggered = alertService.checkAvailabilityThresholds(name, value, tags);
            }
            
            if (triggered) {
                alertIds.add(name + ":" + tags.toString());
            }
        }
        
        logger.debug("Checked thresholds for {} metrics, triggered {} alerts", metricValues.size(), alertIds.size());
        return alertIds;
    }
    
    /**
     * Scheduled method that periodically checks all metrics against thresholds
     */
    @Scheduled(fixedDelayString = "${payment.monitoring.check-interval-seconds:60}000")
    public void scheduledThresholdCheck() {
        if (!monitoringEnabled) {
            logger.debug("Scheduled threshold check skipped - monitoring is disabled");
            return;
        }
        
        List<String> triggeredAlerts = checkThresholds();
        if (!triggeredAlerts.isEmpty()) {
            logger.info("Scheduled threshold check triggered {} alerts: {}", 
                    triggeredAlerts.size(), triggeredAlerts);
        } else {
            logger.debug("Scheduled threshold check completed - no alerts triggered");
        }
    }
    
    /**
     * Creates a unique key for a metric based on its name and tags
     *
     * @param name The name of the metric
     * @param tags The metric tags
     * @return A unique key for the metric
     */
    private String createMetricKey(String name, Map<String, String> tags) {
        StringBuilder key = new StringBuilder(name);
        
        if (tags != null && !tags.isEmpty()) {
            key.append('[');
            boolean first = true;
            
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                if (!first) {
                    key.append(',');
                }
                key.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            
            key.append(']');
        }
        
        return key.toString();
    }
    
    /**
     * Calculates the success rate based on success and total counts
     *
     * @param metricName The base name of the metric
     * @param success Whether this operation was successful
     * @return The calculated success rate as a percentage
     */
    private double calculateSuccessRate(String metricName, boolean success) {
        String totalKey = metricName + ".total";
        String successKey = metricName + ".success";
        
        metricValues.put(totalKey, metricValues.getOrDefault(totalKey, 0.0) + 1.0);
        
        if (success) {
            metricValues.put(successKey, metricValues.getOrDefault(successKey, 0.0) + 1.0);
        }
        
        double total = metricValues.get(totalKey);
        double successCount = metricValues.getOrDefault(successKey, 0.0);
        
        return (total > 0) ? (successCount / total) * 100.0 : 0.0;
    }
}