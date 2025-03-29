package com.payment.eapi.controller;

import com.payment.eapi.service.AuthenticationService;
import com.payment.eapi.service.ForwardingService;
import com.payment.eapi.model.Token;
import com.payment.eapi.exception.AuthenticationException;
import com.payment.common.model.ErrorResponse;
import com.payment.common.monitoring.MetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";
    private static final String PAYMENT_ID = "payment-123";
    private static final String TOKEN_STRING = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LWNsaWVudCIsImlhdCI6MTYyMzc1Nzg0NSwiZXhwIjoxNjIzNzYxNDQ1fQ.signature";

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private ForwardingService forwardingService;
    
    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private PaymentController paymentController;

    private Token mockToken;

    @BeforeEach
    void setUp() {
        // Setup mock token
        mockToken = Token.builder()
                .tokenString(TOKEN_STRING)
                .build();
        
        // Configure the AuthenticationService mock to return the token when authenticateWithHeaders is called
        when(authenticationService.authenticateWithHeaders(any()))
                .thenReturn(mockToken);
        
        // Configure the ForwardingService mock to return a successful response when forwarding requests
        when(forwardingService.forwardPostRequest(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
        when(forwardingService.forwardGetRequest(any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
    }

    @Test
    void testProcessPaymentSuccess() {
        // Create a test payment request object
        Object paymentRequest = createTestPaymentRequest();
        
        // Create headers with valid Client ID and Client Secret
        Map<String, String> headers = createTestHeaders(CLIENT_ID, CLIENT_SECRET);
        
        // Call the processPayment method on the controller
        ResponseEntity<?> response = paymentController.processPayment(paymentRequest, headers);
        
        // Verify that AuthenticationService.authenticateWithHeaders was called with the correct headers
        verify(authenticationService).authenticateWithHeaders(any());
        
        // Verify that ForwardingService.forwardPostRequest was called with the correct parameters
        verify(forwardingService).forwardPostRequest(eq("/internal/v1/payments"), eq(paymentRequest), eq(mockToken), any());
        
        // Verify that MetricsService.recordApiRequest was called
        verify(metricsService).recordApiRequest(eq("/payments"), eq("POST"), anyInt(), anyLong());
        
        // Assert that the response status is 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testProcessPaymentAuthenticationFailure() {
        // Create a test payment request object
        Object paymentRequest = createTestPaymentRequest();
        
        // Create headers with invalid Client ID and Client Secret
        Map<String, String> headers = createTestHeaders(CLIENT_ID, CLIENT_SECRET);
        
        // Configure AuthenticationService to throw AuthenticationException
        AuthenticationException authException = new AuthenticationException("Invalid credentials");
        when(authenticationService.authenticateWithHeaders(any()))
                .thenThrow(authException);
        
        // Call the processPayment method on the controller
        ResponseEntity<?> response = paymentController.processPayment(paymentRequest, headers);
        
        // Verify that AuthenticationService.authenticateWithHeaders was called
        verify(authenticationService).authenticateWithHeaders(any());
        
        // Verify that ForwardingService was not called
        verifyNoInteractions(forwardingService);
        
        // Assert that the response status is 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() instanceof ErrorResponse);
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("AUTH_ERROR", errorResponse.getErrorCode());
        assertTrue(errorResponse.getMessage().contains("Authentication failed"));
        assertNotNull(errorResponse.getRequestId());
        assertNotNull(errorResponse.getTimestamp());
    }
    
    @Test
    void testGetPaymentStatusSuccess() {
        // Create headers with valid Client ID and Client Secret
        Map<String, String> headers = createTestHeaders(CLIENT_ID, CLIENT_SECRET);
        
        // Call the getPaymentStatus method on the controller with a payment ID
        ResponseEntity<?> response = paymentController.getPaymentStatus(PAYMENT_ID, headers);
        
        // Verify that AuthenticationService.authenticateWithHeaders was called with the correct headers
        verify(authenticationService).authenticateWithHeaders(any());
        
        // Verify that ForwardingService.forwardGetRequest was called with the correct parameters
        verify(forwardingService).forwardGetRequest(eq("/internal/v1/payments/" + PAYMENT_ID), eq(mockToken), any());
        
        // Verify that MetricsService.recordApiRequest was called
        verify(metricsService).recordApiRequest(eq("/payments/{paymentId}"), eq("GET"), anyInt(), anyLong());
        
        // Assert that the response status is 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
    
    @Test
    void testGetPaymentStatusAuthenticationFailure() {
        // Create headers with invalid Client ID and Client Secret
        Map<String, String> headers = createTestHeaders(CLIENT_ID, CLIENT_SECRET);
        
        // Configure AuthenticationService to throw AuthenticationException
        AuthenticationException authException = new AuthenticationException("Invalid credentials");
        when(authenticationService.authenticateWithHeaders(any()))
                .thenThrow(authException);
        
        // Call the getPaymentStatus method on the controller with a payment ID
        ResponseEntity<?> response = paymentController.getPaymentStatus(PAYMENT_ID, headers);
        
        // Verify that AuthenticationService.authenticateWithHeaders was called
        verify(authenticationService).authenticateWithHeaders(any());
        
        // Verify that ForwardingService was not called
        verifyNoInteractions(forwardingService);
        
        // Assert that the response status is 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() instanceof ErrorResponse);
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("AUTH_ERROR", errorResponse.getErrorCode());
        assertTrue(errorResponse.getMessage().contains("Authentication failed"));
        assertNotNull(errorResponse.getRequestId());
        assertNotNull(errorResponse.getTimestamp());
    }
    
    @Test
    void testMissingCredentialsInHeaders() {
        // Create a test payment request object
        Object paymentRequest = createTestPaymentRequest();
        
        // Create headers without Client ID and Client Secret
        Map<String, String> headers = createTestHeaders(null, null);
        
        // Call the processPayment method on the controller
        ResponseEntity<?> response = paymentController.processPayment(paymentRequest, headers);
        
        // Verify that AuthenticationService was not called
        verifyNoInteractions(authenticationService);
        
        // Verify that ForwardingService was not called
        verifyNoInteractions(forwardingService);
        
        // Assert that the response status is 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() instanceof ErrorResponse);
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("AUTH_ERROR", errorResponse.getErrorCode());
        assertTrue(errorResponse.getMessage().contains("Missing required authentication headers"));
        assertNotNull(errorResponse.getRequestId());
        assertNotNull(errorResponse.getTimestamp());
    }
    
    private Map<String, String> createTestHeaders(String clientId, String clientSecret) {
        Map<String, String> headers = new HashMap<>();
        if (clientId != null) {
            headers.put("X-Client-ID", clientId);
        }
        if (clientSecret != null) {
            headers.put("X-Client-Secret", clientSecret);
        }
        return headers;
    }
    
    private Object createTestPaymentRequest() {
        Map<String, Object> payment = new HashMap<>();
        payment.put("amount", 100.00);
        payment.put("currency", "USD");
        payment.put("reference", "INV-12345");
        payment.put("description", "Payment for Invoice #12345");
        return payment;
    }
}