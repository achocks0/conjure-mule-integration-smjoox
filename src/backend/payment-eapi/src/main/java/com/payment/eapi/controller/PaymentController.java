package com.payment.eapi.controller;

import com.payment.eapi.service.AuthenticationService;
import com.payment.eapi.service.ForwardingService;
import com.payment.eapi.model.Token;
import com.payment.common.model.ErrorResponse;
import com.payment.common.monitoring.MetricsService;
import com.payment.eapi.exception.AuthenticationException;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

/**
 * REST controller that handles payment-related API endpoints in the Payment-Eapi component.
 * This controller implements the backward compatibility layer by accepting Client ID and
 * Client Secret in headers while using token-based authentication for internal service communication.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private static final String CLIENT_ID_HEADER = "X-Client-ID";
    private static final String CLIENT_SECRET_HEADER = "X-Client-Secret";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final AuthenticationService authenticationService;
    private final ForwardingService forwardingService;
    private final MetricsService metricsService;

    public PaymentController(AuthenticationService authenticationService,
                             ForwardingService forwardingService,
                             MetricsService metricsService) {
        this.authenticationService = authenticationService;
        this.forwardingService = forwardingService;
        this.metricsService = metricsService;
    }

    /**
     * Processes a payment request by authenticating the vendor and forwarding the request to Payment-Sapi
     *
     * @param paymentRequest The payment request body
     * @param headers HTTP request headers containing Client ID and Client Secret
     * @return Response from Payment-Sapi or error response
     */
    @PostMapping("/payments")
    public ResponseEntity<?> processPayment(@RequestBody Object paymentRequest,
                                           @RequestHeader Map<String, String> headers) {
        String requestId = generateRequestId();
        log.info("Received payment request with ID: {}", requestId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Extract Client ID and Client Secret from headers
            String clientId = headers.get(CLIENT_ID_HEADER);
            String clientSecret = headers.get(CLIENT_SECRET_HEADER);
            String correlationId = headers.getOrDefault(CORRELATION_ID_HEADER, requestId);
            
            if (clientId == null || clientSecret == null) {
                throw new AuthenticationException("Missing required authentication headers");
            }
            
            // Authenticate vendor
            Token token = authenticationService.authenticateWithHeaders(
                extractHeaders(clientId, clientSecret, correlationId));
            
            // Forward request to Payment-Sapi with token
            Map<String, String> additionalHeaders = new HashMap<>();
            additionalHeaders.put(CORRELATION_ID_HEADER, correlationId);
            
            ResponseEntity<?> response = forwardingService.forwardPostRequest(
                "/internal/v1/payments", paymentRequest, token, additionalHeaders);
            
            // Record metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest("/payments", "POST", response.getStatusCodeValue(), duration);
            
            return response;
        } catch (AuthenticationException e) {
            return handleAuthenticationError(requestId, e);
        } catch (Exception e) {
            // Log other errors
            log.error("Error processing payment request {}: {}", requestId, e.getMessage(), e);
            
            // Record metrics for failed request
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest("/payments", "POST", HttpStatus.INTERNAL_SERVER_ERROR.value(), duration);
            
            // Return generic error response
            ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("PAYMENT_ERROR")
                .message("An error occurred while processing the payment request")
                .requestId(requestId)
                .timestamp(new Date())
                .build();
                
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Retrieves the status of a payment by ID by authenticating the vendor and forwarding the request to Payment-Sapi
     *
     * @param paymentId The unique identifier of the payment
     * @param headers HTTP request headers containing Client ID and Client Secret
     * @return Response from Payment-Sapi or error response
     */
    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String paymentId,
                                            @RequestHeader Map<String, String> headers) {
        String requestId = generateRequestId();
        log.info("Received payment status request for ID: {} with request ID: {}", paymentId, requestId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Extract Client ID and Client Secret from headers
            String clientId = headers.get(CLIENT_ID_HEADER);
            String clientSecret = headers.get(CLIENT_SECRET_HEADER);
            String correlationId = headers.getOrDefault(CORRELATION_ID_HEADER, requestId);
            
            if (clientId == null || clientSecret == null) {
                throw new AuthenticationException("Missing required authentication headers");
            }
            
            // Authenticate vendor
            Token token = authenticationService.authenticateWithHeaders(
                extractHeaders(clientId, clientSecret, correlationId));
            
            // Forward request to Payment-Sapi with token
            Map<String, String> additionalHeaders = new HashMap<>();
            additionalHeaders.put(CORRELATION_ID_HEADER, correlationId);
            
            ResponseEntity<?> response = forwardingService.forwardGetRequest(
                "/internal/v1/payments/" + paymentId, token, additionalHeaders);
            
            // Record metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest("/payments/{paymentId}", "GET", response.getStatusCodeValue(), duration);
            
            return response;
        } catch (AuthenticationException e) {
            return handleAuthenticationError(requestId, e);
        } catch (Exception e) {
            // Log other errors
            log.error("Error retrieving payment status for ID {} with request {}: {}", 
                    paymentId, requestId, e.getMessage(), e);
            
            // Record metrics for failed request
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordApiRequest("/payments/{paymentId}", "GET", 
                    HttpStatus.INTERNAL_SERVER_ERROR.value(), duration);
            
            // Return generic error response
            ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("STATUS_ERROR")
                .message("An error occurred while retrieving the payment status")
                .requestId(requestId)
                .timestamp(new Date())
                .build();
                
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Creates a standardized error response for authentication failures
     *
     * @param requestId The unique identifier for the request
     * @param ex The exception that caused the authentication error
     * @return ResponseEntity with 401 Unauthorized response with error details
     */
    private ResponseEntity<ErrorResponse> handleAuthenticationError(String requestId, Exception ex) {
        log.error("Authentication error for request {}: {}", requestId, ex.getMessage());
        
        String errorCode = "AUTH_ERROR";
        if (ex instanceof AuthenticationException) {
            errorCode = ((AuthenticationException) ex).getErrorCode();
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode(errorCode)
            .message("Authentication failed: " + ex.getMessage())
            .requestId(requestId)
            .timestamp(new Date())
            .build();
            
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Extracts relevant headers from the request for authentication and forwarding
     *
     * @param clientId The Client ID extracted from the request headers
     * @param clientSecret The Client Secret extracted from the request headers
     * @param correlationId The Correlation ID for request tracking
     * @return Map of headers for authentication and request tracking
     */
    private Map<String, String> extractHeaders(String clientId, String clientSecret, String correlationId) {
        Map<String, String> headers = new HashMap<>();
        
        if (clientId != null) {
            headers.put(CLIENT_ID_HEADER, clientId);
        }
        
        if (clientSecret != null) {
            headers.put(CLIENT_SECRET_HEADER, clientSecret);
        }
        
        if (correlationId != null) {
            headers.put(CORRELATION_ID_HEADER, correlationId);
        }
        
        return headers;
    }

    /**
     * Generates a unique request ID for tracking and correlation
     *
     * @return Unique request ID
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }
}