package com.payment.monitoring;

import com.payment.monitoring.config.MonitoringConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Payment API Monitoring module.
 * This class serves as the entry point for the Spring Boot application that provides
 * comprehensive monitoring, metrics collection, and alerting capabilities for the
 * Payment API Security Enhancement project. It enables monitoring of authentication,
 * token operations, API performance, and system health metrics.
 */
@SpringBootApplication
@EnableScheduling
public class MonitoringApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringApplication.class);
    
    /**
     * Main method that serves as the entry point for the Spring Boot application
     * 
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        logger.info("Starting Payment API Monitoring Application...");
        SpringApplication.run(MonitoringApplication.class, args);
        logger.info("Payment API Monitoring Application initialized successfully");
    }
}