package com.payment.sapi.controller;

import com.payment.sapi.model.PaymentRequest;
import com.payment.sapi.model.PaymentResponse;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.model.Token;
import com.payment.sapi.service.PaymentService;
import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.exception.PaymentProcessingException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * REST controller that handles payment processing requests in the Payment-Sapi component.
 * Provides endpoints for processing payments, retrieving payment status, and managing
 * payment transactions with token-based authentication.
 */
@RestController
@RequestMapping("/internal/v1/payments")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final TokenValidationService tokenValidationService;
    
    /**
     * Constructor that injects required dependencies
     */
    public PaymentController(PaymentService paymentService, TokenValidationService tokenValidationService) {
        this.paymentService = paymentService;
        this.tokenValidationService = tokenValidationService;
    }

    /**
     * Processes a payment request after validating the authentication token
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody @Valid PaymentRequest paymentRequest,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        log.debug("Received payment processing request with amount: {} {}", 
                paymentRequest.getAmount(), paymentRequest.getCurrency());
        
        try {
            String tokenString = extractTokenFromHeader(authorizationHeader);
            ResponseEntity<?> validationResponse = validateToken(tokenString, "process_payment");
            if (validationResponse != null) {
                return ResponseEntity.status(validationResponse.getStatusCode()).build();
            }
            
            String clientId = getClientIdFromToken(tokenString);
            PaymentResponse paymentResponse = paymentService.processPayment(paymentRequest, clientId);
            
            log.info("Successfully processed payment with ID: {} for client: {}", 
                    paymentResponse.getPaymentId(), clientId);
            
            return ResponseEntity.ok(paymentResponse);
        } catch (PaymentProcessingException e) {
            log.error("Payment processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(PaymentResponse.builder()
                            .status("FAILED")
                            .reference(paymentRequest.getReference())
                            .build());
        } catch (Exception e) {
            log.error("Unexpected error during payment processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves the status of a payment by its ID after validating the authentication token
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(
            @PathVariable String paymentId,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        log.debug("Received request to get payment status for ID: {}", paymentId);
        
        try {
            String tokenString = extractTokenFromHeader(authorizationHeader);
            ResponseEntity<?> validationResponse = validateToken(tokenString, "view_payment");
            if (validationResponse != null) {
                return ResponseEntity.status(validationResponse.getStatusCode()).build();
            }
            
            String clientId = getClientIdFromToken(tokenString);
            Optional<PaymentResponse> paymentResponse = paymentService.getPaymentStatus(paymentId, clientId);
            
            if (paymentResponse.isPresent()) {
                log.info("Retrieved payment status for ID: {} and client: {}", paymentId, clientId);
                return ResponseEntity.ok(paymentResponse.get());
            } else {
                log.info("Payment with ID: {} not found for client: {}", paymentId, clientId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving payment status for ID: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves all payments for the authenticated client
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentsByClient(
            @RequestHeader("Authorization") String authorizationHeader) {
        
        log.debug("Received request to get all payments for client");
        
        try {
            String tokenString = extractTokenFromHeader(authorizationHeader);
            ResponseEntity<?> validationResponse = validateToken(tokenString, "view_payment");
            if (validationResponse != null) {
                return ResponseEntity.status(validationResponse.getStatusCode()).build();
            }
            
            String clientId = getClientIdFromToken(tokenString);
            List<PaymentResponse> payments = paymentService.getPaymentsByClient(clientId);
            
            log.info("Retrieved {} payments for client: {}", payments.size(), clientId);
            return ResponseEntity.ok(payments);
        } catch (Exception e) {
            log.error("Error retrieving payments for client", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Retrieves a payment by its reference for the authenticated client
     */
    @GetMapping("/reference/{reference}")
    public ResponseEntity<PaymentResponse> getPaymentByReference(
            @PathVariable String reference,
            @RequestHeader("Authorization") String authorizationHeader) {
        
        log.debug("Received request to get payment by reference: {}", reference);
        
        try {
            String tokenString = extractTokenFromHeader(authorizationHeader);
            ResponseEntity<?> validationResponse = validateToken(tokenString, "view_payment");
            if (validationResponse != null) {
                return ResponseEntity.status(validationResponse.getStatusCode()).build();
            }
            
            String clientId = getClientIdFromToken(tokenString);
            Optional<PaymentResponse> paymentResponse = paymentService.getPaymentByReference(reference, clientId);
            
            if (paymentResponse.isPresent()) {
                log.info("Retrieved payment for reference: {} and client: {}", reference, clientId);
                return ResponseEntity.ok(paymentResponse.get());
            } else {
                log.info("Payment with reference: {} not found for client: {}", reference, clientId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving payment by reference: {}", reference, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Extracts the JWT token from the Authorization header
     */
    private String extractTokenFromHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("Invalid Authorization header format");
            throw new IllegalArgumentException("Invalid Authorization header format. Expected 'Bearer {token}'");
        }
        return authorizationHeader.substring(7);
    }

    /**
     * Validates the token and required permission
     */
    private ResponseEntity<?> validateToken(String tokenString, String requiredPermission) {
        ValidationResult validationResult = tokenValidationService.validateToken(tokenString, requiredPermission);
        
        if (!validationResult.isValid()) {
            log.warn("Token validation failed: {}", validationResult.getErrorMessage());
            
            if (validationResult.isExpired()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            if (validationResult.isForbidden()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        return null;
    }

    /**
     * Extracts the client ID from the token
     */
    private String getClientIdFromToken(String tokenString) {
        Token token = tokenValidationService.parseToken(tokenString);
        if (token == null) {
            throw new IllegalArgumentException("Failed to parse token");
        }
        return token.getSubject();
    }
}