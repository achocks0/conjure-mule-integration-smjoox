package com.payment.eapi.util;

import com.payment.eapi.model.Token;
import com.payment.common.model.TokenClaims;
import com.payment.common.util.TokenGenerator;
import com.payment.common.util.TokenValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * A utility class that provides token-related functionality specific to the Payment-Eapi component.
 * This class serves as a bridge between the common token utilities and the Payment-Eapi's token service,
 * offering simplified methods for token operations in the context of the Payment API Security Enhancement project.
 */
public class TokenUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenUtil.class);
    private static final String DEFAULT_ISSUER = "payment-eapi";
    private static final String DEFAULT_AUDIENCE = "payment-sapi";
    private static final int DEFAULT_TOKEN_EXPIRATION_SECONDS = 3600;
    private static final List<String> DEFAULT_PERMISSIONS = List.of("process_payment", "view_status");

    /**
     * Creates a token for the specified client ID with default permissions
     *
     * @param clientId    Client ID for whom the token is being created
     * @param signingKey  Key used to sign the token
     * @return Generated token with claims and expiration time
     */
    public static Token createToken(String clientId, byte[] signingKey) {
        SecurityUtil.logSecurely(LOGGER, "Creating token for client ID: {}", clientId);
        return createToken(clientId, DEFAULT_PERMISSIONS, signingKey);
    }

    /**
     * Creates a token for the specified client ID with custom permissions
     *
     * @param clientId    Client ID for whom the token is being created
     * @param permissions List of permissions to include in the token
     * @param signingKey  Key used to sign the token
     * @return Generated token with claims and expiration time
     */
    public static Token createToken(String clientId, List<String> permissions, byte[] signingKey) {
        SecurityUtil.logSecurely(LOGGER, "Creating token for client ID: {} with custom permissions", clientId);
        
        try {
            if (clientId == null || permissions == null || signingKey == null) {
                throw new IllegalArgumentException("Client ID, permissions, and signing key must not be null");
            }

            // Generate token ID
            String jti = UUID.randomUUID().toString();
            
            // Generate issuance and expiration times
            Date issuedAt = new Date();
            Date expirationTime = new Date(issuedAt.getTime() + DEFAULT_TOKEN_EXPIRATION_SECONDS * 1000L);
            
            // Build token claims
            TokenClaims claims = TokenClaims.builder()
                    .sub(clientId)
                    .iss(DEFAULT_ISSUER)
                    .aud(DEFAULT_AUDIENCE)
                    .permissions(permissions)
                    .iat(issuedAt)
                    .exp(expirationTime)
                    .jti(jti)
                    .build();
            
            // Generate token
            String tokenString = TokenGenerator.generateToken(claims, signingKey);
            
            // Build token object
            return Token.builder()
                    .tokenString(tokenString)
                    .claims(claims)
                    .expirationTime(expirationTime)
                    .jti(jti)
                    .clientId(clientId)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error creating token for client ID {}: {}", clientId, e.getMessage());
            throw new RuntimeException("Error creating token", e);
        }
    }

    /**
     * Validates a token's signature and basic structure
     *
     * @param tokenString Token string to validate
     * @param signingKey  Key used to validate the token's signature
     * @return True if the token is valid, false otherwise
     */
    public static boolean validateToken(String tokenString, byte[] signingKey) {
        LOGGER.debug("Validating token");
        
        try {
            if (tokenString == null || signingKey == null) {
                LOGGER.warn("Token string or signing key is null");
                return false;
            }
            
            return TokenValidator.validateToken(tokenString, signingKey);
        } catch (Exception e) {
            LOGGER.error("Error validating token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses a token string into a Token object
     *
     * @param tokenString Token string to parse
     * @return Parsed token if successful, empty Optional otherwise
     */
    public static Optional<Token> parseToken(String tokenString) {
        LOGGER.debug("Parsing token");
        
        try {
            if (tokenString == null || tokenString.isEmpty()) {
                LOGGER.warn("Token string is null or empty");
                return Optional.empty();
            }
            
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return Optional.empty();
            }
            
            Date expirationTime = claims.getExp();
            String jti = claims.getJti();
            String clientId = claims.getSub();
            
            Token token = Token.builder()
                    .tokenString(tokenString)
                    .claims(claims)
                    .expirationTime(expirationTime)
                    .jti(jti)
                    .clientId(clientId)
                    .build();
            
            return Optional.of(token);
        } catch (Exception e) {
            LOGGER.error("Error parsing token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts the client ID from a token
     *
     * @param token Token from which to extract the client ID
     * @return Client ID from the token's subject claim
     */
    public static String extractClientId(Token token) {
        try {
            if (token == null || token.getClaims() == null) {
                return null;
            }
            
            return token.getClaims().getSub();
        } catch (Exception e) {
            LOGGER.error("Error extracting client ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the permissions from a token
     *
     * @param token Token from which to extract permissions
     * @return List of permissions from the token
     */
    public static List<String> extractPermissions(Token token) {
        try {
            if (token == null) {
                return List.of();
            }
            
            return token.getPermissions();
        } catch (Exception e) {
            LOGGER.error("Error extracting permissions from token: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if a token has expired
     *
     * @param token Token to check
     * @return True if the token has expired, false otherwise
     */
    public static boolean isExpired(Token token) {
        try {
            if (token == null) {
                return true;
            }
            
            return token.isExpired();
        } catch (Exception e) {
            LOGGER.error("Error checking token expiration: {}", e.getMessage());
            return true; // Safer to consider as expired
        }
    }

    /**
     * Checks if a token has a specific permission
     *
     * @param token      Token to check
     * @param permission Permission to check for
     * @return True if the token has the permission, false otherwise
     */
    public static boolean hasPermission(Token token, String permission) {
        try {
            if (token == null || permission == null) {
                return false;
            }
            
            return token.hasPermission(permission);
        } catch (Exception e) {
            LOGGER.error("Error checking token permission: {}", e.getMessage());
            return false;
        }
    }
}