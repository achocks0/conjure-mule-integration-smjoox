package com.payment.rotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.payment.rotation.config.RotationConfig;
import com.payment.rotation.config.ConjurConfig;
import com.payment.common.config.MetricsConfig;

/**
 * Main application class for the Credential Rotation service.
 * <p>
 * This service is responsible for securely rotating authentication credentials stored in Conjur vault
 * without disrupting existing vendor integrations. It provides mechanisms to support multiple valid
 * credential versions during transition periods, ensuring zero-downtime rotation.
 * <p>
 * The service implements the following key features:
 * <ul>
 *   <li>Secure rotation of credentials with configurable transition periods</li>
 *   <li>Support for multiple valid credential versions during transitions</li>
 *   <li>Scheduled checks to automatically advance rotation state</li>
 *   <li>Integration with Conjur vault for secure credential storage</li>
 *   <li>Comprehensive metrics and monitoring for rotation operations</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@Import({RotationConfig.class, ConjurConfig.class, MetricsConfig.class})
public class CredentialRotationApplication {

    private static final Logger logger = LoggerFactory.getLogger(CredentialRotationApplication.class);

    /**
     * Main method that serves as the entry point for the Credential Rotation application.
     * <p>
     * This method bootstraps the Spring Boot application and initializes all required
     * components for the credential rotation service.
     *
     * @param args Command line arguments passed to the application
     */
    public static void main(String[] args) {
        logger.info("Starting Credential Rotation Service...");
        SpringApplication.run(CredentialRotationApplication.class, args);
        logger.info("Credential Rotation Service initialized successfully");
    }
}