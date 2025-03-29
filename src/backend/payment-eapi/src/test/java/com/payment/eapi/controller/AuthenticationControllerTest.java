package com.payment.eapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.payment.eapi.exception.AuthenticationException;
import com.payment.eapi.model.AuthenticationRequest;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.model.Token;
import com.payment.eapi.service.AuthenticationService;

/**
 * Unit test class for the AuthenticationController that verifies all authentication endpoints
 */
@ExtendWith(MockitoExtension.class)
public class AuthenticationControllerTest {
    
    @Mock
    private AuthenticationService authenticationService;
    
    @InjectMocks
    private AuthenticationController controller;
    
    private String clientId;
    private String clientSecret;
    private String tokenString;
    private Token token;
    
    @BeforeEach
    void setUp() {
        // Initialize test data
        clientId = "test-client";
        clientSecret = "test-secret";
        tokenString = "sample.jwt.token";
        
        // Create a test token with expiration time
        token = Token.builder()
                .tokenString(tokenString)
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in future
                .clientId(clientId)
                .jti("test-token-id")
                .build();
    }
    
    @Test
    void testAuthenticateWithValidCredentials() {
        // Arrange
        AuthenticationRequest request = new AuthenticationRequest(clientId, clientSecret);
        AuthenticationResponse expectedResponse = AuthenticationResponse.builder()
                .token(tokenString)
                .expiresAt(new Date(System.currentTimeMillis() + 3600000))
                .tokenType("Bearer")
                .build();
        
        when(authenticationService.authenticate(any(AuthenticationRequest.class)))
                .thenReturn(expectedResponse);
        
        // Act
        ResponseEntity<AuthenticationResponse> response = controller.authenticate(request);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedResponse.getToken(), response.getBody().getToken());
        assertEquals(expectedResponse.getExpiresAt(), response.getBody().getExpiresAt());
        
        // Verify service was called
        verify(authenticationService).authenticate(request);
    }
    
    @Test
    void testAuthenticateWithInvalidCredentials() {
        // Arrange
        AuthenticationRequest request = new AuthenticationRequest(clientId, "wrong-secret");
        
        when(authenticationService.authenticate(any(AuthenticationRequest.class)))
                .thenThrow(new AuthenticationException("Invalid credentials"));
        
        // Act & Assert
        assertThrows(AuthenticationException.class, () -> {
            controller.authenticate(request);
        });
        
        // Verify service was called
        verify(authenticationService).authenticate(request);
    }
    
    @Test
    void testAuthenticateWithHeadersValidCredentials() {
        // Arrange
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("X-Client-ID", clientId);
        expectedHeaders.put("X-Client-Secret", clientSecret);
        
        when(authenticationService.authenticateWithHeaders(eq(expectedHeaders)))
                .thenReturn(token);
        
        // Act
        ResponseEntity<AuthenticationResponse> response = controller.authenticateWithHeaders(clientId, clientSecret);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(token.getTokenString(), response.getBody().getToken());
        
        // Verify service was called with correct headers
        verify(authenticationService).authenticateWithHeaders(eq(expectedHeaders));
    }
    
    @Test
    void testAuthenticateWithHeadersInvalidCredentials() {
        // Arrange
        when(authenticationService.authenticateWithHeaders(any(Map.class)))
                .thenThrow(new AuthenticationException("Invalid credentials"));
        
        // Act & Assert
        assertThrows(AuthenticationException.class, () -> {
            controller.authenticateWithHeaders(clientId, "wrong-secret");
        });
        
        // Verify service was called with headers
        verify(authenticationService).authenticateWithHeaders(any(Map.class));
    }
    
    @Test
    void testValidateTokenValid() {
        // Arrange
        when(authenticationService.validateToken(tokenString))
                .thenReturn(true);
        
        // Act
        ResponseEntity<Boolean> response = controller.validateToken(tokenString);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
        
        // Verify service was called
        verify(authenticationService).validateToken(tokenString);
    }
    
    @Test
    void testValidateTokenInvalid() {
        // Arrange
        when(authenticationService.validateToken(tokenString))
                .thenReturn(false);
        
        // Act
        ResponseEntity<Boolean> response = controller.validateToken(tokenString);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody());
        
        // Verify service was called
        verify(authenticationService).validateToken(tokenString);
    }
    
    @Test
    void testValidateTokenException() {
        // Arrange
        when(authenticationService.validateToken(tokenString))
                .thenThrow(new RuntimeException("Validation error"));
        
        // Act
        ResponseEntity<Boolean> response = controller.validateToken(tokenString);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody());
        
        // Verify service was called
        verify(authenticationService).validateToken(tokenString);
    }
    
    @Test
    void testRefreshTokenSuccess() {
        // Arrange
        String expiredToken = "expired.jwt.token";
        Token refreshedToken = Token.builder()
                .tokenString("refreshed.jwt.token")
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour in future
                .clientId(clientId)
                .jti("refreshed-token-id")
                .build();
        
        when(authenticationService.refreshToken(expiredToken))
                .thenReturn(Optional.of(refreshedToken));
        
        // Act
        ResponseEntity<AuthenticationResponse> response = controller.refreshToken(expiredToken);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(refreshedToken.getTokenString(), response.getBody().getToken());
        
        // Verify service was called
        verify(authenticationService).refreshToken(expiredToken);
    }
    
    @Test
    void testRefreshTokenFailure() {
        // Arrange
        String expiredToken = "expired.jwt.token";
        
        when(authenticationService.refreshToken(expiredToken))
                .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(AuthenticationException.class, () -> {
            controller.refreshToken(expiredToken);
        });
        
        // Verify service was called
        verify(authenticationService).refreshToken(expiredToken);
    }
    
    @Test
    void testCheckTokenStatusValid() {
        // Arrange
        String tokenId = "valid-token-id";
        
        when(authenticationService.validateToken(tokenId))
                .thenReturn(true);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.checkTokenStatus(tokenId);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("valid"));
        
        // Verify service was called
        verify(authenticationService).validateToken(tokenId);
    }
    
    @Test
    void testCheckTokenStatusInvalid() {
        // Arrange
        String tokenId = "invalid-token-id";
        
        when(authenticationService.validateToken(tokenId))
                .thenReturn(false);
        
        // Act
        ResponseEntity<Map<String, Object>> response = controller.checkTokenStatus(tokenId);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("valid"));
        
        // Verify service was called
        verify(authenticationService).validateToken(tokenId);
    }
}