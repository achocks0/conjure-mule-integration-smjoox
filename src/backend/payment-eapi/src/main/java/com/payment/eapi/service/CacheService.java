package com.payment.eapi.service;

import com.payment.eapi.model.Token;
import com.payment.eapi.model.Credential;

import java.util.List;
import java.util.Optional;

/**
 * Interface defining the caching operations for authentication tokens and credential metadata
 * in the Payment API Security Enhancement project. This service provides methods to store, 
 * retrieve, and invalidate tokens and credentials in a cache, reducing the need for frequent 
 * Conjur vault access and improving performance.
 */
public interface CacheService {
    
    /**
     * Stores a token in the cache with appropriate expiration time.
     *
     * @param token the token to be cached
     */
    void cacheToken(Token token);
    
    /**
     * Retrieves a token from the cache by client ID.
     *
     * @param clientId the client ID associated with the token
     * @return an Optional containing the token if found, empty Optional otherwise
     */
    Optional<Token> retrieveToken(String clientId);
    
    /**
     * Retrieves a token from the cache by token ID.
     *
     * @param tokenId the unique identifier of the token
     * @return an Optional containing the token if found, empty Optional otherwise
     */
    Optional<Token> retrieveTokenById(String tokenId);
    
    /**
     * Removes a token from the cache by client ID.
     *
     * @param clientId the client ID associated with the token to be removed
     */
    void invalidateToken(String clientId);
    
    /**
     * Removes a token from the cache by token ID.
     *
     * @param tokenId the unique identifier of the token to be removed
     */
    void invalidateTokenById(String tokenId);
    
    /**
     * Stores credential metadata in the cache.
     *
     * @param clientId the client ID associated with the credential
     * @param credential the credential metadata to be cached
     */
    void cacheCredential(String clientId, Credential credential);
    
    /**
     * Retrieves credential metadata from the cache.
     *
     * @param clientId the client ID associated with the credential
     * @return an Optional containing the credential if found, empty Optional otherwise
     */
    Optional<Credential> retrieveCredential(String clientId);
    
    /**
     * Removes credential metadata from the cache.
     *
     * @param clientId the client ID associated with the credential to be removed
     */
    void invalidateCredential(String clientId);
    
    /**
     * Removes all cached data for a specific client, including tokens and credentials.
     *
     * @param clientId the client ID for which all cache entries should be invalidated
     */
    void invalidateAllForClient(String clientId);
    
    /**
     * Removes multiple tokens from the cache in a batch operation.
     *
     * @param tokenIds a list of token IDs to be removed from the cache
     */
    void invalidateTokensBatch(List<String> tokenIds);
}