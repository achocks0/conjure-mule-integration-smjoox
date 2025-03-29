package com.payment.eapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test class for the Payment-Eapi application that verifies the Spring application context 
 * loads correctly with all required beans and configurations. This test ensures that the application
 * can start up properly with test configurations and validates the core components are wired correctly.
 */
@SpringBootTest(classes = PaymentEapiApplication.class)
@TestPropertySource(locations = "classpath:application-test.yml")
public class PaymentEapiApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Tests that the Spring application context loads successfully
     */
    @Test
    void contextLoads() {
        // Verify that the application context is not null
        assertNotNull(applicationContext, "Application context should not be null");
        
        // Verify that the application has started successfully
        assertTrue(applicationContext.isActive(), "Application context should be active");
    }

    /**
     * Tests that all required beans are properly configured and can be retrieved from the application context
     */
    @Test
    void testRequiredBeansExist() {
        // Verify that AuthenticationService bean exists
        assertTrue(applicationContext.containsBean("authenticationService"),
                "AuthenticationService bean should exist");
                
        // Verify that TokenService bean exists
        assertTrue(applicationContext.containsBean("tokenService"),
                "TokenService bean should exist");
                
        // Verify that ConjurService bean exists
        assertTrue(applicationContext.containsBean("conjurService"),
                "ConjurService bean should exist");
                
        // Verify that CacheService bean exists
        assertTrue(applicationContext.containsBean("cacheService"),
                "CacheService bean should exist");
                
        // Verify that MetricsService bean exists
        assertTrue(applicationContext.containsBean("metricsService"),
                "MetricsService bean should exist");
    }

    /**
     * Tests that configuration properties are correctly loaded from application-test.yml
     */
    @Test
    void testConfigurationProperties() {
        // Verify that token configuration properties are correctly loaded
        assertNotNull(applicationContext.getEnvironment().getProperty("token.issuer"),
                "token.issuer property should be defined");
        assertNotNull(applicationContext.getEnvironment().getProperty("token.audience"),
                "token.audience property should be defined");
        assertNotNull(applicationContext.getEnvironment().getProperty("token.expiration-seconds"),
                "token.expiration-seconds property should be defined");
        
        // Verify that conjur configuration properties are correctly loaded
        assertNotNull(applicationContext.getEnvironment().getProperty("conjur.url"),
                "conjur.url property should be defined");
        assertNotNull(applicationContext.getEnvironment().getProperty("conjur.account"),
                "conjur.account property should be defined");
        
        // Verify that backward compatibility configuration is enabled
        assertTrue(Boolean.parseBoolean(applicationContext.getEnvironment()
                .getProperty("payment.backward-compatibility.enabled", "false")),
                "Backward compatibility should be enabled in test environment");
    }
}