package com.payment.rotation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.payment.rotation.service.RotationService;
import com.payment.rotation.service.impl.RotationServiceImpl;
import com.payment.rotation.service.NotificationService;
import com.payment.rotation.service.impl.NotificationServiceImpl;
import com.payment.rotation.service.ConjurService;
import com.payment.common.monitoring.MetricsService;

/**
 * Configuration class for the Credential Rotation service that provides beans and settings
 * for managing secure credential rotation processes. This class configures the rotation service,
 * notification service, and related components to enable zero-downtime credential rotation.
 */
@Configuration
@EnableScheduling
public class RotationConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotationConfig.class);

    @Value("${rotation.default-transition-period-minutes:60}")
    private int defaultTransitionPeriodMinutes;

    @Value("${rotation.monitoring-interval-seconds:30}")
    private int monitoringIntervalSeconds;

    @Value("${rotation.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${rotation.auto-rotation-enabled:false}")
    private boolean autoRotationEnabled;

    @Value("${rotation.auto-rotation-cron:0 0 0 * * ?}")
    private String autoRotationCron;

    @Value("${rotation.notification-endpoint:}")
    private String notificationEndpoint;

    @Value("${rotation.credential-rotation-threshold-days:90}")
    private int credentialRotationThresholdDays;

    @Value("${rotation.excluded-clients:}")
    private String excludedClients;

    /**
     * Creates and configures a RotationService bean for managing credential rotation processes
     *
     * @param conjurService Service for interacting with Conjur vault
     * @param notificationService Service for sending rotation notifications
     * @return Configured rotation service instance
     */
    @Bean
    public RotationService rotationService(ConjurService conjurService, NotificationService notificationService) {
        LOGGER.info("Initializing rotation service");
        return new RotationServiceImpl(conjurService, notificationService);
    }

    /**
     * Creates a NotificationService bean for sending notifications about rotation events
     *
     * @param metricsService Service for recording metrics about credential rotation
     * @return Service for sending rotation notifications
     */
    @Bean
    public NotificationService notificationService(MetricsService metricsService) {
        LOGGER.info("Initializing notification service");
        return new NotificationServiceImpl(metricsService);
    }

    /**
     * Creates a RestTemplate bean for HTTP communication with notification endpoints
     *
     * @return HTTP client for sending notifications
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}