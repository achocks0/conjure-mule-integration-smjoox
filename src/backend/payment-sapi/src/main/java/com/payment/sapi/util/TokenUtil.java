package com.payment.sapi.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils; // version 3.12.0
import org.slf4j.Logger; // version 1.7.32
import org.slf4j.LoggerFactory; // version 1.7.32

import com.payment.common.model.TokenClaims;
import com.payment.common.util.TokenValidator;
import com.payment.sapi.model.Token;

/**
 * Utility class for token handling operations in the Payment-Sapi component.
 * Provides methods for extracting information from tokens, validating tokens,
 * and performing common token-related operations.
 */
public class TokenUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenUtil.class);
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Extracts the JWT token string from the Authorization header value
     *
     * @param authorizationHeader The Authorization header value
     * @return The extracted token string or null if not found
     */
    public static String extractTokenFromHeader(String authorizationHeader) {
        if (StringUtils.isEmpty(authorizationHeader)) {
            LOGGER.debug("Authorization header is empty or null");
            return null;
        }

        if (authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }

        LOGGER.debug("Authorization header does not contain a Bearer token");
        return null;
    }

    /**
     * Creates a Token object from a JWT token string
     *
     * @param tokenString The JWT token string
     * @return A Token object containing the parsed token information or null if parsing fails
     */
    public static Token createToken(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return null;
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return null;
            }

            String clientId = claims.getSub();
            String jti = claims.getJti();
            Date expirationTime = claims.getExp();

            return Token.builder()
                    .tokenString(tokenString)
                    .claims(claims)
                    .clientId(clientId)
                    .jti(jti)
                    .expirationTime(expirationTime)
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error creating token object: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Formats a token string as a Bearer token for use in Authorization headers
     *
     * @param tokenString The token string to format
     * @return The formatted Bearer token string or null if input is invalid
     */
    public static String formatBearerToken(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return null;
        }

        return BEARER_PREFIX + tokenString;
    }

    /**
     * Extracts the client ID (subject) from a token string
     *
     * @param tokenString The JWT token string
     * @return The client ID or null if extraction fails
     */
    public static String getClientIdFromToken(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return null;
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return null;
            }

            return claims.getSub();
        } catch (Exception e) {
            LOGGER.error("Error extracting client ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the token ID (jti) from a token string
     *
     * @param tokenString The JWT token string
     * @return The token ID or null if extraction fails
     */
    public static String getTokenIdFromToken(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return null;
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return null;
            }

            return claims.getJti();
        } catch (Exception e) {
            LOGGER.error("Error extracting token ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the permissions list from a token string
     *
     * @param tokenString The JWT token string
     * @return The list of permissions or empty list if extraction fails
     */
    public static List<String> getPermissionsFromToken(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return new ArrayList<>();
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return new ArrayList<>();
            }

            List<String> permissions = claims.getPermissions();
            return permissions != null ? permissions : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.error("Error extracting permissions from token: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Checks if a token string represents an expired token
     *
     * @param tokenString The JWT token string
     * @return True if the token is expired or invalid, false otherwise
     */
    public static boolean isTokenExpired(String tokenString) {
        if (StringUtils.isEmpty(tokenString)) {
            LOGGER.debug("Token string is empty or null");
            return true; // Consider as expired for safety
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return true; // Consider as expired for safety
            }

            return claims.isExpired();
        } catch (Exception e) {
            LOGGER.error("Error checking token expiration: {}", e.getMessage());
            return true; // Consider as expired for safety
        }
    }

    /**
     * Checks if a token string contains a specific permission
     *
     * @param tokenString The JWT token string
     * @param requiredPermission The permission to check for
     * @return True if the token has the required permission, false otherwise
     */
    public static boolean hasPermission(String tokenString, String requiredPermission) {
        if (StringUtils.isEmpty(tokenString) || StringUtils.isEmpty(requiredPermission)) {
            LOGGER.debug("Token string or required permission is empty or null");
            return false;
        }

        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Failed to parse token claims");
                return false;
            }

            return claims.hasPermission(requiredPermission);
        } catch (Exception e) {
            LOGGER.error("Error checking token permission: {}", e.getMessage());
            return false;
        }
    }
}