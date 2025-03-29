package com.payment.sapi.controller;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.common.model.ErrorResponse;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * REST controller responsible for handling token-related operations in the Payment-Sapi component,
 * including token validation and renewal. This controller provides endpoints for validating
 * JWT tokens and renewing expired tokens to maintain secure service-to-service communication.
 */
@RestController
@RequestMapping("/internal/v1/tokens")
public class TokenController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenController.class);

    private final TokenValidationService tokenValidationService;
    private final TokenRenewalService tokenRenewalService;

    /**
     * Constructs a new TokenController with the required services
     *
     * @param tokenValidationService Service for validating JWT tokens
     * @param tokenRenewalService Service for renewing JWT tokens
     */
    public TokenController(TokenValidationService tokenValidationService, 
                          TokenRenewalService tokenRenewalService) {
        this.tokenValidationService = tokenValidationService;
        this.tokenRenewalService = tokenRenewalService;
    }

    /**
     * Validates a JWT token and checks if it has the required permission
     *
     * @param tokenString The JWT token string to validate
     * @param requiredPermission The permission required to access the requested resource
     * @return HTTP response containing validation result or error
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestBody String tokenString, 
                                         @RequestHeader(value = "X-Required-Permission", required = false) String requiredPermission) {
        LOGGER.debug("Token validation request received");
        
        ValidationResult result = tokenValidationService.validateToken(tokenString, requiredPermission);
        
        if (result.isValid()) {
            LOGGER.debug("Token validation successful");
            return ResponseEntity.ok(result);
        } else if (result.isExpired() && result.isRenewed()) {
            LOGGER.debug("Token expired but renewed successfully");
            return ResponseEntity.ok(result);
        } else if (result.isForbidden()) {
            LOGGER.warn("Token validation failed: Insufficient permissions");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse.builder()
                            .errorCode("INSUFFICIENT_PERMISSIONS")
                            .message(result.getErrorMessage())
                            .requestId(String.valueOf(System.currentTimeMillis()))
                            .timestamp(new Date())
                            .build());
        } else {
            LOGGER.warn("Token validation failed: {}", result.getErrorMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.builder()
                            .errorCode("INVALID_TOKEN")
                            .message(result.getErrorMessage())
                            .requestId(String.valueOf(System.currentTimeMillis()))
                            .timestamp(new Date())
                            .build());
        }
    }

    /**
     * Renews an expired or about-to-expire JWT token
     *
     * @param tokenString The JWT token string to renew
     * @return HTTP response containing renewed token or error
     */
    @PostMapping("/renew")
    public ResponseEntity<?> renewToken(@RequestBody String tokenString) {
        LOGGER.debug("Token renewal request received");
        
        Token token = tokenValidationService.parseToken(tokenString);
        if (token == null) {
            LOGGER.warn("Token renewal failed: Unable to parse token");
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.builder()
                            .errorCode("INVALID_TOKEN_FORMAT")
                            .message("Unable to parse token")
                            .requestId(String.valueOf(System.currentTimeMillis()))
                            .timestamp(new Date())
                            .build());
        }
        
        if (!tokenRenewalService.shouldRenew(token)) {
            LOGGER.debug("Token renewal not needed");
            return ResponseEntity.ok(ValidationResult.valid());
        }
        
        ValidationResult renewalResult = tokenRenewalService.renewToken(token);
        if (renewalResult.isRenewed()) {
            LOGGER.debug("Token renewed successfully");
            return ResponseEntity.ok(renewalResult);
        } else {
            LOGGER.warn("Token renewal failed: {}", renewalResult.getErrorMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .errorCode("RENEWAL_FAILED")
                            .message(renewalResult.getErrorMessage())
                            .requestId(String.valueOf(System.currentTimeMillis()))
                            .timestamp(new Date())
                            .build());
        }
    }
}