package com.payment.sapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.payment.sapi.service.CacheService;
import com.payment.sapi.service.PaymentService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.sapi.service.TokenValidationService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test class for the Payment-Sapi application that verifies the Spring context loads correctly
 * and all required beans are properly configured.
 * 
 * This test ensures that the application can start up with the test configuration and validates
 * the core components of the token-based authentication system.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
@ActiveProfiles("test")
public class PaymentSapiApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private TokenValidationService tokenValidationService;
    
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private TokenRenewalService tokenRenewalService;
    
    @Autowired
    private CacheService cacheService;
    
    @Value("${token.audience}")
    private String tokenAudience;
    
    @Value("${token.issuers}")
    private String tokenIssuers;
    
    @Value("${token.renewal-enabled}")
    private boolean tokenRenewalEnabled;

    /**
     * Verifies that the Spring application context loads successfully
     */
    @Test
    public void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }
    
    /**
     * Verifies that all required service beans are properly configured and available
     * in the application context
     */
    @Test
    public void requiredBeansExist() {
        assertThat(tokenValidationService).isNotNull();
        assertThat(paymentService).isNotNull();
        assertThat(tokenRenewalService).isNotNull();
        assertThat(cacheService).isNotNull();
    }
    
    /**
     * Verifies that token configuration properties are correctly loaded from the test configuration
     */
    @Test
    public void tokenConfigurationLoaded() {
        assertThat(tokenAudience).isEqualTo("payment-sapi");
        assertThat(tokenIssuers).contains("payment-eapi");
        assertThat(tokenRenewalEnabled).isTrue();
    }
}