package com.payment.sapi.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.CacheService;
import com.payment.sapi.service.TokenValidationService;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the CacheService interface that uses Redis for caching authentication tokens
 * and validation results to improve performance and support credential rotation.
 * <p>
 * This service reduces the need for repeated token validation and supports the credential
 * rotation process by providing methods to invalidate tokens when credentials are rotated.
 */
@Service
public class RedisCacheServiceImpl implements CacheService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheServiceImpl.class);
    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final String CLIENT_TOKENS_KEY_PREFIX = "client:tokens:";
    private static final String VALIDATION_KEY_PREFIX = "validation:";
    private static final long DEFAULT_CACHE_EXPIRATION_SECONDS = 300; // 5 minutes

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final TokenValidationService tokenValidationService;

    /**
     * Constructs a new RedisCacheServiceImpl with the required dependencies.
     *
     * @param redisTemplate Redis template for cache operations
     * @param objectMapper JSON object mapper for serialization/deserialization
     * @param tokenValidationService Service for token validation when cache misses occur
     */
    public RedisCacheServiceImpl(RedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper,
                              TokenValidationService tokenValidationService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tokenValidationService = tokenValidationService;
        logger.info("Initialized Redis cache service for token management");
    }

    /**
     * Stores a token in the Redis cache with appropriate expiration time.
     *
     * @param token the token to store in the cache
     */
    @Override
    public void storeToken(Token token) {
        if (token == null) {
            logger.warn("Attempted to cache null token, ignoring request");
            return;
        }

        String tokenId = token.getTokenId();
        String clientId = token.getSubject();
        
        if (tokenId == null || clientId == null) {
            logger.warn("Token missing required fields (tokenId or clientId), cannot cache");
            return;
        }

        String tokenKey = getTokenCacheKey(tokenId);
        String clientTokensKey = getClientTokensKey(clientId);
        
        long expirationSeconds = calculateExpirationSeconds(token);
        
        // Store token with expiration
        redisTemplate.opsForValue().set(tokenKey, token, expirationSeconds, TimeUnit.SECONDS);
        
        // Add token to the set of tokens for this client
        redisTemplate.opsForSet().add(clientTokensKey, tokenId);
        // Set expiration for client tokens set if not already set
        if (Boolean.FALSE.equals(redisTemplate.hasKey(clientTokensKey))) {
            redisTemplate.expire(clientTokensKey, 24, TimeUnit.HOURS); // Client token sets expire after 24 hours
        }
        
        logger.debug("Stored token in cache: id={}, clientId={}, expires in {} seconds", 
                tokenId, clientId, expirationSeconds);
    }

    /**
     * Retrieves a token from the Redis cache by its token string.
     *
     * @param tokenString the JWT token string to look up
     * @return the retrieved token or null if not found
     */
    @Override
    public Token retrieveToken(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            logger.warn("Attempted to retrieve token with null or empty string");
            return null;
        }

        // Parse token to get token ID
        Token parsedToken = tokenValidationService.parseToken(tokenString);
        if (parsedToken == null || parsedToken.getTokenId() == null) {
            logger.debug("Failed to parse token string or extract token ID");
            return null;
        }

        String tokenKey = getTokenCacheKey(parsedToken.getTokenId());
        Object cachedToken = redisTemplate.opsForValue().get(tokenKey);

        if (cachedToken == null) {
            logger.debug("Token not found in cache: {}", parsedToken.getTokenId());
            return null;
        }

        if (!(cachedToken instanceof Token)) {
            logger.warn("Cached object is not a Token instance: {}", parsedToken.getTokenId());
            return null;
        }

        Token token = (Token) cachedToken;
        logger.debug("Retrieved token from cache: id={}, clientId={}", 
                token.getTokenId(), token.getSubject());
        return token;
    }

    /**
     * Validates a token using cached validation results or performs validation if not cached.
     * This method improves performance by caching validation results for tokens.
     *
     * @param tokenString the JWT token string to validate
     * @param requiredPermission the permission required for the operation
     * @return the result of token validation
     */
    @Override
    public ValidationResult validateToken(String tokenString, String requiredPermission) {
        if (tokenString == null || tokenString.isEmpty()) {
            logger.warn("Attempted to validate null or empty token string");
            return ValidationResult.invalid("Token string cannot be null or empty");
        }
        
        // Create a key for caching validation results
        String validationKey = getValidationCacheKey(tokenString, requiredPermission);
        
        // Check if validation result is cached
        Object cachedResult = redisTemplate.opsForValue().get(validationKey);
        
        if (cachedResult != null && cachedResult instanceof ValidationResult) {
            ValidationResult result = (ValidationResult) cachedResult;
            logger.debug("Using cached validation result for token: valid={}", result.isValid());
            return result;
        }
        
        // Perform validation if not cached
        ValidationResult result = tokenValidationService.validateToken(tokenString, requiredPermission);
        
        // Cache validation result (only if token is valid or has a specific error)
        // Don't cache renewed tokens as the token string will change
        if (!result.isRenewed()) {
            // Cache for a shorter time than tokens themselves
            redisTemplate.opsForValue().set(validationKey, result, DEFAULT_CACHE_EXPIRATION_SECONDS / 3, TimeUnit.SECONDS);
            logger.debug("Cached validation result for token: valid={}", result.isValid());
        }
        
        return result;
    }

    /**
     * Invalidates a token in the cache by its ID.
     * This is typically used when a token needs to be revoked.
     *
     * @param tokenId the unique identifier of the token to invalidate
     * @return true if token was successfully invalidated, false otherwise
     */
    @Override
    public boolean invalidateToken(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) {
            logger.warn("Attempted to invalidate token with null or empty ID");
            return false;
        }
        
        String tokenKey = getTokenCacheKey(tokenId);
        
        // Get the token to find the client ID
        Object cachedToken = redisTemplate.opsForValue().get(tokenKey);
        if (cachedToken == null) {
            logger.debug("Token not found in cache for invalidation: {}", tokenId);
            return false;
        }
        
        if (!(cachedToken instanceof Token)) {
            logger.warn("Cached object is not a Token instance: {}", tokenId);
            // Still delete the token key even if it's not a Token instance
            redisTemplate.delete(tokenKey);
            return true;
        }
        
        Token token = (Token) cachedToken;
        String clientId = token.getSubject();
        
        // Remove token from cache
        redisTemplate.delete(tokenKey);
        
        // Remove token ID from client tokens set
        if (clientId != null) {
            String clientTokensKey = getClientTokensKey(clientId);
            redisTemplate.opsForSet().remove(clientTokensKey, tokenId);
        }
        
        logger.debug("Invalidated token in cache: id={}, clientId={}", tokenId, clientId);
        return true;
    }

    /**
     * Invalidates all tokens for a specific client ID, used during credential rotation.
     * When credentials are rotated, all existing tokens for the client must be invalidated.
     *
     * @param clientId the client ID for which to invalidate all tokens
     * @return number of tokens invalidated
     */
    @Override
    public int invalidateTokensByClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            logger.warn("Attempted to invalidate tokens for null or empty client ID");
            return 0;
        }
        
        String clientTokensKey = getClientTokensKey(clientId);
        
        // Get all token IDs for this client
        Set<Object> tokenIds = redisTemplate.opsForSet().members(clientTokensKey);
        
        if (tokenIds == null || tokenIds.isEmpty()) {
            logger.debug("No tokens found for client: {}", clientId);
            return 0;
        }
        
        int count = 0;
        for (Object tokenId : tokenIds) {
            if (tokenId instanceof String) {
                if (invalidateToken((String) tokenId)) {
                    count++;
                }
            }
        }
        
        // Remove the client tokens set itself
        redisTemplate.delete(clientTokensKey);
        
        logger.info("Invalidated {} tokens for client: {}", count, clientId);
        return count;
    }

    /**
     * Creates a Redis key for storing a token.
     *
     * @param tokenId the token ID
     * @return the Redis key for the token
     */
    private String getTokenCacheKey(String tokenId) {
        return TOKEN_KEY_PREFIX + tokenId;
    }

    /**
     * Creates a Redis key for storing the set of token IDs for a client.
     *
     * @param clientId the client ID
     * @return the Redis key for the client's tokens
     */
    private String getClientTokensKey(String clientId) {
        return CLIENT_TOKENS_KEY_PREFIX + clientId;
    }

    /**
     * Creates a Redis key for storing validation results.
     *
     * @param tokenString the token string
     * @param requiredPermission the required permission
     * @return the Redis key for the validation result
     */
    private String getValidationCacheKey(String tokenString, String requiredPermission) {
        return VALIDATION_KEY_PREFIX + tokenString + ":" + (requiredPermission != null ? requiredPermission : "none");
    }

    /**
     * Calculates the expiration time in seconds for a token.
     *
     * @param token the token
     * @return the expiration time in seconds
     */
    private long calculateExpirationSeconds(Token token) {
        if (token == null || token.getExpirationTime() == null) {
            return DEFAULT_CACHE_EXPIRATION_SECONDS;
        }
        
        Date now = new Date();
        Date expiration = token.getExpirationTime();
        
        // Calculate difference in seconds
        long diffInSeconds = (expiration.getTime() - now.getTime()) / 1000;
        
        // If token is already expired or expires in 0 seconds, use default
        if (diffInSeconds <= 0) {
            return DEFAULT_CACHE_EXPIRATION_SECONDS;
        }
        
        // Use the minimum of calculated time and default to avoid caching for too long
        return Math.min(diffInSeconds, DEFAULT_CACHE_EXPIRATION_SECONDS);
    }
}