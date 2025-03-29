package com.payment.rotation.config;

import com.payment.rotation.service.ConjurService;
import com.payment.rotation.service.impl.ConjurServiceImpl;
import com.payment.common.util.SecurityUtils;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.payment.common.retry.RetryHandler;
import com.payment.common.retry.ExponentialBackoffRetryHandler;
import com.cyberark.conjur.api.ConjurClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for Conjur vault integration in the Credential Rotation service.
 * This class provides the necessary beans and configuration for connecting to and 
 * interacting with the Conjur vault for secure credential management and rotation operations.
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
    
    @Value("${conjur.connection-timeout}")
    private int connectionTimeout;
    
    @Value("${conjur.read-timeout}")
    private int readTimeout;
    
    @Value("${conjur.retry-count}")
    private int retryCount;
    
    @Value("${conjur.retry-backoff-multiplier}")
    private double retryBackoffMultiplier;
    
    /**
     * Creates and configures a Conjur client bean for interacting with the Conjur vault.
     *
     * @return Configured Conjur client instance
     */
    @Bean
    public ConjurClient conjurClient() {
        LOGGER.info("Initializing Conjur client with URL: {}", conjurUrl);
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
            LOGGER.error("Failed to initialize Conjur client", e);
            throw new RuntimeException("Failed to initialize Conjur client", e);
        }
    }
    
    /**
     * Creates a ConjurService bean for credential management and rotation operations.
     *
     * @return Service for Conjur vault operations
     */
    @Bean
    public ConjurService conjurService() {
        return new ConjurServiceImpl(conjurClient(), retryHandler());
    }
    
    /**
     * Creates a RetryHandler bean for handling transient failures in Conjur operations.
     *
     * @return Retry handler with exponential backoff
     */
    @Bean
    public RetryHandler retryHandler() {
        return new ExponentialBackoffRetryHandler(retryCount, retryBackoffMultiplier);
    }
    
    /**
     * Reads the SSL certificate from the specified path.
     *
     * @param path Path to the certificate file
     * @return Certificate content as a string
     */
    private String readCertificate(String path) {
        try {
            return SecurityUtils.readCertificate(path);
        } catch (Exception e) {
            LOGGER.error("Failed to read certificate from path: {}", path, e);
            throw new RuntimeException("Failed to read certificate", e);
        }
    }
}