package com.payment.sapi.controller;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.common.model.ErrorResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the TokenController class, which is responsible for token validation
 * and renewal in the Payment-Sapi component. These tests verify that the controller
 * correctly handles various token validation and renewal scenarios.
 * <p>
 * The tests use MockMvc to simulate HTTP requests and Mockito to mock the
 * dependencies of the TokenController.
 */
@ExtendWith(MockitoExtension.class)
public class TokenControllerTest {

    @Mock
    private TokenValidationService tokenValidationService;

    @Mock
    private TokenRenewalService tokenRenewalService;

    @InjectMocks
    private TokenController tokenController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TEST_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LWNsaWVudCIsImlzcyI6InBheW1lbnQtZWFwaSIsImF1ZCI6InBheW1lbnQtc2FwaSIsImV4cCI6MTYyMzc2MTQ0NSwiaWF0IjoxNjIzNzU3ODQ1LCJqdGkiOiJ0b2tlbi02Nzg5MCIsInBlcm1pc3Npb25zIjpbInByb2Nlc3NfcGF5bWVudCJdfQ.signature";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_PERMISSION = "process_payment";
    private static final String RENEWED_TOKEN = "renewed-token-string";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(tokenController).build();
    }

    /**
     * Test successful token validation.
     * Verifies that the controller returns HTTP 200 OK with a valid result when
     * token validation is successful.
     */
    @Test
    void testValidateToken_Success() throws Exception {
        // Configure tokenValidationService to return successful validation
        ValidationResult validResult = ValidationResult.valid();
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION)))
                .thenReturn(validResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN)
                .header("X-Required-Permission", TEST_PERMISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.forbidden").value(false))
                .andExpect(jsonPath("$.renewed").value(false));

        // Verify service was called
        verify(tokenValidationService).validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION));
    }

    /**
     * Test validation of an invalid token.
     * Verifies that the controller returns HTTP 401 Unauthorized with an error message
     * when token validation fails due to an invalid token.
     */
    @Test
    void testValidateToken_Invalid() throws Exception {
        // Configure tokenValidationService to return invalid validation
        ValidationResult invalidResult = ValidationResult.invalid("Invalid token signature");
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION)))
                .thenReturn(invalidResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN)
                .header("X-Required-Permission", TEST_PERMISSION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Invalid token signature"));

        // Verify service was called
        verify(tokenValidationService).validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION));
    }

    /**
     * Test validation of an expired token.
     * Verifies that the controller returns HTTP 401 Unauthorized with an expiration message
     * when token validation fails due to an expired token.
     */
    @Test
    void testValidateToken_Expired() throws Exception {
        // Configure tokenValidationService to return expired validation
        ValidationResult expiredResult = ValidationResult.expired("Token has expired");
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION)))
                .thenReturn(expiredResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN)
                .header("X-Required-Permission", TEST_PERMISSION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Token has expired"));

        // Verify service was called
        verify(tokenValidationService).validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION));
    }

    /**
     * Test validation of a token with insufficient permissions.
     * Verifies that the controller returns HTTP 403 Forbidden with a permissions error message
     * when token validation fails due to insufficient permissions.
     */
    @Test
    void testValidateToken_Forbidden() throws Exception {
        // Configure tokenValidationService to return forbidden validation
        ValidationResult forbiddenResult = ValidationResult.forbidden("Insufficient permissions");
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION)))
                .thenReturn(forbiddenResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN)
                .header("X-Required-Permission", TEST_PERMISSION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_PERMISSIONS"))
                .andExpect(jsonPath("$.message").value("Insufficient permissions"));

        // Verify service was called
        verify(tokenValidationService).validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION));
    }

    /**
     * Test validation of an expired token that gets renewed.
     * Verifies that the controller returns HTTP 200 OK with a renewed token
     * when token validation results in token renewal.
     */
    @Test
    void testValidateToken_Renewed() throws Exception {
        // Configure tokenValidationService to return renewed validation
        ValidationResult renewedResult = ValidationResult.renewed(RENEWED_TOKEN);
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION)))
                .thenReturn(renewedResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN)
                .header("X-Required-Permission", TEST_PERMISSION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.renewed").value(true))
                .andExpect(jsonPath("$.renewedTokenString").value(RENEWED_TOKEN));

        // Verify service was called
        verify(tokenValidationService).validateToken(eq(TEST_TOKEN), eq(TEST_PERMISSION));
    }

    /**
     * Test successful token renewal.
     * Verifies that the controller returns HTTP 200 OK with a renewed token
     * when token renewal is successful.
     */
    @Test
    void testRenewToken_Success() throws Exception {
        // Create a token and configure the mocks
        Token token = createTestToken(true); // Create expired token
        ValidationResult renewedResult = ValidationResult.renewed(RENEWED_TOKEN);
        
        when(tokenValidationService.parseToken(eq(TEST_TOKEN))).thenReturn(token);
        when(tokenRenewalService.shouldRenew(eq(token))).thenReturn(true);
        when(tokenRenewalService.renewToken(eq(token))).thenReturn(renewedResult);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/renew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renewed").value(true))
                .andExpect(jsonPath("$.renewedTokenString").value(RENEWED_TOKEN));

        // Verify services were called
        verify(tokenValidationService).parseToken(eq(TEST_TOKEN));
        verify(tokenRenewalService).shouldRenew(eq(token));
        verify(tokenRenewalService).renewToken(eq(token));
    }

    /**
     * Test token renewal with parsing failure.
     * Verifies that the controller returns HTTP 400 Bad Request with an error message
     * when token renewal fails due to token parsing issues.
     */
    @Test
    void testRenewToken_ParseFailure() throws Exception {
        // Configure tokenValidationService to return null (parse failure)
        when(tokenValidationService.parseToken(eq(TEST_TOKEN))).thenReturn(null);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/renew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN_FORMAT"))
                .andExpect(jsonPath("$.message").value("Unable to parse token"));

        // Verify service was called
        verify(tokenValidationService).parseToken(eq(TEST_TOKEN));
        verify(tokenRenewalService, never()).renewToken(any(Token.class));
    }

    /**
     * Test token renewal when renewal is not needed.
     * Verifies that the controller returns HTTP 200 OK with the original token
     * when token renewal is not required (e.g., token not near expiration).
     */
    @Test
    void testRenewToken_NotNeeded() throws Exception {
        // Create a token and configure the mocks
        Token token = createTestToken(false); // Create non-expired token
        
        when(tokenValidationService.parseToken(eq(TEST_TOKEN))).thenReturn(token);
        when(tokenRenewalService.shouldRenew(eq(token))).thenReturn(false);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/renew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.renewed").value(false));

        // Verify services were called
        verify(tokenValidationService).parseToken(eq(TEST_TOKEN));
        verify(tokenRenewalService).shouldRenew(eq(token));
        verify(tokenRenewalService, never()).renewToken(any(Token.class));
    }

    /**
     * Test token renewal with renewal failure.
     * Verifies that the controller returns HTTP 500 Internal Server Error with an error message
     * when token renewal fails due to renewal service issues.
     */
    @Test
    void testRenewToken_RenewalFailure() throws Exception {
        // Create a token and configure the mocks
        Token token = createTestToken(true); // Create expired token
        ValidationResult failedRenewal = ValidationResult.invalid("Renewal service unavailable");
        
        when(tokenValidationService.parseToken(eq(TEST_TOKEN))).thenReturn(token);
        when(tokenRenewalService.shouldRenew(eq(token))).thenReturn(true);
        when(tokenRenewalService.renewToken(eq(token))).thenReturn(failedRenewal);

        // Perform request
        mockMvc.perform(post("/internal/v1/tokens/renew")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEST_TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("RENEWAL_FAILED"))
                .andExpect(jsonPath("$.message").value("Renewal service unavailable"));

        // Verify services were called
        verify(tokenValidationService).parseToken(eq(TEST_TOKEN));
        verify(tokenRenewalService).shouldRenew(eq(token));
        verify(tokenRenewalService).renewToken(eq(token));
    }

    /**
     * Helper method to create a test token.
     * 
     * @param expired whether the token should be expired
     * @return a Token object for testing
     */
    private Token createTestToken(boolean expired) {
        return Token.builder()
                .tokenString(TEST_TOKEN)
                .clientId(TEST_CLIENT_ID)
                .expirationTime(expired ? new Date(System.currentTimeMillis() - 1000) : new Date(System.currentTimeMillis() + 3600000))
                .build();
    }
}