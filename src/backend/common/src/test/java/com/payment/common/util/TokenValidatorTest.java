package com.payment.common.util;

import com.payment.common.model.TokenClaims;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TokenValidator utility class.
 * Tests all validation methods for JWT tokens including signature verification,
 * token parsing, and validation of token claims.
 */
public class TokenValidatorTest {

    private ObjectMapper objectMapper;
    private byte[] signingKey;
    private String validToken;
    private TokenClaims validClaims;
    private TokenClaims expiredClaims;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize object mapper
        objectMapper = new ObjectMapper();
        
        // Generate a signing key for testing
        signingKey = "test-signing-key-for-unit-tests".getBytes();
        
        // Create valid token claims
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 1); // Token expires in 1 hour
        Date futureDate = calendar.getTime();
        
        validClaims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud("payment-sapi")
                .exp(futureDate)
                .iat(new Date())
                .jti("token-12345")
                .permissions(Arrays.asList("process_payment", "view_status"))
                .build();
        
        // Create expired token claims
        calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, -1); // Token expired 1 hour ago
        Date pastDate = calendar.getTime();
        
        expiredClaims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud("payment-sapi")
                .exp(pastDate)
                .iat(pastDate)
                .jti("token-expired")
                .permissions(Arrays.asList("process_payment", "view_status"))
                .build();
        
        // Generate a valid token for testing
        validToken = createToken(validClaims);
    }

    @Test
    @DisplayName("Should validate token with valid signature")
    void testValidateToken_withValidToken_shouldReturnTrue() {
        boolean result = TokenValidator.validateToken(validToken, signingKey);
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token with invalid signature")
    void testValidateToken_withInvalidSignature_shouldReturnFalse() {
        // Create a token with tampered signature
        String[] parts = validToken.split("\\.");
        
        // Create a Base64URL encoded invalid signature
        String encodedInvalidSignature = Base64.getUrlEncoder().withoutPadding().encodeToString("invalid-signature".getBytes());
        
        String tamperedToken = parts[0] + "." + parts[1] + "." + encodedInvalidSignature;
        
        boolean result = TokenValidator.validateToken(tamperedToken, signingKey);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject token with invalid format")
    void testValidateToken_withInvalidFormat_shouldReturnFalse() {
        // Create an invalid token (missing parts)
        String invalidToken = "header.payload";
        
        boolean result = TokenValidator.validateToken(invalidToken, signingKey);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should parse valid token into claims")
    void testParseToken_withValidToken_shouldReturnClaims() {
        TokenClaims claims = TokenValidator.parseToken(validToken);
        
        assertNotNull(claims);
        assertEquals("test-client", claims.getSub());
        assertEquals("payment-eapi", claims.getIss());
        assertEquals("payment-sapi", claims.getAud());
        assertEquals("token-12345", claims.getJti());
        assertTrue(claims.getPermissions().contains("process_payment"));
        assertTrue(claims.getPermissions().contains("view_status"));
    }

    @Test
    @DisplayName("Should return null when parsing invalid token")
    void testParseToken_withInvalidToken_shouldReturnNull() {
        String invalidToken = "not.a.valid.token";
        
        TokenClaims claims = TokenValidator.parseToken(invalidToken);
        
        assertNull(claims);
    }

    @Test
    @DisplayName("Should validate token with future expiration date")
    void testValidateTokenExpiration_withValidToken_shouldReturnTrue() {
        boolean result = TokenValidator.validateTokenExpiration(validClaims);
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token with past expiration date")
    void testValidateTokenExpiration_withExpiredToken_shouldReturnFalse() {
        boolean result = TokenValidator.validateTokenExpiration(expiredClaims);
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate token with required permission")
    void testValidateTokenPermission_withValidPermission_shouldReturnTrue() {
        boolean result = TokenValidator.validateTokenPermission(validClaims, "process_payment");
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token without required permission")
    void testValidateTokenPermission_withInvalidPermission_shouldReturnFalse() {
        boolean result = TokenValidator.validateTokenPermission(validClaims, "delete_payment");
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate token with matching audience")
    void testValidateTokenAudience_withValidAudience_shouldReturnTrue() {
        boolean result = TokenValidator.validateTokenAudience(validClaims, "payment-sapi");
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token with non-matching audience")
    void testValidateTokenAudience_withInvalidAudience_shouldReturnFalse() {
        boolean result = TokenValidator.validateTokenAudience(validClaims, "payment-other");
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate token with allowed issuer")
    void testValidateTokenIssuer_withValidIssuer_shouldReturnTrue() {
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenIssuer(validClaims, allowedIssuers);
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token with disallowed issuer")
    void testValidateTokenIssuer_withInvalidIssuer_shouldReturnFalse() {
        List<String> allowedIssuers = Arrays.asList("payment-other", "payment-third");
        boolean result = TokenValidator.validateTokenIssuer(validClaims, allowedIssuers);
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should validate token with all valid claims")
    void testValidateTokenAndClaims_withAllValid_shouldReturnTrue() {
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenAndClaims(
                validToken,
                signingKey,
                "payment-sapi",
                allowedIssuers,
                "process_payment"
        );
        
        assertTrue(result);
    }

    @Test
    @DisplayName("Should reject token with invalid signature in comprehensive validation")
    void testValidateTokenAndClaims_withInvalidSignature_shouldReturnFalse() {
        // Create a token with tampered signature
        String[] parts = validToken.split("\\.");
        String encodedInvalidSignature = Base64.getUrlEncoder().withoutPadding().encodeToString("invalid-signature".getBytes());
        String tamperedToken = parts[0] + "." + parts[1] + "." + encodedInvalidSignature;
        
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenAndClaims(
                tamperedToken,
                signingKey,
                "payment-sapi",
                allowedIssuers,
                "process_payment"
        );
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject expired token in comprehensive validation")
    void testValidateTokenAndClaims_withExpiredToken_shouldReturnFalse() {
        // Create an expired token
        String expiredToken = createToken(expiredClaims);
        
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenAndClaims(
                expiredToken,
                signingKey,
                "payment-sapi",
                allowedIssuers,
                "process_payment"
        );
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject token with invalid audience in comprehensive validation")
    void testValidateTokenAndClaims_withInvalidAudience_shouldReturnFalse() {
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenAndClaims(
                validToken,
                signingKey,
                "payment-other", // Invalid audience
                allowedIssuers,
                "process_payment"
        );
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject token with invalid issuer in comprehensive validation")
    void testValidateTokenAndClaims_withInvalidIssuer_shouldReturnFalse() {
        List<String> allowedIssuers = Arrays.asList("payment-other", "payment-third"); // Disallowed issuers
        boolean result = TokenValidator.validateTokenAndClaims(
                validToken,
                signingKey,
                "payment-sapi",
                allowedIssuers,
                "process_payment"
        );
        
        assertFalse(result);
    }

    @Test
    @DisplayName("Should reject token without required permission in comprehensive validation")
    void testValidateTokenAndClaims_withInvalidPermission_shouldReturnFalse() {
        List<String> allowedIssuers = Arrays.asList("payment-eapi", "payment-other");
        boolean result = TokenValidator.validateTokenAndClaims(
                validToken,
                signingKey,
                "payment-sapi",
                allowedIssuers,
                "delete_payment" // Invalid permission
        );
        
        assertFalse(result);
    }

    /**
     * Helper method to create a JWT token with the given claims
     */
    private String createToken(TokenClaims claims) {
        try {
            // Create header
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes());
            
            // Create payload
            String payload = objectMapper.writeValueAsString(claims);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
            
            // Combine header and payload
            String dataToSign = encodedHeader + "." + encodedPayload;
            
            // Generate signature
            byte[] signature = SecurityUtils.generateHmac(dataToSign.getBytes(), signingKey);
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
            
            // Combine all parts to create the token
            return dataToSign + "." + encodedSignature;
        } catch (Exception e) {
            throw new RuntimeException("Error creating token for testing", e);
        }
    }
}