package com.payment.eapi;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.payment.eapi.config.SecurityConfig;
import com.payment.eapi.config.ConjurConfig;
import com.payment.eapi.config.RedisConfig;
import com.payment.eapi.config.DatabaseConfig;
import com.payment.common.config.MetricsConfig;

/**
 * Main application class for the Payment External API (Payment-Eapi) component of the
 * Payment API Security Enhancement project. This class serves as the entry point for
 * the Spring Boot application and enables the necessary configurations for security,
 * database, Redis cache, and Conjur vault integration.
 * 
 * The Payment-Eapi component implements the backward compatibility layer for existing
 * vendor integrations while enhancing security through Conjur vault credential management
 * and token-based authentication for internal service communications.
 */
@SpringBootApplication
@EnableScheduling
@Import({SecurityConfig.class, ConjurConfig.class, RedisConfig.class, DatabaseConfig.class, MetricsConfig.class})
public class PaymentEapiApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentEapiApplication.class);

    /**
     * Main method that bootstraps the Payment-Eapi application
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        LOGGER.info("Starting Payment-Eapi application - Payment API Security Enhancement");
        SpringApplication.run(PaymentEapiApplication.class, args);
        LOGGER.info("Payment-Eapi application started successfully");
    }
}