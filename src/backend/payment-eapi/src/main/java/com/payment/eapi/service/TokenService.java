package com.payment.eapi.service;

import com.payment.eapi.model.Token;

import java.util.List;
import java.util.Optional;

/**
 * Interface defining the token management operations for the Payment API Security Enhancement project.
 * This service is responsible for generating, validating, parsing, renewing, and revoking JWT tokens
 * used for authentication between Payment-Eapi and Payment-Sapi services.
 */
public interface TokenService {

    /**
     * Generates a JWT token for the specified client ID with default permissions.
     *
     * @param clientId the client identifier for which to generate the token
     * @return a generated token with claims and expiration time
     */
    Token generateToken(String clientId);
    
    /**
     * Generates a JWT token for the specified client ID with custom permissions.
     *
     * @param clientId the client identifier for which to generate the token
     * @param permissions the list of permissions to include in the token
     * @return a generated token with claims and expiration time
     */
    Token generateToken(String clientId, List<String> permissions);
    
    /**
     * Validates a JWT token's signature and claims.
     *
     * @param tokenString the JWT token string to validate
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String tokenString);
    
    /**
     * Parses a JWT token string into a Token object.
     *
     * @param tokenString the JWT token string to parse
     * @return an Optional containing the parsed Token if successful, or empty if parsing fails
     */
    Optional<Token> parseToken(String tokenString);
    
    /**
     * Renews an expired token with the same permissions.
     *
     * @param expiredToken the expired token to renew
     * @return a new token with updated expiration time
     */
    Token renewToken(Token expiredToken);
    
    /**
     * Revokes a token to prevent its further use.
     *
     * @param tokenId the unique identifier of the token to revoke
     * @return true if revocation succeeds, false otherwise
     */
    boolean revokeToken(String tokenId);
    
    /**
     * Checks if a token has been revoked.
     *
     * @param tokenId the unique identifier of the token to check
     * @return true if the token is revoked, false otherwise
     */
    boolean isTokenRevoked(String tokenId);
    
    /**
     * Retrieves the key used for signing tokens.
     *
     * @return the signing key as a byte array
     */
    byte[] getSigningKey();
}