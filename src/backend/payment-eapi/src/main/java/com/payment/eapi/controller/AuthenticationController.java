package com.payment.eapi.controller;

import com.payment.eapi.service.AuthenticationService;
import com.payment.eapi.model.AuthenticationRequest;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.model.Token;
import com.payment.eapi.exception.AuthenticationException;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Date;

/**
 * REST controller that handles authentication requests for the Payment API Security Enhancement project.
 * Provides endpoints for authenticating vendors, validating tokens, and refreshing expired tokens.
 * This controller maintains backward compatibility with existing vendor integrations while
 * implementing enhanced security internally using token-based authentication.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);
    
    private final AuthenticationService authenticationService;

    /**
     * Constructor for AuthenticationController with dependency injection
     * 
     * @param authenticationService Service for handling authentication operations
     */
    @Autowired
    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticates a vendor using Client ID and Client Secret provided in the request body
     *
     * @param request Authentication request containing client credentials
     * @return Authentication response with JWT token and expiration information
     */
    @PostMapping("/token")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request) {
        logger.info("Authentication attempt for client ID: {}", request.getClientId());
        
        try {
            AuthenticationResponse response = authenticationService.authenticate(request);
            logger.info("Authentication successful for client ID: {}", request.getClientId());
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            logger.warn("Authentication failed for client ID: {}: {}", request.getClientId(), e.getMessage());
            throw e; // Will be handled by GlobalExceptionHandler
        }
    }

    /**
     * Authenticates a vendor using Client ID and Client Secret provided in request headers.
     * This endpoint maintains backward compatibility with existing vendor integrations.
     *
     * @param clientId Client ID from X-Client-ID header
     * @param clientSecret Client Secret from X-Client-Secret header
     * @return Authentication response with JWT token and expiration information
     */
    @PostMapping("/header-token")
    public ResponseEntity<AuthenticationResponse> authenticateWithHeaders(
            @RequestHeader("X-Client-ID") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret) {
        
        logger.info("Authentication attempt with headers for client ID: {}", clientId);
        
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Client-ID", clientId);
            headers.put("X-Client-Secret", clientSecret);
            
            Token token = authenticationService.authenticateWithHeaders(headers);
            AuthenticationResponse response = AuthenticationResponse.fromToken(token);
            
            logger.info("Authentication with headers successful for client ID: {}", clientId);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException e) {
            logger.warn("Authentication with headers failed for client ID: {}: {}", clientId, e.getMessage());
            throw e; // Will be handled by GlobalExceptionHandler
        }
    }

    /**
     * Validates a JWT token
     *
     * @param tokenString Token to validate
     * @return Boolean indicating if the token is valid
     */
    @PostMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestBody String tokenString) {
        logger.debug("Token validation attempt");
        
        try {
            boolean isValid = authenticationService.validateToken(tokenString);
            return ResponseEntity.ok(isValid);
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return ResponseEntity.ok(false);
        }
    }

    /**
     * Refreshes an expired token
     *
     * @param tokenString Expired token to refresh
     * @return Authentication response with new JWT token and expiration information
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refreshToken(@RequestBody String tokenString) {
        logger.info("Token refresh attempt");
        
        try {
            Optional<Token> refreshedToken = authenticationService.refreshToken(tokenString);
            
            if (refreshedToken.isPresent()) {
                AuthenticationResponse response = AuthenticationResponse.fromToken(refreshedToken.get());
                logger.info("Token refresh successful");
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Token refresh failed - token not eligible for refresh");
                throw new AuthenticationException("Token refresh failed");
            }
        } catch (AuthenticationException e) {
            logger.warn("Token refresh failed: {}", e.getMessage());
            throw e; // Will be handled by GlobalExceptionHandler
        }
    }

    /**
     * Checks if a token is valid and returns its status
     *
     * @param tokenId Token ID to check
     * @return Map containing token status information
     */
    @GetMapping("/status/{tokenId}")
    public ResponseEntity<Map<String, Object>> checkTokenStatus(@PathVariable String tokenId) {
        logger.debug("Token status check for token ID: {}", tokenId);
        
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean isValid = authenticationService.validateToken(tokenId);
            status.put("valid", isValid);
            
            if (isValid) {
                // If we have additional token information, add it to the response
                try {
                    // This is a simplified placeholder - actual implementation would extract
                    // expiration and other details from the token via authenticationService
                    status.put("expiresIn", "N/A");
                } catch (Exception e) {
                    logger.debug("Could not extract token expiration: {}", e.getMessage());
                }
            }
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.warn("Token status check failed: {}", e.getMessage());
            status.put("valid", false);
            status.put("reason", "Error processing token");
            return ResponseEntity.ok(status);
        }
    }
}