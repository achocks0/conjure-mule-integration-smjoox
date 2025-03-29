package com.payment.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.model.TokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;

/**
 * Utility class for generating JWT (JSON Web Token) tokens used for authentication
 * between services in the Payment API Security Enhancement project.
 * Implements HS256 (HMAC with SHA-256) signing algorithm as specified in the requirements.
 */
public class TokenGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenGenerator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String ENCODED_HEADER = Base64.getUrlEncoder().withoutPadding().encodeToString(JWT_HEADER.getBytes());

    /**
     * Generates a JWT token with the provided claims and signs it with the signing key.
     *
     * @param claims the token claims containing subject, issuer, audience, etc.
     * @param signingKey the key used to sign the token
     * @return JWT token string in the format header.payload.signature
     * @throws RuntimeException if token generation fails
     */
    public static String generateToken(TokenClaims claims, byte[] signingKey) {
        if (claims == null || signingKey == null) {
            throw new IllegalArgumentException("Claims and signing key cannot be null");
        }

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(claims);
            String encodedPayload = encodeBase64Url(payload);
            
            String headerAndPayload = ENCODED_HEADER + "." + encodedPayload;
            byte[] signature = SecurityUtils.generateHmac(headerAndPayload.getBytes(), signingKey);
            String encodedSignature = encodeBase64Url(signature);
            
            return headerAndPayload + "." + encodedSignature;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error generating token: {}", e.getMessage());
            throw new RuntimeException("Error generating token", e);
        }
    }

    /**
     * Overloaded method that generates a JWT token with the provided claims map and signs it with the signing key.
     *
     * @param claimsMap a map containing token claims (sub, iss, aud, exp, iat, jti, permissions)
     * @param signingKey the key used to sign the token
     * @return JWT token string in the format header.payload.signature
     * @throws RuntimeException if token generation fails
     */
    public static String generateToken(Map<String, Object> claimsMap, byte[] signingKey) {
        if (claimsMap == null || signingKey == null) {
            throw new IllegalArgumentException("Claims map and signing key cannot be null");
        }

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(claimsMap);
            String encodedPayload = encodeBase64Url(payload);
            
            String headerAndPayload = ENCODED_HEADER + "." + encodedPayload;
            byte[] signature = SecurityUtils.generateHmac(headerAndPayload.getBytes(), signingKey);
            String encodedSignature = encodeBase64Url(signature);
            
            return headerAndPayload + "." + encodedSignature;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error generating token: {}", e.getMessage());
            throw new RuntimeException("Error generating token", e);
        }
    }

    /**
     * Encodes a string using Base64URL encoding without padding.
     *
     * @param input the string to encode
     * @return Base64URL encoded string without padding
     */
    public static String encodeBase64Url(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes());
    }

    /**
     * Encodes a byte array using Base64URL encoding without padding.
     *
     * @param input the byte array to encode
     * @return Base64URL encoded string without padding
     */
    public static String encodeBase64Url(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }
}