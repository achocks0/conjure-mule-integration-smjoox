package com.payment.eapi.service;

import com.payment.eapi.exception.AuthenticationException;
import com.payment.eapi.model.AuthenticationRequest;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.model.Credential;
import com.payment.eapi.model.Token;
import com.payment.eapi.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private ConjurService conjurService;

    @Mock
    private TokenService tokenService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_TOKEN_STRING = "test-token-string";

    @BeforeEach
    void setUp() {
        // Reset all mocks before each test
        Mockito.reset(conjurService, tokenService, cacheService);
    }

    @Test
    @DisplayName("Should authenticate successfully with valid credentials")
    void testAuthenticateWithValidCredentials() {
        // Create a test credential
        Credential credential = Credential.builder()
                .clientId(TEST_CLIENT_ID)
                .hashedSecret("hashed-secret")
                .active(true)
                .build();

        // Create a test token
        Token token = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Mock conjurService.validateCredentialsWithFallback to return true
        when(conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET)).thenReturn(true);

        // Mock tokenService.generateToken to return the test token
        when(tokenService.generateToken(TEST_CLIENT_ID)).thenReturn(token);

        // Call authenticationService.authenticate with test client ID and secret
        Token result = authenticationService.authenticate(TEST_CLIENT_ID, TEST_CLIENT_SECRET);

        // Verify the token is returned correctly
        assertNotNull(result);
        assertEquals(TEST_TOKEN_STRING, result.getTokenString());
        assertEquals(TEST_CLIENT_ID, result.getClientId());

        // Verify conjurService.validateCredentialsWithFallback was called with correct parameters
        verify(conjurService).validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify tokenService.generateToken was called with correct parameters
        verify(tokenService).generateToken(TEST_CLIENT_ID);
        
        // Verify cacheService.cacheToken was called with the test token
        verify(cacheService).cacheToken(token);
    }

    @Test
    @DisplayName("Should throw AuthenticationException with invalid credentials")
    void testAuthenticateWithInvalidCredentials() {
        // Mock conjurService.validateCredentialsWithFallback to return false
        when(conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET)).thenReturn(false);

        // Assert that calling authenticationService.authenticate throws AuthenticationException
        assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticate(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        });

        // Verify conjurService.validateCredentialsWithFallback was called with correct parameters
        verify(conjurService).validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify tokenService.generateToken was never called
        verify(tokenService, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("Should return cached token if available")
    void testAuthenticateWithCachedToken() {
        // Create a test token
        Token token = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Mock cacheService.retrieveToken to return the test token
        when(cacheService.retrieveToken(TEST_CLIENT_ID)).thenReturn(Optional.of(token));

        // Call authenticationService.authenticate with test client ID and secret
        Token result = authenticationService.authenticate(TEST_CLIENT_ID, TEST_CLIENT_SECRET);

        // Verify the cached token is returned
        assertNotNull(result);
        assertEquals(TEST_TOKEN_STRING, result.getTokenString());
        assertEquals(TEST_CLIENT_ID, result.getClientId());

        // Verify cacheService.retrieveToken was called with the test client ID
        verify(cacheService).retrieveToken(TEST_CLIENT_ID);
        
        // Verify conjurService.validateCredentialsWithFallback was never called
        verify(conjurService, never()).validateCredentialsWithFallback(anyString(), anyString());
        
        // Verify tokenService.generateToken was never called
        verify(tokenService, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("Should authenticate with AuthenticationRequest object")
    void testAuthenticateWithRequest() {
        // Create a test token
        Token token = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Create an AuthenticationRequest with test client ID and secret
        AuthenticationRequest request = AuthenticationRequest.builder()
                .clientId(TEST_CLIENT_ID)
                .clientSecret(TEST_CLIENT_SECRET)
                .build();

        // Mock conjurService.validateCredentialsWithFallback to return true
        when(conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET)).thenReturn(true);
        
        // Mock tokenService.generateToken to return the test token
        when(tokenService.generateToken(TEST_CLIENT_ID)).thenReturn(token);

        // Call authenticationService.authenticate with the request object
        AuthenticationResponse response = authenticationService.authenticate(request);

        // Verify the response contains the correct token and expiration
        assertNotNull(response);
        assertEquals(TEST_TOKEN_STRING, response.getToken());
        assertNotNull(response.getExpiresAt());

        // Verify conjurService.validateCredentialsWithFallback was called with correct parameters
        verify(conjurService).validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify tokenService.generateToken was called with correct parameters
        verify(tokenService).generateToken(TEST_CLIENT_ID);
    }

    @Test
    @DisplayName("Should authenticate with headers containing credentials")
    void testAuthenticateWithHeaders() {
        // Create a test token
        Token token = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Create a Map of headers with X-Client-ID and X-Client-Secret
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Client-ID", TEST_CLIENT_ID);
        headers.put("X-Client-Secret", TEST_CLIENT_SECRET);

        // Mock conjurService.validateCredentialsWithFallback to return true
        when(conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET)).thenReturn(true);
        
        // Mock tokenService.generateToken to return the test token
        when(tokenService.generateToken(TEST_CLIENT_ID)).thenReturn(token);

        // Call authenticationService.authenticateWithHeaders with the headers map
        Token result = authenticationService.authenticateWithHeaders(headers);

        // Verify the token is returned correctly
        assertNotNull(result);
        assertEquals(TEST_TOKEN_STRING, result.getTokenString());
        assertEquals(TEST_CLIENT_ID, result.getClientId());

        // Verify conjurService.validateCredentialsWithFallback was called with correct parameters
        verify(conjurService).validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify tokenService.generateToken was called with correct parameters
        verify(tokenService).generateToken(TEST_CLIENT_ID);
    }

    @Test
    @DisplayName("Should throw AuthenticationException with missing headers")
    void testAuthenticateWithMissingHeaders() {
        // Create a Map of headers with missing X-Client-ID or X-Client-Secret
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Client-ID", TEST_CLIENT_ID);
        // Deliberately omit client secret

        // Assert that calling authenticationService.authenticateWithHeaders throws AuthenticationException
        assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticateWithHeaders(headers);
        });

        // Verify conjurService.validateCredentialsWithFallback was never called
        verify(conjurService, never()).validateCredentialsWithFallback(anyString(), anyString());
        
        // Verify tokenService.generateToken was never called
        verify(tokenService, never()).generateToken(anyString());
    }

    @Test
    @DisplayName("Should validate token correctly")
    void testValidateToken() {
        // Mock tokenService.validateToken to return true
        when(tokenService.validateToken(TEST_TOKEN_STRING)).thenReturn(true);

        // Call authenticationService.validateToken with test token string
        boolean result = authenticationService.validateToken(TEST_TOKEN_STRING);

        // Verify the result is true
        assertTrue(result);

        // Verify tokenService.validateToken was called with the test token string
        verify(tokenService).validateToken(TEST_TOKEN_STRING);
    }

    @Test
    @DisplayName("Should refresh expired token successfully")
    void testRefreshToken() {
        // Create an expired test token
        Token expiredToken = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() - 3600000)) // 1 hour in the past
                .build();

        // Create a new test token
        Token newToken = Token.builder()
                .tokenString("new-token-string")
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Mock tokenService.parseToken to return the expired token
        when(tokenService.parseToken(TEST_TOKEN_STRING)).thenReturn(Optional.of(expiredToken));
        
        // Mock tokenService.renewToken to return the new token
        when(tokenService.renewToken(expiredToken)).thenReturn(newToken);

        // Call authenticationService.refreshToken with test token string
        Optional<Token> result = authenticationService.refreshToken(TEST_TOKEN_STRING);

        // Verify the new token is returned
        assertTrue(result.isPresent());
        assertEquals("new-token-string", result.get().getTokenString());

        // Verify tokenService.parseToken was called with the test token string
        verify(tokenService).parseToken(TEST_TOKEN_STRING);
        
        // Verify tokenService.renewToken was called with the expired token
        verify(tokenService).renewToken(expiredToken);
        
        // Verify cacheService.cacheToken was called with the new token
        verify(cacheService).cacheToken(newToken);
    }

    @Test
    @DisplayName("Should return original token if not expired")
    void testRefreshTokenWithNonExpiredToken() {
        // Create a non-expired test token
        Token nonExpiredToken = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Mock tokenService.parseToken to return the non-expired token
        when(tokenService.parseToken(TEST_TOKEN_STRING)).thenReturn(Optional.of(nonExpiredToken));

        // Call authenticationService.refreshToken with test token string
        Optional<Token> result = authenticationService.refreshToken(TEST_TOKEN_STRING);

        // Verify the original token is returned
        assertTrue(result.isPresent());
        assertEquals(TEST_TOKEN_STRING, result.get().getTokenString());

        // Verify tokenService.parseToken was called with the test token string
        verify(tokenService).parseToken(TEST_TOKEN_STRING);
        
        // Verify tokenService.renewToken was never called
        verify(tokenService, never()).renewToken(any(Token.class));
        
        // Verify cacheService.cacheToken was never called
        verify(cacheService, never()).cacheToken(any(Token.class));
    }

    @Test
    @DisplayName("Should revoke authentication successfully")
    void testRevokeAuthentication() {
        // Create a test token
        Token token = Token.builder()
                .tokenString(TEST_TOKEN_STRING)
                .clientId(TEST_CLIENT_ID)
                .jti("token-67890")
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in the future
                .build();

        // Mock cacheService.retrieveToken to return the test token
        when(cacheService.retrieveToken(TEST_CLIENT_ID)).thenReturn(Optional.of(token));
        
        // Mock tokenService.revokeToken to return true
        when(tokenService.revokeToken(token.getTokenId())).thenReturn(true);

        // Call authenticationService.revokeAuthentication with test client ID
        boolean result = authenticationService.revokeAuthentication(TEST_CLIENT_ID);

        // Verify the result is true
        assertTrue(result);

        // Verify cacheService.retrieveToken was called with the test client ID
        verify(cacheService).retrieveToken(TEST_CLIENT_ID);
        
        // Verify tokenService.revokeToken was called with the token ID
        verify(tokenService).revokeToken(token.getTokenId());
        
        // Verify cacheService.invalidateAllForClient was called with the test client ID
        verify(cacheService).invalidateAllForClient(TEST_CLIENT_ID);
    }
}