package com.payment.sapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.impl.TokenRenewalServiceImpl;

/**
 * Unit test class for the TokenRenewalService implementation.
 * Tests the token renewal functionality, including token renewal requests,
 * validation of renewal conditions, and handling of error scenarios.
 */
@ExtendWith(MockitoExtension.class)
public class TokenRenewalServiceTest {
    
    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private TokenRenewalServiceImpl tokenRenewalService;
    
    @BeforeEach
    void setUp() {
        // Initialize fields using ReflectionTestUtils as they are normally set via @Value
        ReflectionTestUtils.setField(tokenRenewalService, "eapiUrl", "https://payment-eapi.example.com");
        ReflectionTestUtils.setField(tokenRenewalService, "tokenRenewalEndpoint", "/api/v1/tokens/renew");
        ReflectionTestUtils.setField(tokenRenewalService, "renewalThresholdSeconds", 300);
    }
    
    @Test
    void testRenewToken_Success() {
        // Create a test token
        String tokenString = "test.token.string";
        Token token = Token.builder()
                .tokenString(tokenString)
                .jti("test-token-id")
                .build();
        
        // Create a renewed token to be returned by the mock
        String renewedTokenString = "renewed.token.string";
        Token renewedToken = Token.builder()
                .tokenString(renewedTokenString)
                .jti("renewed-token-id")
                .build();
        
        // Create a spy of the service
        TokenRenewalServiceImpl spyService = Mockito.spy(tokenRenewalService);
        
        // Stub the requestTokenRenewal method on the spy
        doReturn(renewedToken).when(spyService).requestTokenRenewal(tokenString);
        
        // Call the method under test
        ValidationResult result = spyService.renewToken(token);
        
        // Verify the result
        assertTrue(result.isValid());
        assertTrue(result.isRenewed());
        assertEquals(renewedTokenString, result.getRenewedTokenString());
        
        // Verify the cacheService was called
        verify(cacheService).storeToken(renewedToken);
    }
    
    @Test
    void testRenewToken_NullToken() {
        // Call the method under test with null token
        ValidationResult result = tokenRenewalService.renewToken(null);
        
        // Verify the result
        assertFalse(result.isValid());
        assertEquals("Cannot renew null token", result.getErrorMessage());
        
        // Verify cacheService was not called
        verifyNoInteractions(cacheService);
    }
    
    @Test
    void testRenewToken_RenewalFailed() {
        // Create a test token
        String tokenString = "test.token.string";
        Token token = Token.builder()
                .tokenString(tokenString)
                .jti("test-token-id")
                .build();
        
        // Create a spy of the service
        TokenRenewalServiceImpl spyService = Mockito.spy(tokenRenewalService);
        
        // Stub the requestTokenRenewal method on the spy to return null
        doReturn(null).when(spyService).requestTokenRenewal(tokenString);
        
        // Call the method under test
        ValidationResult result = spyService.renewToken(token);
        
        // Verify the result
        assertFalse(result.isValid());
        assertEquals("Token renewal failed", result.getErrorMessage());
        
        // Verify cacheService was not called
        verifyNoInteractions(cacheService);
    }
    
    @Test
    void testRequestTokenRenewal_Success() {
        // Create test token string
        String tokenString = "test.token.string";
        
        // Create response data
        String newTokenString = "new.token.string";
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("token", newTokenString);
        
        // Mock the RestTemplate response
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(responseMap, HttpStatus.OK);
        when(restTemplate.postForEntity(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(HttpEntity.class),
                ArgumentMatchers.eq(Map.class)
        )).thenReturn(responseEntity);
        
        // Call the method under test
        Token result = tokenRenewalService.requestTokenRenewal(tokenString);
        
        // Verify the result
        assertNotNull(result);
        assertEquals(newTokenString, result.getTokenString());
    }
    
    @Test
    void testRequestTokenRenewal_ErrorResponse() {
        // Create test token string
        String tokenString = "test.token.string";
        
        // Mock the RestTemplate to return an error
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        when(restTemplate.postForEntity(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(HttpEntity.class),
                ArgumentMatchers.eq(Map.class)
        )).thenReturn(responseEntity);
        
        // Call the method under test
        Token result = tokenRenewalService.requestTokenRenewal(tokenString);
        
        // Verify the result
        assertNull(result);
    }
    
    @Test
    void testRequestTokenRenewal_Exception() {
        // Create test token string
        String tokenString = "test.token.string";
        
        // Mock the RestTemplate to throw an exception
        when(restTemplate.postForEntity(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(HttpEntity.class),
                ArgumentMatchers.eq(Map.class)
        )).thenThrow(new RuntimeException("Connection error"));
        
        // Call the method under test
        Token result = tokenRenewalService.requestTokenRenewal(tokenString);
        
        // Verify the result
        assertNull(result);
    }
    
    @Test
    void testShouldRenew_NullToken() {
        // Call the method under test with null token
        boolean result = tokenRenewalService.shouldRenew(null);
        
        // Verify the result
        assertFalse(result);
    }
    
    @Test
    void testShouldRenew_ExpiredToken() {
        // Create an expired token
        Token token = Token.builder().build();
        
        // Mock the isExpired method
        when(token.isExpired()).thenReturn(true);
        
        // Call the method under test
        boolean result = tokenRenewalService.shouldRenew(token);
        
        // Verify the result
        assertTrue(result);
    }
    
    @Test
    void testShouldRenew_TokenExpiringWithinThreshold() {
        // Create a token with an expiration time within the renewal threshold
        Date expirationTime = new Date(System.currentTimeMillis() + 100000); // 100 seconds from now
        Token token = Token.builder()
                .expirationTime(expirationTime)
                .build();
        
        // Mock the isExpired method
        when(token.isExpired()).thenReturn(false);
        
        // Call the method under test
        boolean result = tokenRenewalService.shouldRenew(token);
        
        // Verify the result (should be true as 100 seconds is within the 300 second threshold)
        assertTrue(result);
    }
    
    @Test
    void testShouldRenew_TokenNotExpiringWithinThreshold() {
        // Create a token with an expiration time beyond the renewal threshold
        Date expirationTime = new Date(System.currentTimeMillis() + 600000); // 600 seconds from now
        Token token = Token.builder()
                .expirationTime(expirationTime)
                .build();
        
        // Mock the isExpired method
        when(token.isExpired()).thenReturn(false);
        
        // Call the method under test
        boolean result = tokenRenewalService.shouldRenew(token);
        
        // Verify the result (should be false as 600 seconds is beyond the 300 second threshold)
        assertFalse(result);
    }
}