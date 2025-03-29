package com.payment.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;

import com.payment.common.monitoring.MetricsService;
import com.payment.common.monitoring.impl.MicrometerMetricsService;

/**
 * Configuration class that sets up the metrics collection infrastructure for the Payment API Security Enhancement project.
 */
@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsConfig {

    /**
     * Creates a default meter registry if none is provided by the application context
     *
     * @return A simple meter registry for collecting metrics
     */
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Creates a metrics service bean that uses the meter registry for collecting application metrics
     *
     * @param meterRegistry The meter registry to use for metrics collection
     * @return Metrics service implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsService metricsService(MeterRegistry meterRegistry) {
        return new MicrometerMetricsService(meterRegistry);
    }

    /**
     * Configures JVM memory metrics collection
     *
     * @param meterRegistry The meter registry to use for metrics collection
     * @return JVM memory metrics collector
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry meterRegistry) {
        JvmMemoryMetrics metrics = new JvmMemoryMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Configures JVM garbage collection metrics collection
     *
     * @param meterRegistry The meter registry to use for metrics collection
     * @return JVM garbage collection metrics collector
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics(MeterRegistry meterRegistry) {
        JvmGcMetrics metrics = new JvmGcMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Configures processor usage metrics collection
     *
     * @param meterRegistry The meter registry to use for metrics collection
     * @return Processor metrics collector
     */
    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry meterRegistry) {
        ProcessorMetrics metrics = new ProcessorMetrics();
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}

/**
 * Configuration properties for metrics collection
 */
@ConfigurationProperties(prefix = "payment.metrics")
public class MetricsProperties {
    private boolean enabled = true;
    private String prefix = "payment";
    private int retentionDays = 30;

    /**
     * Default constructor for MetricsProperties
     */
    public MetricsProperties() {
        // Initialize with default values: enabled=true, prefix="payment", retentionDays=30
    }

    /**
     * Returns whether metrics collection is enabled
     *
     * @return True if metrics collection is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether metrics collection is enabled
     *
     * @param enabled True to enable metrics collection, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the metrics name prefix
     *
     * @return The metrics name prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Sets the metrics name prefix
     *
     * @param prefix The metrics name prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the number of days to retain metrics data
     *
     * @return The number of days to retain metrics data
     */
    public int getRetentionDays() {
        return retentionDays;
    }

    /**
     * Sets the number of days to retain metrics data
     *
     * @param retentionDays The number of days to retain metrics data
     */
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}