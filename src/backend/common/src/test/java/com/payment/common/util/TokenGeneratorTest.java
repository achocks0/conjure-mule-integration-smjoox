package com.payment.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.model.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TokenGenerator class which verifies the correct generation of JWT
 * tokens with proper claims and signatures.
 */
public class TokenGeneratorTest {

    private byte[] signingKey;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Generate a random signing key for testing
        String randomKey = SecurityUtils.generateSecureRandomString(32);
        signingKey = randomKey.getBytes();
        
        // Initialize ObjectMapper for JSON parsing
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGenerateTokenWithTokenClaims() throws Exception {
        // Create TokenClaims with test values
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000); // 1 hour in future
        String jti = "test-token-id-12345";
        List<String> permissions = new ArrayList<>();
        permissions.add("process_payment");
        permissions.add("view_status");
        
        TokenClaims claims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud("payment-sapi")
                .exp(expiration)
                .iat(now)
                .jti(jti)
                .permissions(permissions)
                .build();

        // Generate token
        String token = TokenGenerator.generateToken(claims, signingKey);

        // Verify token is not null or empty
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Split token into parts
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "Token should have three parts");

        // Decode payload
        String decodedPayload = decodeBase64(parts[1]);
        Map<String, Object> payloadMap = objectMapper.readValue(decodedPayload, Map.class);

        // Verify claims
        assertEquals("test-client", payloadMap.get("sub"));
        assertEquals("payment-eapi", payloadMap.get("iss"));
        assertEquals("payment-sapi", payloadMap.get("aud"));
        // Date values are stored as timestamps - convert to compare
        assertEquals(expiration.getTime() / 1000, ((Number) payloadMap.get("exp")).longValue());
        assertEquals(now.getTime() / 1000, ((Number) payloadMap.get("iat")).longValue());
        assertEquals(jti, payloadMap.get("jti"));
        assertEquals(permissions, payloadMap.get("permissions"));
    }

    @Test
    void testGenerateTokenWithClaimsMap() throws Exception {
        // Create claims Map with test values
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600000); // 1 hour in future
        String jti = "test-token-id-12345";
        List<String> permissions = new ArrayList<>();
        permissions.add("process_payment");
        permissions.add("view_status");
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "test-client");
        claims.put("iss", "payment-eapi");
        claims.put("aud", "payment-sapi");
        claims.put("exp", expiration.getTime() / 1000);
        claims.put("iat", now.getTime() / 1000);
        claims.put("jti", jti);
        claims.put("permissions", permissions);

        // Generate token
        String token = TokenGenerator.generateToken(claims, signingKey);

        // Verify token is not null or empty
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Split token into parts
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "Token should have three parts");

        // Decode payload
        String decodedPayload = decodeBase64(parts[1]);
        Map<String, Object> payloadMap = objectMapper.readValue(decodedPayload, Map.class);

        // Verify claims
        assertEquals("test-client", payloadMap.get("sub"));
        assertEquals("payment-eapi", payloadMap.get("iss"));
        assertEquals("payment-sapi", payloadMap.get("aud"));
        assertEquals(expiration.getTime() / 1000, ((Number) payloadMap.get("exp")).longValue());
        assertEquals(now.getTime() / 1000, ((Number) payloadMap.get("iat")).longValue());
        assertEquals(jti, payloadMap.get("jti"));
        assertEquals(permissions, payloadMap.get("permissions"));
    }

    @Test
    void testGenerateTokenWithNullClaims() {
        // Should throw exception when TokenClaims is null
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            TokenGenerator.generateToken((TokenClaims) null, signingKey);
        });
        
        assertTrue(exception.getMessage().contains("Claims and signing key cannot be null"));
        
        // Should throw exception when Map claims is null
        exception = assertThrows(IllegalArgumentException.class, () -> {
            TokenGenerator.generateToken((Map<String, Object>) null, signingKey);
        });
        
        assertTrue(exception.getMessage().contains("Claims map and signing key cannot be null"));
    }

    @Test
    void testGenerateTokenWithNullSigningKey() {
        // Create valid TokenClaims
        TokenClaims claims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud("payment-sapi")
                .exp(new Date(System.currentTimeMillis() + 3600000))
                .iat(new Date())
                .jti("test-token-id")
                .permissions(List.of("process_payment"))
                .build();

        // Should throw exception when signing key is null
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            TokenGenerator.generateToken(claims, null);
        });
        
        assertTrue(exception.getMessage().contains("Claims and signing key cannot be null"));
    }

    @Test
    void testTokenFormat() throws Exception {
        // Create valid TokenClaims
        TokenClaims claims = TokenClaims.builder()
                .sub("test-client")
                .iss("payment-eapi")
                .aud("payment-sapi")
                .exp(new Date(System.currentTimeMillis() + 3600000))
                .iat(new Date())
                .jti("test-token-id")
                .permissions(List.of("process_payment"))
                .build();

        // Generate token
        String token = TokenGenerator.generateToken(claims, signingKey);
        
        // Verify token has exactly three parts
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "Token should have three parts separated by periods");
        
        // Verify header contains correct algorithm and type
        String decodedHeader = decodeBase64(parts[0]);
        Map<String, Object> headerMap = objectMapper.readValue(decodedHeader, Map.class);
        
        assertEquals("HS256", headerMap.get("alg"), "Token should use HS256 algorithm");
        assertEquals("JWT", headerMap.get("typ"), "Token type should be JWT");
        
        // Verify payload is valid JSON with expected claims
        String decodedPayload = decodeBase64(parts[1]);
        Map<String, Object> payloadMap = objectMapper.readValue(decodedPayload, Map.class);
        
        assertNotNull(payloadMap.get("sub"), "Subject claim should exist");
        assertNotNull(payloadMap.get("iss"), "Issuer claim should exist");
        assertNotNull(payloadMap.get("aud"), "Audience claim should exist");
        assertNotNull(payloadMap.get("exp"), "Expiration claim should exist");
        assertNotNull(payloadMap.get("iat"), "Issued-at claim should exist");
        assertNotNull(payloadMap.get("jti"), "JWT ID claim should exist");
        assertNotNull(payloadMap.get("permissions"), "Permissions claim should exist");
        
        // Verify signature is not empty
        assertFalse(parts[2].isEmpty(), "Signature should not be empty");
    }

    /**
     * Helper method to decode Base64URL encoded strings.
     *
     * @param encoded Base64URL encoded string
     * @return Decoded string
     */
    private String decodeBase64(String encoded) {
        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded);
    }
}