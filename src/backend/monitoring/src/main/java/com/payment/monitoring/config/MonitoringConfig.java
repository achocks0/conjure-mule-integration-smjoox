package com.payment.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.monitoring.MetricsService;
import com.payment.common.monitoring.impl.MicrometerMetricsService;
import com.payment.monitoring.service.AlertService;
import com.payment.monitoring.service.MetricsCollectionService;
import com.payment.monitoring.service.impl.AlertServiceImpl;
import com.payment.monitoring.service.impl.PrometheusMetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class that sets up the monitoring infrastructure for the Payment API Security Enhancement project.
 */
@Configuration
@EnableConfigurationProperties({MonitoringProperties.class, AlertThresholdProperties.class, NotificationProperties.class})
public class MonitoringConfig {

    /**
     * Creates a Prometheus meter registry for collecting metrics in Prometheus format
     * 
     * @return A Prometheus meter registry for collecting metrics
     */
    @Bean
    @ConditionalOnMissingBean
    public PrometheusMeterRegistry prometheusMeterRegistry() {
        CollectorRegistry collectorRegistry = new CollectorRegistry();
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(key -> null, collectorRegistry);
        // Add common tags for better metric categorization
        registry.config().commonTags("application", "payment-api");
        return registry;
    }

    /**
     * Provides the Prometheus meter registry as the default MeterRegistry
     * 
     * @param prometheusMeterRegistry The Prometheus meter registry
     * @return The Prometheus meter registry as a MeterRegistry
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry(PrometheusMeterRegistry prometheusMeterRegistry) {
        return prometheusMeterRegistry;
    }

    /**
     * Creates a RestTemplate for making HTTP requests to external systems
     * 
     * @return A RestTemplate for making HTTP requests
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Configure the RestTemplate with appropriate timeouts
        // We could add custom error handlers and timeouts here if needed
        return restTemplate;
    }

    /**
     * Creates an ObjectMapper for JSON serialization/deserialization
     * 
     * @return An ObjectMapper for JSON processing
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure objectMapper for common settings if needed
        return objectMapper;
    }

    /**
     * Creates an AlertService bean for managing alerts and notifications
     * 
     * @param restTemplate RestTemplate for making HTTP requests to notification services
     * @param notificationProperties Configuration properties for notification channels
     * @param alertThresholdProperties Configuration properties for alert thresholds
     * @param objectMapper ObjectMapper for JSON serialization/deserialization
     * @return An AlertService implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public AlertService alertService(RestTemplate restTemplate, 
                                    NotificationProperties notificationProperties,
                                    AlertThresholdProperties alertThresholdProperties,
                                    ObjectMapper objectMapper) {
        return new AlertServiceImpl(restTemplate, notificationProperties, alertThresholdProperties, objectMapper);
    }

    /**
     * Creates a MetricsService bean for basic metrics collection
     * 
     * @param meterRegistry The Micrometer registry to use for recording metrics
     * @return A MetricsService implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsService metricsService(MeterRegistry meterRegistry) {
        return new MicrometerMetricsService(meterRegistry);
    }

    /**
     * Creates a MetricsCollectionService bean for specialized metrics collection and monitoring
     * 
     * @param meterRegistry The meter registry for metrics collection
     * @param metricsService The basic metrics service
     * @param alertService The alert service for checking thresholds and sending alerts
     * @param monitoringProperties Configuration properties for monitoring settings
     * @return A MetricsCollectionService implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsCollectionService metricsCollectionService(MeterRegistry meterRegistry,
                                                           MetricsService metricsService,
                                                           AlertService alertService,
                                                           MonitoringProperties monitoringProperties) {
        return new PrometheusMetricsService(
                meterRegistry,
                metricsService,
                alertService,
                monitoringProperties.isEnabled(),
                monitoringProperties.getCheckIntervalSeconds());
    }
}

/**
 * Configuration properties for monitoring settings
 */
@EnableConfigurationProperties
class MonitoringProperties {
    private boolean enabled = true;
    private String environment = "dev";
    private int metricRetentionDays = 30;
    private int checkIntervalSeconds = 60;
    
    /**
     * Default constructor for MonitoringProperties
     */
    public MonitoringProperties() {
        // Initialize with default values
    }
    
    /**
     * Returns whether monitoring is enabled
     * 
     * @return True if monitoring is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets whether monitoring is enabled
     * 
     * @param enabled Whether monitoring should be enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Returns the environment name
     * 
     * @return The environment name
     */
    public String getEnvironment() {
        return environment;
    }
    
