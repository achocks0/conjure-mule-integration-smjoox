package com.payment.sapi;

import com.payment.sapi.config.SecurityConfig;
import com.payment.sapi.config.DatabaseConfig;
import com.payment.sapi.config.RedisConfig;
import com.payment.common.config.MetricsConfig;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.SpringApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for the Payment-Sapi component that serves as the entry point
 * for the internal payment service. This class bootstraps the Spring Boot application
 * and configures necessary components for token-based authentication, database connectivity,
 * Redis caching, and metrics collection.
 * <p>
 * This component is part of the Payment API Security Enhancement project that implements
 * token-based authentication for internal service communication while maintaining
 * backward compatibility with existing vendor integrations.
 */
@SpringBootApplication
@EnableCaching
@Import({SecurityConfig.class, DatabaseConfig.class, RedisConfig.class, MetricsConfig.class})
public class PaymentSapiApplication {

    private static final Logger logger = LoggerFactory.getLogger(PaymentSapiApplication.class);

    /**
     * Main method that serves as the entry point for the Payment-Sapi application.
     * Initializes the Spring Boot application context and starts the embedded web server.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        logger.info("Starting Payment-Sapi application...");
        
        try {
            SpringApplication.run(PaymentSapiApplication.class, args);
            logger.info("Payment-Sapi application started successfully");
        } catch (Exception e) {
            logger.error("Failed to start Payment-Sapi application", e);
            throw e;
        }
    }
}