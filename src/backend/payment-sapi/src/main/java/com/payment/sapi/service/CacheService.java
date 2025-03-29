package com.payment.sapi.service;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;

/**
 * Interface defining the caching operations for token management in the Payment-SAPI service.
 * This service provides methods for storing, retrieving, validating, and invalidating
 * authentication tokens to improve performance and reduce load on authentication services.
 * <p>
 * The caching service is a critical component that:
 * <ul>
 *     <li>Improves performance by avoiding repeated token validation</li>
 *     <li>Reduces load on the authentication services</li>
 *     <li>Supports credential rotation by allowing token invalidation</li>
 * </ul>
 */
public interface CacheService {

    /**
     * Stores a token in the cache with appropriate expiration time.
     * 
     * @param token the token to store in the cache
     */
    void storeToken(Token token);

    /**
     * Retrieves a token from the cache by its token string.
     * 
     * @param tokenString the JWT token string to look up
     * @return the retrieved token or null if not found
     */
    Token retrieveToken(String tokenString);

    /**
     * Validates a token using cached validation results or performs validation if not cached.
     * This method improves performance by caching validation results for tokens.
     * 
     * @param tokenString the JWT token string to validate
     * @param requiredPermission the permission required for the operation
     * @return the result of token validation
     */
    ValidationResult validateToken(String tokenString, String requiredPermission);

    /**
     * Invalidates a token in the cache by its ID.
     * This is typically used when a token needs to be revoked.
     * 
     * @param tokenId the unique identifier of the token to invalidate
     * @return true if token was successfully invalidated, false otherwise
     */
    boolean invalidateToken(String tokenId);

    /**
     * Invalidates all tokens for a specific client ID, used during credential rotation.
     * When credentials are rotated, all existing tokens for the client must be invalidated.
     * 
     * @param clientId the client ID for which to invalidate all tokens
     * @return number of tokens invalidated
     */
    int invalidateTokensByClientId(String clientId);
}