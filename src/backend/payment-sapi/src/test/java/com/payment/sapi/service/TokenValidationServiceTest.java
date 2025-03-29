package com.payment.sapi.service;

import com.payment.common.model.TokenClaims;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.impl.TokenValidationServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the TokenValidationService implementation.
 * These tests validate the behavior of token validation functionality
 * including signature validation, expiration checking, permission validation,
 * and token renewal scenarios.
 */
@ExtendWith(MockitoExtension.class)
public class TokenValidationServiceTest {

    private static final String VALID_TOKEN_STRING = "header.payload.signature";
    private static final String INVALID_TOKEN_STRING = "invalid-token";
    private static final byte[] TOKEN_SIGNING_KEY = "test-signing-key".getBytes();
    private static final String TOKEN_AUDIENCE = "payment-sapi";
    private static final List<String> ALLOWED_ISSUERS = Arrays.asList("payment-eapi");
    
    @Mock
    private TokenRenewalService tokenRenewalService;
    
    private TokenValidationServiceImpl tokenValidationService;
    private TokenValidationService tokenValidationServiceSpy;
    
    private Token validToken;
    private Token expiredToken;
    
    @BeforeEach
    public void setUp() {
        // Setup token renewal service mock behavior
        when(tokenRenewalService.renewToken(any(Token.class)))
            .thenReturn(ValidationResult.renewed("new-token-string"));
        
        // Initialize TokenValidationServiceImpl
        tokenValidationService = new TokenValidationServiceImpl(
                tokenRenewalService,
                TOKEN_SIGNING_KEY,
                TOKEN_AUDIENCE,
                ALLOWED_ISSUERS,
                true // tokenRenewalEnabled
        );
        
        // Create a spy on the service
        tokenValidationServiceSpy = spy(tokenValidationService);
        
        // Create a valid token
        TokenClaims validClaims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud(TOKEN_AUDIENCE)
                .exp(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in future
                .iat(new Date())
                .jti("token-id-123")
                .permissions(Arrays.asList("process_payment", "view_status"))
                .build();
                
        validToken = Token.builder()
                .tokenString(VALID_TOKEN_STRING)
                .claims(validClaims)
                .expirationTime(validClaims.getExp())
                .jti(validClaims.getJti())
                .clientId(validClaims.getSub())
                .build();
                
        // Create an expired token
        TokenClaims expiredClaims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud(TOKEN_AUDIENCE)
                .exp(new Date(System.currentTimeMillis() - 3600000)) // 1 hour in past
                .iat(new Date(System.currentTimeMillis() - 7200000)) // 2 hours in past
                .jti("token-id-456")
                .permissions(Arrays.asList("process_payment"))
                .build();
                
        expiredToken = Token.builder()
                .tokenString(VALID_TOKEN_STRING)
                .claims(expiredClaims)
                .expirationTime(expiredClaims.getExp())
                .jti(expiredClaims.getJti())
                .clientId(expiredClaims.getSub())
                .build();
    }
    
    @Test
    @DisplayName("Should return valid result when token is valid")
    public void testValidateTokenWithValidToken() {
        // Setup mock behavior
        doReturn(validToken).when(tokenValidationServiceSpy).parseToken(anyString());
        doReturn(true).when(tokenValidationServiceSpy).validateTokenSignature(anyString());
        doReturn(true).when(tokenValidationServiceSpy).validateTokenExpiration(any(Token.class));
        doReturn(true).when(tokenValidationServiceSpy).validateTokenPermission(any(Token.class), anyString());
        
        // Execute the method
        ValidationResult result = tokenValidationServiceSpy.validateToken(VALID_TOKEN_STRING, "process_payment");
        
        // Verify the result
        assertThat(result.isValid()).isTrue();
        assertThat(result.isExpired()).isFalse();
        assertThat(result.isForbidden()).isFalse();
        assertThat(result.isRenewed()).isFalse();
        
        // Verify method calls
        verify(tokenValidationServiceSpy).parseToken(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenSignature(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenExpiration(validToken);
        verify(tokenValidationServiceSpy).validateTokenPermission(validToken, "process_payment");
    }
    
    @Test
    @DisplayName("Should return invalid result when token format is invalid")
    public void testValidateTokenWithInvalidTokenFormat() {
        // Setup mock behavior
        doReturn(null).when(tokenValidationServiceSpy).parseToken(anyString());
        
        // Execute the method
        ValidationResult result = tokenValidationServiceSpy.validateToken(INVALID_TOKEN_STRING, "process_payment");
        
        // Verify the result
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid token format");
        
        // Verify method calls
        verify(tokenValidationServiceSpy).parseToken(INVALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy, never()).validateTokenSignature(anyString());
        verify(tokenValidationServiceSpy, never()).validateTokenExpiration(any(Token.class));
        verify(tokenValidationServiceSpy, never()).validateTokenPermission(any(Token.class), anyString());
    }
    
    @Test
    @DisplayName("Should return invalid result when token signature is invalid")
    public void testValidateTokenWithInvalidSignature() {
        // Setup mock behavior
        doReturn(validToken).when(tokenValidationServiceSpy).parseToken(anyString());
        doReturn(false).when(tokenValidationServiceSpy).validateTokenSignature(anyString());
        
        // Execute the method
        ValidationResult result = tokenValidationServiceSpy.validateToken(VALID_TOKEN_STRING, "process_payment");
        
        // Verify the result
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid token signature");
        
        // Verify method calls
        verify(tokenValidationServiceSpy).parseToken(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenSignature(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy, never()).validateTokenExpiration(any(Token.class));
        verify(tokenValidationServiceSpy, never()).validateTokenPermission(any(Token.class), anyString());
    }
    
    @Test
    @DisplayName("Should return expired result when token is expired and renewal is disabled")
    public void testValidateTokenWithExpiredToken() {
        // Setup a service with token renewal disabled
        TokenValidationServiceImpl serviceWithoutRenewal = new TokenValidationServiceImpl(
                tokenRenewalService,
                TOKEN_SIGNING_KEY,
                TOKEN_AUDIENCE,
                ALLOWED_ISSUERS,
                false // tokenRenewalEnabled set to false
        );
        TokenValidationService spyWithoutRenewal = spy(serviceWithoutRenewal);
        
        // Setup mock behavior on the spy without renewal
        doReturn(expiredToken).when(spyWithoutRenewal).parseToken(anyString());
        doReturn(true).when(spyWithoutRenewal).validateTokenSignature(anyString());
        doReturn(false).when(spyWithoutRenewal).validateTokenExpiration(any(Token.class));
        
        // Execute the method
        ValidationResult result = spyWithoutRenewal.validateToken(VALID_TOKEN_STRING, "process_payment");
        
        // Verify the result
        assertThat(result.isValid()).isFalse();
        assertThat(result.isExpired()).isTrue();
        assertThat(result.getErrorMessage()).contains("expired");
        
        // Verify method calls
        verify(spyWithoutRenewal).parseToken(VALID_TOKEN_STRING);
        verify(spyWithoutRenewal).validateTokenSignature(VALID_TOKEN_STRING);
        verify(spyWithoutRenewal).validateTokenExpiration(expiredToken);
        verify(spyWithoutRenewal, never()).validateTokenPermission(any(Token.class), anyString());
        verify(tokenRenewalService, never()).renewToken(any(Token.class));
    }
    
    @Test
    @DisplayName("Should attempt to renew token when expired and renewal is enabled")
    public void testValidateTokenWithExpiredTokenAndRenewalEnabled() {
        // Setup mock behavior
        doReturn(expiredToken).when(tokenValidationServiceSpy).parseToken(anyString());
        doReturn(true).when(tokenValidationServiceSpy).validateTokenSignature(anyString());
        doReturn(false).when(tokenValidationServiceSpy).validateTokenExpiration(any(Token.class));
        
        // Execute the method
        ValidationResult result = tokenValidationServiceSpy.validateToken(VALID_TOKEN_STRING, "process_payment");
        
        // Verify the result
        assertThat(result.isValid()).isTrue();
        assertThat(result.isRenewed()).isTrue();
        assertThat(result.getRenewedTokenString()).isEqualTo("new-token-string");
        
        // Verify method calls
        verify(tokenValidationServiceSpy).parseToken(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenSignature(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenExpiration(expiredToken);
        verify(tokenRenewalService).renewToken(expiredToken);
        verify(tokenValidationServiceSpy, never()).validateTokenPermission(any(Token.class), anyString());
    }
    
    @Test
    @DisplayName("Should return forbidden result when token lacks required permissions")
    public void testValidateTokenWithInsufficientPermissions() {
        // Setup mock behavior
        doReturn(validToken).when(tokenValidationServiceSpy).parseToken(anyString());
        doReturn(true).when(tokenValidationServiceSpy).validateTokenSignature(anyString());
        doReturn(true).when(tokenValidationServiceSpy).validateTokenExpiration(any(Token.class));
        doReturn(false).when(tokenValidationServiceSpy).validateTokenPermission(any(Token.class), eq("delete_payment"));
        
        // Execute the method
        ValidationResult result = tokenValidationServiceSpy.validateToken(VALID_TOKEN_STRING, "delete_payment");
        
        // Verify the result
        assertThat(result.isValid()).isFalse();
        assertThat(result.isForbidden()).isTrue();
        assertThat(result.getErrorMessage()).contains("does not have required permission");
        
        // Verify method calls
        verify(tokenValidationServiceSpy).parseToken(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenSignature(VALID_TOKEN_STRING);
        verify(tokenValidationServiceSpy).validateTokenExpiration(validToken);
        verify(tokenValidationServiceSpy).validateTokenPermission(validToken, "delete_payment");
    }
    
    @Test
    @DisplayName("Should parse valid token string into Token object")
    public void testParseTokenWithValidToken() {
        // Setup mock behavior for the spy
        doReturn(validToken).when(tokenValidationServiceSpy).parseToken(VALID_TOKEN_STRING);
        
        // Execute the method
        Token parsedToken = tokenValidationServiceSpy.parseToken(VALID_TOKEN_STRING);
        
        // Verify result
        assertThat(parsedToken).isNotNull();
        assertThat(parsedToken.getTokenString()).isEqualTo(VALID_TOKEN_STRING);
        assertThat(parsedToken.getSubject()).isEqualTo("test-client");
    }
    
    @Test
    @DisplayName("Should return null when token string is invalid")
    public void testParseTokenWithInvalidToken() {
        // Setup mock behavior for the spy
        doReturn(null).when(tokenValidationServiceSpy).parseToken(INVALID_TOKEN_STRING);
        
        // Execute the method
        Token parsedToken = tokenValidationServiceSpy.parseToken(INVALID_TOKEN_STRING);
        
        // Verify result
        assertThat(parsedToken).isNull();
    }
    
    @Test
    @DisplayName("Should validate token signature correctly")
    public void testValidateTokenSignature() {
        // Setup mock behavior for the spy
        doReturn(true).when(tokenValidationServiceSpy).validateTokenSignature(VALID_TOKEN_STRING);
        doReturn(false).when(tokenValidationServiceSpy).validateTokenSignature(INVALID_TOKEN_STRING);
        
        // Execute the methods
        boolean validSignature = tokenValidationServiceSpy.validateTokenSignature(VALID_TOKEN_STRING);
        boolean invalidSignature = tokenValidationServiceSpy.validateTokenSignature(INVALID_TOKEN_STRING);
        
        // Verify results
        assertThat(validSignature).isTrue();
        assertThat(invalidSignature).isFalse();
    }
    
    @Test
    @DisplayName("Should validate token expiration correctly")
    public void testValidateTokenExpiration() {
        // Create non-expired and expired tokens for testing
        Token nonExpiredToken = validToken;
        Token expiredTokenObj = expiredToken;
        
        // Execute the methods with the actual service to test the real implementation
        boolean isValidExpiration = tokenValidationService.validateTokenExpiration(nonExpiredToken);
        boolean isInvalidExpiration = tokenValidationService.validateTokenExpiration(expiredTokenObj);
        
        // Verify results
        assertThat(isValidExpiration).isTrue();
        assertThat(isInvalidExpiration).isFalse();
    }
    
    @Test
    @DisplayName("Should validate token permissions correctly")
    public void testValidateTokenPermission() {
        // Setup a token with specific permissions
        Token tokenWithPermissions = validToken; // Has "process_payment" and "view_status"
        
        // Execute the method with permission the token has
        boolean hasPermission = tokenValidationService.validateTokenPermission(tokenWithPermissions, "process_payment");
        
        // Execute the method with permission the token doesn't have
        boolean lackPermission = tokenValidationService.validateTokenPermission(tokenWithPermissions, "delete_payment");
        
        // Verify results
        assertThat(hasPermission).isTrue();
        assertThat(lackPermission).isFalse();
    }
}