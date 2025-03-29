package com.payment.common.util;

import com.payment.common.model.TokenClaims;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.List;

/**
 * A utility class responsible for validating JWT (JSON Web Token) tokens used for authentication
 * between services in the Payment API Security Enhancement project. This class provides methods
 * to verify token signatures, parse token claims, and validate various aspects of tokens such as
 * expiration, permissions, audience, and issuer.
 */
public class TokenValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenValidator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Validates a JWT token by verifying its signature and format
     * 
     * @param token      The JWT token to validate
     * @param signingKey The key used to verify the token's signature
     * @return true if the token is valid, false otherwise
     */
    public static boolean validateToken(String token, byte[] signingKey) {
        if (token == null || signingKey == null) {
            LOGGER.warn("Token or signing key is null");
            return false;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Invalid token format: expected 3 parts but got {}", parts.length);
                return false;
            }

            String dataToSign = parts[0] + "." + parts[1];
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            
            byte[] expectedSignature = SecurityUtils.generateHmac(dataToSign.getBytes(), signingKey);
            
            return SecurityUtils.constantTimeEquals(expectedSignature, signature);
        } catch (Exception e) {
            LOGGER.warn("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses a JWT token into a TokenClaims object
     * 
     * @param token The JWT token to parse
     * @return Parsed token claims or null if parsing fails
     */
    public static TokenClaims parseToken(String token) {
        if (token == null || token.isEmpty()) {
            LOGGER.warn("Token is null or empty");
            return null;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Invalid token format: expected 3 parts but got {}", parts.length);
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            return OBJECT_MAPPER.readValue(payload, TokenClaims.class);
        } catch (JsonProcessingException e) {
            LOGGER.warn("Error parsing token payload: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Error decoding token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validates that a token has not expired
     * 
     * @param claims The token claims to validate
     * @return true if the token is not expired, false otherwise
     */
    public static boolean validateTokenExpiration(TokenClaims claims) {
        if (claims == null) {
            LOGGER.warn("Token claims are null");
            return false;
        }

        try {
            boolean expired = claims.isExpired();
            if (expired) {
                LOGGER.debug("Token has expired");
            }
            return !expired;
        } catch (Exception e) {
            LOGGER.warn("Error validating token expiration: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a token has a specific permission
     * 
     * @param claims             The token claims to validate
     * @param requiredPermission The permission required to access a resource
     * @return true if the token has the required permission, false otherwise
     */
    public static boolean validateTokenPermission(TokenClaims claims, String requiredPermission) {
        if (claims == null || requiredPermission == null || requiredPermission.isEmpty()) {
            LOGGER.warn("Claims or required permission is null or empty");
            return false;
        }

        try {
            boolean hasPermission = claims.hasPermission(requiredPermission);
            if (!hasPermission) {
                LOGGER.debug("Token does not have required permission: {}", requiredPermission);
            }
            return hasPermission;
        } catch (Exception e) {
            LOGGER.warn("Error validating token permission: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a token has the expected audience
     * 
     * @param claims           The token claims to validate
     * @param expectedAudience The expected audience of the token
     * @return true if the token audience matches the expected audience, false otherwise
     */
    public static boolean validateTokenAudience(TokenClaims claims, String expectedAudience) {
        if (claims == null || expectedAudience == null || expectedAudience.isEmpty()) {
            LOGGER.warn("Claims or expected audience is null or empty");
            return false;
        }

        try {
            String audience = claims.getAud();
            if (audience == null) {
                LOGGER.debug("Token audience is null");
                return false;
            }

            boolean validAudience = audience.equals(expectedAudience);
            if (!validAudience) {
                LOGGER.debug("Token audience '{}' does not match expected audience '{}'", audience, expectedAudience);
            }
            return validAudience;
        } catch (Exception e) {
            LOGGER.warn("Error validating token audience: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates that a token issuer is in the list of allowed issuers
     * 
     * @param claims         The token claims to validate
     * @param allowedIssuers List of issuers that are allowed to issue tokens
     * @return true if the token issuer is in the allowed issuers list, false otherwise
     */
    public static boolean validateTokenIssuer(TokenClaims claims, List<String> allowedIssuers) {
        if (claims == null || allowedIssuers == null || allowedIssuers.isEmpty()) {
            LOGGER.warn("Claims or allowed issuers list is null or empty");
            return false;
        }

        try {
            String issuer = claims.getIss();
            if (issuer == null) {
                LOGGER.debug("Token issuer is null");
                return false;
            }

            boolean validIssuer = allowedIssuers.contains(issuer);
            if (!validIssuer) {
                LOGGER.debug("Token issuer '{}' is not in the allowed issuers list", issuer);
            }
            return validIssuer;
        } catch (Exception e) {
            LOGGER.warn("Error validating token issuer: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validates a token's signature and claims in one operation
     * 
     * @param token              The JWT token to validate
     * @param signingKey         The key used to verify the token's signature
     * @param expectedAudience   The expected audience of the token
     * @param allowedIssuers     List of issuers that are allowed to issue tokens
     * @param requiredPermission The permission required to access a resource (can be null)
     * @return true if the token is valid and all claims are valid, false otherwise
     */
    public static boolean validateTokenAndClaims(String token, byte[] signingKey, String expectedAudience, 
                                                List<String> allowedIssuers, String requiredPermission) {
        if (token == null || signingKey == null || expectedAudience == null || allowedIssuers == null) {
            LOGGER.warn("One or more required parameters are null");
            return false;
        }

        try {
            // Validate token signature
            if (!validateToken(token, signingKey)) {
                LOGGER.debug("Token signature validation failed");
                return false;
            }

            // Parse token claims
            TokenClaims claims = parseToken(token);
            if (claims == null) {
                LOGGER.debug("Failed to parse token claims");
                return false;
            }

            // Validate token expiration
            if (!validateTokenExpiration(claims)) {
                LOGGER.debug("Token has expired");
                return false;
            }

            // Validate token audience
            if (!validateTokenAudience(claims, expectedAudience)) {
                LOGGER.debug("Token audience validation failed");
                return false;
            }

            // Validate token issuer
            if (!validateTokenIssuer(claims, allowedIssuers)) {
                LOGGER.debug("Token issuer validation failed");
                return false;
            }

            // Validate token permission (if required)
            if (requiredPermission != null && !requiredPermission.isEmpty()) {
                if (!validateTokenPermission(claims, requiredPermission)) {
                    LOGGER.debug("Token permission validation failed");
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            LOGGER.warn("Error validating token and claims: {}", e.getMessage());
            return false;
        }
    }
}