    /**
     * Sets the environment name
     * 
     * @param environment The environment name
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    /**
     * Returns the number of days to retain metrics data
     * 
     * @return The number of days to retain metrics data
     */
    public int getMetricRetentionDays() {
        return metricRetentionDays;
    }
    
    /**
     * Sets the number of days to retain metrics data
     * 
     * @param metricRetentionDays The number of days to retain metrics data
     */
    public void setMetricRetentionDays(int metricRetentionDays) {
        this.metricRetentionDays = metricRetentionDays;
    }
    
    /**
     * Returns the interval in seconds for checking metrics against thresholds
     * 
     * @return The check interval in seconds
     */
    public int getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }
    
    /**
     * Sets the interval in seconds for checking metrics against thresholds
     * 
     * @param checkIntervalSeconds The check interval in seconds
     */
    public void setCheckIntervalSeconds(int checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }
}

/**
 * Configuration properties for alert thresholds
 */
@EnableConfigurationProperties
class AlertThresholdProperties {
    private SecurityThresholds security;
    private PerformanceThresholds performance;
    private AvailabilityThresholds availability;
    
    /**
     * Default constructor for AlertThresholdProperties
     */
    public AlertThresholdProperties() {
        this.security = new SecurityThresholds();
        this.performance = new PerformanceThresholds();
        this.availability = new AvailabilityThresholds();
    }
    
    /**
     * Returns the security thresholds configuration
     * 
     * @return The security thresholds configuration
     */
    public SecurityThresholds getSecurity() {
        return security;
    }
    
    /**
     * Sets the security thresholds configuration
     * 
     * @param security The security thresholds configuration
     */
    public void setSecurity(SecurityThresholds security) {
        this.security = security;
    }
    
    /**
     * Returns the performance thresholds configuration
     * 
     * @return The performance thresholds configuration
     */
    public PerformanceThresholds getPerformance() {
        return performance;
    }
    
    /**
     * Sets the performance thresholds configuration
     * 
     * @param performance The performance thresholds configuration
     */
    public void setPerformance(PerformanceThresholds performance) {
        this.performance = performance;
    }
    
    /**
     * Returns the availability thresholds configuration
     * 
     * @return The availability thresholds configuration
     */
    public AvailabilityThresholds getAvailability() {
        return availability;
    }
    
    /**
     * Sets the availability thresholds configuration
     * 
     * @param availability The availability thresholds configuration
     */
    public void setAvailability(AvailabilityThresholds availability) {
        this.availability = availability;
    }
}

/**
 * Configuration properties for notification channels
 */
@EnableConfigurationProperties
class NotificationProperties {
    private String pagerDutyApiKey;
    private String slackWebhookUrl;
    private String emailNotificationAddress;
    private String emailServiceEndpoint;
    
    /**
     * Default constructor for NotificationProperties
     */
    public NotificationProperties() {
        // Initialize with default empty values
    }
    
    /**
     * Returns the PagerDuty API key
     * 
     * @return The PagerDuty API key
     */
    public String getPagerDutyApiKey() {
        return pagerDutyApiKey;
    }
    
    /**
     * Sets the PagerDuty API key
     * 
     * @param pagerDutyApiKey The PagerDuty API key
     */
    public void setPagerDutyApiKey(String pagerDutyApiKey) {
        this.pagerDutyApiKey = pagerDutyApiKey;
    }
    
    /**
     * Returns the Slack webhook URL
     * 
     * @return The Slack webhook URL
     */
    public String getSlackWebhookUrl() {
        return slackWebhookUrl;
    }
    
    /**
     * Sets the Slack webhook URL
     * 
     * @param slackWebhookUrl The Slack webhook URL
     */
    public void setSlackWebhookUrl(String slackWebhookUrl) {
        this.slackWebhookUrl = slackWebhookUrl;
    }
    
    /**
     * Returns the email notification address
     * 
     * @return The email notification address
     */
    public String getEmailNotificationAddress() {
        return emailNotificationAddress;
    }
    
    /**
     * Sets the email notification address
     * 
     * @param emailNotificationAddress The email notification address
     */
    public void setEmailNotificationAddress(String emailNotificationAddress) {
        this.emailNotificationAddress = emailNotificationAddress;
    }
    
    /**
     * Returns the email service endpoint
     * 
     * @return The email service endpoint
     */
    public String getEmailServiceEndpoint() {
        return emailServiceEndpoint;
    }
    
    /**
     * Sets the email service endpoint
     * 
     * @param emailServiceEndpoint The email service endpoint
     */
    public void setEmailServiceEndpoint(String emailServiceEndpoint) {
        this.emailServiceEndpoint = emailServiceEndpoint;
    }
}