package com.payment.eapi.config;

import com.cyberark.conjur.api.ConjurClient;
import com.payment.common.retry.RetryHandler;
import com.payment.common.retry.impl.ExponentialBackoffRetryHandler;
import com.payment.common.util.SecurityUtils;
import com.payment.eapi.service.ConjurService;
import com.payment.eapi.service.impl.ConjurServiceImpl;
import com.payment.eapi.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Configuration class for Conjur vault integration in the Payment API Security Enhancement project.
 * This class configures the Conjur client, retry handler, and related components needed
 * for secure credential storage and retrieval.
 */
@Configuration
public class ConjurConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurConfig.class);

    @Value("${conjur.url}")
    private String conjurUrl;

    @Value("${conjur.account}")
    private String conjurAccount;

    @Value("${conjur.authn-login}")
    private String conjurAuthnLogin;

    @Value("${conjur.ssl-certificate}")
    private String conjurSslCertificate;

    @Value("${conjur.connection-timeout:5000}")
    private int connectionTimeout;

    @Value("${conjur.read-timeout:3000}")
    private int readTimeout;

    @Value("${conjur.retry-count:3}")
    private int retryCount;

    @Value("${conjur.retry-backoff-multiplier:1.5}")
    private double retryBackoffMultiplier;

    /**
     * Creates and configures a Conjur client bean for interacting with Conjur vault.
     *
     * @return Configured Conjur client instance
     */
    @Bean
    public ConjurClient conjurClient() {
        LOGGER.info("Creating Conjur client with URL: {}", SecurityUtils.maskSensitiveData(conjurUrl));
        try {
            return ConjurClient.builder()
                .url(conjurUrl)
                .account(conjurAccount)
                .login(conjurAuthnLogin)
                .sslCertificate(readCertificate(conjurSslCertificate))
                .connectionTimeout(connectionTimeout)
                .readTimeout(readTimeout)
                .build();
        } catch (Exception e) {
            LOGGER.error("Failed to create Conjur client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Conjur client", e);
        }
    }

    /**
     * Creates a retry handler bean for handling transient failures in Conjur operations.
     *
     * @return Configured retry handler instance
     */
    @Bean
    public RetryHandler retryHandler() {
        LOGGER.info("Creating retry handler with retry count: {} and backoff multiplier: {}", 
                retryCount, retryBackoffMultiplier);
        return new ExponentialBackoffRetryHandler(retryCount, retryBackoffMultiplier);
    }

    /**
     * Creates a ConjurService bean for secure credential management operations.
     *
     * @param conjurClient the client for interacting with Conjur vault
     * @param retryHandler the handler for retrying operations on transient failures
     * @param cacheService the service for caching credentials and tokens
     * @return Configured ConjurService instance
     */
    @Bean
    public ConjurService conjurService(ConjurClient conjurClient, RetryHandler retryHandler, CacheService cacheService) {
        LOGGER.info("Creating Conjur service");
        return new ConjurServiceImpl(conjurClient, retryHandler, cacheService);
    }

    /**
     * Reads the Conjur SSL certificate from the configured path.
     *
     * @param path the path to the certificate file
     * @return Certificate content as a string
     */
    private String readCertificate(String path) {
        LOGGER.debug("Reading certificate from path: {}", path);
        
        // Check if the input is already a PEM-encoded certificate
        if (path != null && path.trim().startsWith("-----BEGIN CERTIFICATE-----")) {
            LOGGER.debug("Using provided PEM certificate directly");
            return path;
        }
        
        try {
            return Files.readString(Paths.get(path));
        } catch (IOException e) {
            LOGGER.error("Failed to read certificate from path: {}, error: {}", path, e.getMessage(), e);
            throw new RuntimeException("Failed to read certificate", e);
        }
    }
}