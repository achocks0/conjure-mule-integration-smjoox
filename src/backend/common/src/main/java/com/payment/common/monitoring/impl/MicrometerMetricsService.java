package com.payment.common.monitoring.impl;

import com.payment.common.monitoring.MetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the MetricsService interface using Micrometer for collecting and recording metrics
 * related to authentication, token operations, API performance, and Conjur vault operations.
 * This service provides standardized monitoring for security and performance aspects of the
 * Payment API Security Enhancement project.
 */
public class MicrometerMetricsService implements MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsService.class);
    
    private final MeterRegistry meterRegistry;
    private final String metricPrefix;
    private final ConcurrentHashMap<String, MutableDouble> gaugeValues = new ConcurrentHashMap<>();
    
    /**
     * Constructs a new MicrometerMetricsService with the provided meter registry.
     *
     * @param meterRegistry The Micrometer registry to use for recording metrics
     */
    public MicrometerMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.metricPrefix = "payment";
    }
    
    @Override
    public void recordAuthenticationAttempt(String clientId, boolean success) {
        Tags tags = createTags("clientId", clientId, "success", String.valueOf(success));
        
        Counter counter = meterRegistry.counter(createMetricName("authentication.attempts"), tags);
        counter.increment();
        
        logger.debug("Recorded authentication attempt: clientId={}, success={}", clientId, success);
    }
    
    @Override
    public void recordTokenGeneration(String clientId, long durationMs) {
        Tags tags = createTags("clientId", clientId);
        
        Counter counter = meterRegistry.counter(createMetricName("token.generation.count"), tags);
        counter.increment();
        
        Timer timer = meterRegistry.timer(createMetricName("token.generation.time"), tags);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        logger.debug("Recorded token generation: clientId={}, durationMs={}", clientId, durationMs);
    }
    
    @Override
    public void recordTokenValidation(String clientId, boolean valid, long durationMs) {
        Tags tags = createTags("clientId", clientId, "valid", String.valueOf(valid));
        
        Counter counter = meterRegistry.counter(createMetricName("token.validation.count"), tags);
        counter.increment();
        
        Timer timer = meterRegistry.timer(createMetricName("token.validation.time"), tags);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        logger.debug("Recorded token validation: clientId={}, valid={}, durationMs={}", 
                    clientId, valid, durationMs);
    }
    
    @Override
    public void recordApiRequest(String endpoint, String method, int statusCode, long durationMs) {
        Tags tags = createTags("endpoint", endpoint, "method", method, "status", String.valueOf(statusCode));
        
        Counter counter = meterRegistry.counter(createMetricName("api.requests"), tags);
        counter.increment();
        
        Timer timer = meterRegistry.timer(createMetricName("api.request.time"), tags);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        // Track status code categories (2xx, 4xx, 5xx)
        String statusCategory = Integer.toString(statusCode).charAt(0) + "xx";
        Counter statusCounter = meterRegistry.counter(
            createMetricName("api.status." + statusCategory),
            Tags.of("endpoint", endpoint, "method", method)
        );
        statusCounter.increment();
        
        logger.debug("Recorded API request: endpoint={}, method={}, status={}, durationMs={}", 
                    endpoint, method, statusCode, durationMs);
    }
    
    @Override
    public void recordConjurOperation(String operationType, boolean success, long durationMs) {
        Tags tags = createTags("operation", operationType, "success", String.valueOf(success));
        
        Counter counter = meterRegistry.counter(createMetricName("conjur.operations"), tags);
        counter.increment();
        
        Timer timer = meterRegistry.timer(createMetricName("conjur.operation.time"), tags);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        logger.debug("Recorded Conjur operation: operation={}, success={}, durationMs={}", 
                    operationType, success, durationMs);
    }
    
    @Override
    public void recordCredentialRotation(String clientId, String status, long durationMs) {
        Tags tags = createTags("clientId", clientId, "status", status);
        
        Counter counter = meterRegistry.counter(createMetricName("credential.rotation"), tags);
        counter.increment();
        
        Timer timer = meterRegistry.timer(createMetricName("credential.rotation.time"), tags);
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        logger.debug("Recorded credential rotation: clientId={}, status={}, durationMs={}", 
                    clientId, status, durationMs);
    }
    
    @Override
    public void incrementCounter(String name, String[] tags) {
        Counter counter = meterRegistry.counter(createMetricName(name), createTags(tags));
        counter.increment();
        
        logger.debug("Incremented counter: name={}", name);
    }
    
    @Override
    public void recordGaugeValue(String name, double value, String[] tags) {
        // Create tags from the array
        Tags metricTags = createTags(tags);
        
        // Create a unique ID for this gauge based on name and tags
        String gaugeId = createMetricName(name) + metricTags.toString();
        
        // Get or create a MutableDouble for this gauge
        MutableDouble mutableValue = gaugeValues.computeIfAbsent(gaugeId, k -> {
            MutableDouble md = new MutableDouble(value);
            // Register a gauge that reads from this MutableDouble
            meterRegistry.gauge(createMetricName(name), metricTags, md, MutableDouble::getValue);
            return md;
        });
        
        // Update the gauge value
        mutableValue.setValue(value);
        
        logger.debug("Recorded gauge value: name={}, value={}", name, value);
    }
    
    @Override
    public void recordTiming(String name, long durationMs, String[] tags) {
        Timer timer = meterRegistry.timer(createMetricName(name), createTags(tags));
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        
        logger.debug("Recorded timing: name={}, durationMs={}", name, durationMs);
    }
    
    /**
     * Creates a prefixed metric name to ensure consistent naming across the application.
     *
     * @param name The base name of the metric
     * @return The prefixed metric name
     */
    private String createMetricName(String name) {
        return metricPrefix + "." + name;
    }
    
    /**
     * Creates Micrometer Tags from key-value pairs.
     *
     * @param keyValuePairs An array of key-value pairs (must have even number of elements)
     * @return Micrometer Tags object containing the key-value pairs
     */
    private Tags createTags(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided as key-value pairs");
        }
        
        Tags tags = Tags.empty();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            tags = tags.and(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        
        return tags;
    }
    
    /**
     * Creates Micrometer Tags from an array of key-value pairs.
     *
     * @param keyValuePairs An array of key-value pairs (must have even number of elements)
     * @return Micrometer Tags object containing the key-value pairs
     */
    private Tags createTags(String[] keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided as key-value pairs");
        }
        
        Tags tags = Tags.empty();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            tags = tags.and(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        
        return tags;
    }
    
    // Inner class for mutable double values
    private static class MutableDouble {
        private double value;
        
        public MutableDouble(double value) {
            this.value = value;
        }
        
        public double getValue() {
            return value;
        }
        
        public void setValue(double value) {
            this.value = value;
        }
    }
}