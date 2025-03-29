package com.payment.eapi.service;

import com.payment.eapi.model.Token;
import com.payment.eapi.model.Credential;
import com.payment.eapi.service.impl.RedisCacheServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RedisCacheServiceImpl that verifies caching operations
 * for authentication tokens and credential metadata.
 */
@ExtendWith(MockitoExtension.class)
public class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisCacheServiceImpl cacheService;

    private String clientId;
    private String tokenId;
    private Token token;
    private Credential credential;

    @BeforeEach
    void setUp() {
        // Initialize test data
        clientId = "test-client-id";
        tokenId = "test-token-id";
        
        // Set up default token
        Date expirationTime = new Date(System.currentTimeMillis() + 3600000); // 1 hour in future
        token = Token.builder()
                .tokenString("test-token-string")
                .jti(tokenId)
                .clientId(clientId)
                .expirationTime(expirationTime)
                .build();
        
        // Set up default credential
        credential = Credential.builder()
                .clientId(clientId)
                .hashedSecret("hashed-secret")
                .active(true)
                .version("1.0")
                .build();
        
        // Configure standard mocks
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @AfterEach
    void tearDown() {
        // Reset all mocks to clean state
        reset(redisTemplate, valueOps, objectMapper);
    }

    @Test
    void testCacheToken() {
        // Call the method to test
        cacheService.cacheToken(token);

        // Verify interactions with Redis
        verify(valueOps).set(eq("token:" + clientId), eq(token));
        verify(valueOps).set(eq("token_id:" + tokenId), eq(token));
        
        // Verify expiration is set
        verify(redisTemplate).expire(eq("token:" + clientId), anyLong(), eq(TimeUnit.SECONDS));
        verify(redisTemplate).expire(eq("token_id:" + tokenId), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void testRetrieveToken() {
        // Configure mock to return token
        when(valueOps.get("token:" + clientId)).thenReturn(token);

        // Call the method to test
        Optional<Token> result = cacheService.retrieveToken(clientId);

        // Verify result
        assertTrue(result.isPresent());
        assertEquals(token, result.get());
        
        // Verify interaction with Redis
        verify(valueOps).get("token:" + clientId);
    }

    @Test
    void testRetrieveTokenById() {
        // Configure mock to return token
        when(valueOps.get("token_id:" + tokenId)).thenReturn(token);

        // Call the method to test
        Optional<Token> result = cacheService.retrieveTokenById(tokenId);

        // Verify result
        assertTrue(result.isPresent());
        assertEquals(token, result.get());
        
        // Verify interaction with Redis
        verify(valueOps).get("token_id:" + tokenId);
    }

    @Test
    void testRetrieveTokenNotFound() {
        // Configure mock to return null (token not found)
        when(valueOps.get("token:" + clientId)).thenReturn(null);

        // Call the method to test
        Optional<Token> result = cacheService.retrieveToken(clientId);

        // Verify result
        assertFalse(result.isPresent());
        
        // Verify interaction with Redis
        verify(valueOps).get("token:" + clientId);
    }

    @Test
    void testRetrieveExpiredToken() {
        // Create an expired token
        Date expiredTime = new Date(System.currentTimeMillis() - 3600000); // 1 hour in past
        Token expiredToken = Token.builder()
                .tokenString("test-token-string")
                .jti(tokenId)
                .clientId(clientId)
                .expirationTime(expiredTime)
                .build();

        // Configure mock to return expired token
        when(valueOps.get("token:" + clientId)).thenReturn(expiredToken);

        // Call the method to test
        Optional<Token> result = cacheService.retrieveToken(clientId);

        // Verify result
        assertFalse(result.isPresent());
        
        // Verify interactions with Redis
        verify(valueOps).get("token:" + clientId);
        
        // Verify that invalidation was called for expired token
        verify(redisTemplate).delete("token:" + clientId);
    }

    @Test
    void testInvalidateToken() {
        // Configure mock to return token when retrieving for invalidation
        when(valueOps.get("token:" + clientId)).thenReturn(token);

        // Call the method to test
        cacheService.invalidateToken(clientId);

        // Verify interactions with Redis
        verify(valueOps).get("token:" + clientId);
        verify(redisTemplate).delete("token:" + clientId);
        verify(redisTemplate).delete("token_id:" + tokenId);
    }

    @Test
    void testInvalidateTokenById() {
        // Call the method to test
        cacheService.invalidateTokenById(tokenId);

        // Verify interaction with Redis
        verify(redisTemplate).delete("token_id:" + tokenId);
    }

    @Test
    void testCacheCredential() {
        // Call the method to test
        cacheService.cacheCredential(clientId, credential);

        // Verify interactions with Redis
        verify(valueOps).set(eq("credential:" + clientId), eq(credential));
        verify(redisTemplate).expire(eq("credential:" + clientId), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    void testRetrieveCredential() {
        // Configure mock to return credential
        when(valueOps.get("credential:" + clientId)).thenReturn(credential);

        // Call the method to test
        Optional<Credential> result = cacheService.retrieveCredential(clientId);

        // Verify result
        assertTrue(result.isPresent());
        assertEquals(credential, result.get());
        
        // Verify interaction with Redis
        verify(valueOps).get("credential:" + clientId);
    }

    @Test
    void testRetrieveCredentialNotFound() {
        // Configure mock to return null (credential not found)
        when(valueOps.get("credential:" + clientId)).thenReturn(null);

        // Call the method to test
        Optional<Credential> result = cacheService.retrieveCredential(clientId);

        // Verify result
        assertFalse(result.isPresent());
        
        // Verify interaction with Redis
        verify(valueOps).get("credential:" + clientId);
    }

    @Test
    void testRetrieveExpiredCredential() {
        // Create an expired credential
        Date expiredTime = new Date(System.currentTimeMillis() - 3600000); // 1 hour in past
        Credential expiredCredential = Credential.builder()
                .clientId(clientId)
                .hashedSecret("hashed-secret")
                .active(true)
                .version("1.0")
                .expiresAt(expiredTime)
                .build();

        // Configure mock to return expired credential
        when(valueOps.get("credential:" + clientId)).thenReturn(expiredCredential);

        // Call the method to test
        Optional<Credential> result = cacheService.retrieveCredential(clientId);

        // Verify result
        assertFalse(result.isPresent());
        
        // Verify interactions with Redis
        verify(valueOps).get("credential:" + clientId);
        
        // Verify that invalidation was called for expired credential
        verify(redisTemplate).delete("credential:" + clientId);
    }

    @Test
    void testInvalidateCredential() {
        // Call the method to test
        cacheService.invalidateCredential(clientId);

        // Verify interaction with Redis
        verify(redisTemplate).delete("credential:" + clientId);
    }

    @Test
    void testInvalidateAllForClient() {
        // Configure mock to return token when retrieving for invalidation
        when(valueOps.get("token:" + clientId)).thenReturn(token);

        // Call the method to test
        cacheService.invalidateAllForClient(clientId);

        // Verify that both token and credential were invalidated
        verify(redisTemplate).delete("credential:" + clientId);
        verify(valueOps).get("token:" + clientId);
        verify(redisTemplate).delete("token:" + clientId);
        verify(redisTemplate).delete("token_id:" + tokenId);
    }

    @Test
    void testInvalidateTokensBatch() {
        // Create a list of token IDs
        List<String> tokenIds = new ArrayList<>();
        tokenIds.add("token1");
        tokenIds.add("token2");
        tokenIds.add("token3");

        // Call the method to test
        cacheService.invalidateTokensBatch(tokenIds);

        // Verify that delete was called for each token ID
        verify(redisTemplate).delete("token_id:token1");
        verify(redisTemplate).delete("token_id:token2");
        verify(redisTemplate).delete("token_id:token3");
    }

    @Test
    void testCalculateTokenExpiration() {
        // Capture the expiration time value
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);
        
        // Call the method to test
        cacheService.cacheToken(token);

        // Verify that expire was called and capture the TTL value
        verify(redisTemplate).expire(eq("token:" + clientId), expirationCaptor.capture(), eq(TimeUnit.SECONDS));
        
        // Assert that the TTL is reasonable (slightly less than 1 hour)
        long ttl = expirationCaptor.getValue();
        assertTrue(ttl > 3500 && ttl < 3600, "TTL should be slightly less than 1 hour");
    }

    @Test
    void testCalculateCredentialExpiration() {
        // Create a credential with future expiration
        Date expirationTime = new Date(System.currentTimeMillis() + 3600000); // 1 hour in future
        Credential expirableCredential = Credential.builder()
                .clientId(clientId)
                .hashedSecret("hashed-secret")
                .active(true)
                .version("1.0")
                .expiresAt(expirationTime)
                .build();
        
        // Capture the expiration time value
        ArgumentCaptor<Long> expirationCaptor = ArgumentCaptor.forClass(Long.class);
        
        // Call the method to test
        cacheService.cacheCredential(clientId, expirableCredential);

        // Verify that expire was called and capture the TTL value
        verify(redisTemplate).expire(eq("credential:" + clientId), expirationCaptor.capture(), eq(TimeUnit.SECONDS));
        
        // Assert that the TTL is reasonable (slightly less than 1 hour)
        long ttl = expirationCaptor.getValue();
        assertTrue(ttl > 3500 && ttl < 3600, "TTL should be slightly less than 1 hour");
    }
}