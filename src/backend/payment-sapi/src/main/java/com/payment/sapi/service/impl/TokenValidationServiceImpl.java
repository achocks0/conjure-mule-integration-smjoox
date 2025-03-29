package com.payment.sapi.service.impl;

import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.common.util.TokenValidator;
import com.payment.common.model.TokenClaims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Base64;
import java.util.List;

/**
 * Implementation of the TokenValidationService interface that provides functionality
 * for validating JWT tokens used for authentication between Payment-Eapi and Payment-Sapi services.
 * This class validates token signatures, expiration, and permissions to ensure secure
 * service-to-service communication.
 */
@Service
public class TokenValidationServiceImpl implements TokenValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenValidationServiceImpl.class);
    
    private final byte[] tokenSigningKey;
    private final String tokenAudience;
    private final List<String> allowedIssuers;
    private final boolean tokenRenewalEnabled;
    private final TokenRenewalService tokenRenewalService;
    
    /**
     * Constructs a new TokenValidationServiceImpl with the specified dependencies and configuration.
     *
     * @param tokenRenewalService Service for token renewal
     * @param tokenSigningKey Key used to validate token signatures
     * @param tokenAudience Expected audience value for tokens
     * @param allowedIssuers List of allowed token issuers
     * @param tokenRenewalEnabled Flag indicating if token renewal is enabled
     */
    public TokenValidationServiceImpl(
            TokenRenewalService tokenRenewalService,
            @Value("${token.verification-key-path}") byte[] tokenSigningKey,
            @Value("${token.audience}") String tokenAudience,
            @Value("${token.issuers}") List<String> allowedIssuers,
            @Value("${token.renewal-enabled:false}") boolean tokenRenewalEnabled) {
        this.tokenRenewalService = tokenRenewalService;
        this.tokenSigningKey = tokenSigningKey;
        this.tokenAudience = tokenAudience;
        this.allowedIssuers = allowedIssuers;
        this.tokenRenewalEnabled = tokenRenewalEnabled;
    }

    /**
     * Validates a JWT token string and checks if it has the required permission.
     * This is the main entry point for token validation in the Payment-Sapi component.
     *
     * @param tokenString the JWT token string to validate
     * @param requiredPermission the permission required to access the requested resource
     * @return ValidationResult containing the validation status, error messages, and renewed token if applicable
     */
    @Override
    public ValidationResult validateToken(String tokenString, String requiredPermission) {
        LOGGER.debug("Validating token with required permission: {}", requiredPermission);
        
        try {
            // Check if token string is valid
            if (tokenString == null || tokenString.isEmpty()) {
                LOGGER.warn("Token string is null or empty");
                return ValidationResult.invalid("Token string is null or empty");
            }
            
            // Parse the token
            Token token = parseToken(tokenString);
            if (token == null) {
                LOGGER.warn("Failed to parse token");
                return ValidationResult.invalid("Invalid token format");
            }
            
            // Validate signature
            if (!validateTokenSignature(tokenString)) {
                LOGGER.warn("Token signature validation failed");
                return ValidationResult.invalid("Invalid token signature");
            }
            
            // Check if token is expired
            if (!validateTokenExpiration(token)) {
                LOGGER.debug("Token has expired");
                
                // If token renewal is enabled, try to renew it
                if (tokenRenewalEnabled && tokenRenewalService != null) {
                    LOGGER.debug("Attempting to renew expired token");
                    try {
                        ValidationResult renewalResult = tokenRenewalService.renewToken(token);
                        if (renewalResult.isRenewed()) {
                            LOGGER.debug("Token successfully renewed");
                            return renewalResult;
                        } else {
                            LOGGER.warn("Token renewal failed: {}", renewalResult.getErrorMessage());
                            return ValidationResult.expired("Token expired and renewal failed: " + 
                                                          renewalResult.getErrorMessage());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error during token renewal", e);
                        return ValidationResult.expired("Token expired and renewal error occurred");
                    }
                } else {
                    LOGGER.debug("Token renewal not enabled or service not available");
                    return ValidationResult.expired("Token has expired");
                }
            }
            
            // Check if token has required permission
            if (requiredPermission != null && !requiredPermission.isEmpty()) {
                if (!validateTokenPermission(token, requiredPermission)) {
                    LOGGER.warn("Token does not have required permission: {}", requiredPermission);
                    return ValidationResult.forbidden("Token does not have required permission: " + 
                                                     requiredPermission);
                }
            }
            
            // All validations passed
            LOGGER.debug("Token validation successful");
            return ValidationResult.valid();
        } catch (Exception e) {
            LOGGER.error("Unexpected error during token validation", e);
            return ValidationResult.invalid("An error occurred during token validation: " + e.getMessage());
        }
    }

    /**
     * Parses a JWT token string and creates a Token object.
     * This method extracts claims from the token string and constructs a Token instance.
     *
     * @param tokenString the JWT token string to parse
     * @return the parsed Token object, or null if parsing fails
     */
    @Override
    public Token parseToken(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            LOGGER.warn("Token string is null or empty");
            return null;
        }
        
        try {
            // Use the TokenValidator to parse the token
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return null;
            }
            
            // Build the Token object from the claims
            return Token.builder()
                    .tokenString(tokenString)
                    .claims(claims)
                    .expirationTime(claims.getExp())
                    .jti(claims.getJti())
                    .clientId(claims.getSub())
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error parsing token", e);
            return null;
        }
    }

    /**
     * Validates the signature of a JWT token to ensure it hasn't been tampered with.
     *
     * @param tokenString the JWT token string to validate
     * @return true if the signature is valid, false otherwise
     */
    @Override
    public boolean validateTokenSignature(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            LOGGER.warn("Token string is null or empty");
            return false;
        }
        
        try {
            // Use the TokenValidator to validate the token's signature
            return TokenValidator.validateToken(tokenString, tokenSigningKey);
        } catch (Exception e) {
            LOGGER.error("Error validating token signature", e);
            return false;
        }
    }

    /**
     * Checks if a token has expired based on its expiration time.
     *
     * @param token the Token object to check
     * @return true if the token is not expired, false if it is expired
     */
    @Override
    public boolean validateTokenExpiration(Token token) {
        if (token == null) {
            LOGGER.warn("Token is null");
            return false;
        }
        
        try {
            // Check if the token is expired
            return !token.isExpired();
        } catch (Exception e) {
            LOGGER.error("Error validating token expiration", e);
            return false;
        }
    }

    /**
     * Checks if a token has the required permission to access a resource.
     *
     * @param token the Token object to check
     * @param requiredPermission the permission required to access the resource
     * @return true if the token has the required permission, false otherwise
     */
    @Override
    public boolean validateTokenPermission(Token token, String requiredPermission) {
        if (token == null) {
            LOGGER.warn("Token is null");
            return false;
        }
        
        if (requiredPermission == null || requiredPermission.isEmpty()) {
            // No permission required, so validation passes
            return true;
        }
        
        try {
            // Check if the token has the required permission
            return token.hasPermission(requiredPermission);
        } catch (Exception e) {
            LOGGER.error("Error validating token permission", e);
            return false;
        }
    }
}