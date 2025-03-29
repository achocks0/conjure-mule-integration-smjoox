package com.payment.eapi.service.impl;

import com.payment.eapi.service.CacheService;
import com.payment.eapi.model.Token;
import com.payment.eapi.model.Credential;

import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.List;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the CacheService interface using Redis for caching authentication tokens 
 * and credential metadata in the Payment API Security Enhancement project. This class provides 
 * efficient caching mechanisms to reduce direct calls to Conjur vault and improve authentication performance.
 */
@Service
public class RedisCacheServiceImpl implements CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisCacheServiceImpl.class);
    
    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final String TOKEN_ID_KEY_PREFIX = "token_id:";
    private static final String CREDENTIAL_KEY_PREFIX = "credential:";
    
    // Default TTL values
    private static final long DEFAULT_TOKEN_TTL_SECONDS = 3600L; // 1 hour
    private static final long DEFAULT_CREDENTIAL_TTL_SECONDS = 900L; // 15 minutes
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ValueOperations<String, Object> valueOps;
    
    /**
     * Constructs a new RedisCacheServiceImpl with the provided RedisTemplate and ObjectMapper
     *
     * @param redisTemplate the Redis template for cache operations
     * @param objectMapper the object mapper for serialization/deserialization
     */
    public RedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.valueOps = redisTemplate.opsForValue();
    }
    
    /**
     * Stores a token in the Redis cache with appropriate expiration time.
     *
     * @param token the token to be cached
     */
    @Override
    public void cacheToken(Token token) {
        logger.debug("Caching token for client ID: {}", token.getClientId());
        
        if (token == null) {
            logger.warn("Cannot cache null token");
            return;
        }
        
        try {
            long expirationSeconds = calculateTokenExpiration(token);
            
            // Cache by client ID
            String clientKey = TOKEN_KEY_PREFIX + token.getClientId();
            valueOps.set(clientKey, token);
            redisTemplate.expire(clientKey, expirationSeconds, TimeUnit.SECONDS);
            
            // Cache by token ID for lookups by token ID
            String tokenIdKey = TOKEN_ID_KEY_PREFIX + token.getJti();
            valueOps.set(tokenIdKey, token);
            redisTemplate.expire(tokenIdKey, expirationSeconds, TimeUnit.SECONDS);
            
            logger.debug("Token cached successfully for client ID: {} with expiration: {} seconds", 
                    token.getClientId(), expirationSeconds);
        } catch (Exception e) {
            logger.error("Error caching token for client ID: {}", token.getClientId(), e);
        }
    }
    
    /**
     * Retrieves a token from the Redis cache by client ID.
     *
     * @param clientId the client ID associated with the token
     * @return an Optional containing the token if found, empty Optional otherwise
     */
    @Override
    public Optional<Token> retrieveToken(String clientId) {
        logger.debug("Retrieving token for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Cannot retrieve token for null or empty client ID");
            return Optional.empty();
        }
        
        try {
            String key = TOKEN_KEY_PREFIX + clientId;
            Token token = (Token) valueOps.get(key);
            
            if (token == null) {
                logger.debug("No token found in cache for client ID: {}", clientId);
                return Optional.empty();
            }
            
            if (token.isExpired()) {
                logger.debug("Found expired token in cache for client ID: {}, invalidating", clientId);
                invalidateToken(clientId);
                return Optional.empty();
            }
            
            logger.debug("Token retrieved successfully from cache for client ID: {}", clientId);
            return Optional.of(token);
        } catch (Exception e) {
            logger.error("Error retrieving token for client ID: {}", clientId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Retrieves a token from the Redis cache by token ID.
     *
     * @param tokenId the unique identifier of the token
     * @return an Optional containing the token if found, empty Optional otherwise
     */
    @Override
    public Optional<Token> retrieveTokenById(String tokenId) {
        logger.debug("Retrieving token by ID: {}", tokenId);
        
        if (tokenId == null || tokenId.isEmpty()) {
            logger.warn("Cannot retrieve token for null or empty token ID");
            return Optional.empty();
        }
        
        try {
            String key = TOKEN_ID_KEY_PREFIX + tokenId;
            Token token = (Token) valueOps.get(key);
            
            if (token == null) {
                logger.debug("No token found in cache for token ID: {}", tokenId);
                return Optional.empty();
            }
            
            if (token.isExpired()) {
                logger.debug("Found expired token in cache for token ID: {}, invalidating", tokenId);
                invalidateTokenById(tokenId);
                return Optional.empty();
            }
            
            logger.debug("Token retrieved successfully from cache for token ID: {}", tokenId);
            return Optional.of(token);
        } catch (Exception e) {
            logger.error("Error retrieving token for token ID: {}", tokenId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Removes a token from the Redis cache by client ID.
     *
     * @param clientId the client ID associated with the token to be removed
     */
    @Override
    public void invalidateToken(String clientId) {
        logger.debug("Invalidating token for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Cannot invalidate token for null or empty client ID");
            return;
        }
        
        try {
            // Also invalidate by token ID if we can retrieve it
            Optional<Token> tokenOpt = retrieveToken(clientId);
            if (tokenOpt.isPresent()) {
                String tokenId = tokenOpt.get().getJti();
                if (tokenId != null && !tokenId.isEmpty()) {
                    invalidateTokenById(tokenId);
                }
            }
            
            String key = TOKEN_KEY_PREFIX + clientId;
            redisTemplate.delete(key);
            logger.debug("Token invalidated successfully for client ID: {}", clientId);
        } catch (Exception e) {
            logger.error("Error invalidating token for client ID: {}", clientId, e);
        }
    }
    
    /**
     * Removes a token from the Redis cache by token ID.
     *
     * @param tokenId the unique identifier of the token to be removed
     */
    @Override
    public void invalidateTokenById(String tokenId) {
        logger.debug("Invalidating token by ID: {}", tokenId);
        
        if (tokenId == null || tokenId.isEmpty()) {
            logger.warn("Cannot invalidate token for null or empty token ID");
            return;
        }
        
        try {
            String key = TOKEN_ID_KEY_PREFIX + tokenId;
            redisTemplate.delete(key);
            logger.debug("Token invalidated successfully for token ID: {}", tokenId);
        } catch (Exception e) {
            logger.error("Error invalidating token for token ID: {}", tokenId, e);
        }
    }
    
    /**
     * Stores credential metadata in the Redis cache.
     *
     * @param clientId the client ID associated with the credential
     * @param credential the credential metadata to be cached
     */
    @Override
    public void cacheCredential(String clientId, Credential credential) {
        logger.debug("Caching credential for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty() || credential == null) {
            logger.warn("Cannot cache credential with null or empty client ID or null credential");
            return;
        }
        
        try {
            long expirationSeconds = calculateCredentialExpiration(credential);
            
            String key = CREDENTIAL_KEY_PREFIX + clientId;
            valueOps.set(key, credential);
            redisTemplate.expire(key, expirationSeconds, TimeUnit.SECONDS);
            
            logger.debug("Credential cached successfully for client ID: {} with expiration: {} seconds", 
                    clientId, expirationSeconds);
        } catch (Exception e) {
            logger.error("Error caching credential for client ID: {}", clientId, e);
        }
    }
    
    /**
     * Retrieves credential metadata from the Redis cache.
     *
     * @param clientId the client ID associated with the credential
     * @return an Optional containing the credential if found, empty Optional otherwise
     */
    @Override
    public Optional<Credential> retrieveCredential(String clientId) {
        logger.debug("Retrieving credential for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Cannot retrieve credential for null or empty client ID");
            return Optional.empty();
        }
        
        try {
            String key = CREDENTIAL_KEY_PREFIX + clientId;
            Credential credential = (Credential) valueOps.get(key);
            
            if (credential == null) {
                logger.debug("No credential found in cache for client ID: {}", clientId);
                return Optional.empty();
            }
            
            if (credential.isExpired()) {
                logger.debug("Found expired credential in cache for client ID: {}, invalidating", clientId);
                invalidateCredential(clientId);
                return Optional.empty();
            }
            
            logger.debug("Credential retrieved successfully from cache for client ID: {}", clientId);
            return Optional.of(credential);
        } catch (Exception e) {
            logger.error("Error retrieving credential for client ID: {}", clientId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Removes credential metadata from the Redis cache.
     *
     * @param clientId the client ID associated with the credential to be removed
     */
    @Override
    public void invalidateCredential(String clientId) {
        logger.debug("Invalidating credential for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Cannot invalidate credential for null or empty client ID");
            return;
        }
        
        try {
            String key = CREDENTIAL_KEY_PREFIX + clientId;
            redisTemplate.delete(key);
            logger.debug("Credential invalidated successfully for client ID: {}", clientId);
        } catch (Exception e) {
            logger.error("Error invalidating credential for client ID: {}", clientId, e);
        }
    }
    
    /**
     * Removes all cached data for a specific client, including tokens and credentials.
     *
     * @param clientId the client ID for which all cache entries should be invalidated
     */
    @Override
    public void invalidateAllForClient(String clientId) {
        logger.debug("Invalidating all cached data for client ID: {}", clientId);
        
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Cannot invalidate all data for null or empty client ID");
            return;
        }
        
        try {
            invalidateToken(clientId);
            invalidateCredential(clientId);
            logger.debug("All cached data invalidated successfully for client ID: {}", clientId);
        } catch (Exception e) {
            logger.error("Error invalidating all cached data for client ID: {}", clientId, e);
        }
    }
    
    /**
     * Removes multiple tokens from the Redis cache in a batch operation.
     *
     * @param tokenIds a list of token IDs to be removed from the cache
     */
    @Override
    public void invalidateTokensBatch(List<String> tokenIds) {
        logger.debug("Batch invalidating tokens, count: {}", tokenIds != null ? tokenIds.size() : 0);
        
        if (tokenIds == null || tokenIds.isEmpty()) {
            logger.warn("Cannot batch invalidate null or empty token IDs list");
            return;
        }
        
        try {
            for (String tokenId : tokenIds) {
                invalidateTokenById(tokenId);
            }
            logger.debug("Batch token invalidation completed successfully, count: {}", tokenIds.size());
        } catch (Exception e) {
            logger.error("Error during batch token invalidation", e);
        }
    }
    
    /**
     * Calculates the expiration time for a token in the cache.
     * 
     * @param token the token to calculate expiration for
     * @return the expiration time in seconds
     */
    private long calculateTokenExpiration(Token token) {
        if (token.getExpirationTime() == null) {
            return DEFAULT_TOKEN_TTL_SECONDS;
        }
        
        long expirationTimeMillis = token.getExpirationTime().getTime();
        long currentTimeMillis = System.currentTimeMillis();
        long differenceMillis = expirationTimeMillis - currentTimeMillis;
        
        // If token is already expired or about to expire, use a small positive value 
        // to ensure it will be removed from cache soon
        if (differenceMillis <= 0) {
            return 10; // 10 seconds
        }
        
        // Convert to seconds and subtract a small buffer to ensure cache expires 
        // slightly before the actual token
        return Math.max(10, (differenceMillis / 1000) - 30);
    }
    
    /**
     * Calculates the expiration time for a credential in the cache.
     * 
     * @param credential the credential to calculate expiration for
     * @return the expiration time in seconds
     */
    private long calculateCredentialExpiration(Credential credential) {
        if (credential.getExpiresAt() == null) {
            return DEFAULT_CREDENTIAL_TTL_SECONDS;
        }
        
        long expirationTimeMillis = credential.getExpiresAt().getTime();
        long currentTimeMillis = System.currentTimeMillis();
        long differenceMillis = expirationTimeMillis - currentTimeMillis;
        
        // If credential is already expired or about to expire, use a small positive value
        // to ensure it will be removed from cache soon
        if (differenceMillis <= 0) {
            return 10; // 10 seconds
        }
        
        // Convert to seconds and subtract a small buffer to ensure cache expires
        // slightly before the actual credential
        return Math.max(10, (differenceMillis / 1000) - 30);
    }
}