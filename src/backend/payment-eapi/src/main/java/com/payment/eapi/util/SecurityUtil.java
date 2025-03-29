package com.payment.eapi.util;

import com.payment.common.util.SecurityUtils;
import com.payment.common.model.ErrorResponse;
import org.slf4j.Logger; // org.slf4j:slf4j-api:1.7.32
import org.slf4j.LoggerFactory; // org.slf4j:slf4j-api:1.7.32
import org.springframework.http.HttpStatus; // org.springframework:spring-web:5.3.13

import java.nio.charset.StandardCharsets; // JDK 11
import java.util.Arrays; // JDK 11
import java.util.Base64; // JDK 11
import java.util.HashMap; // JDK 11
import java.util.List; // JDK 11
import java.util.Map; // JDK 11
import java.util.UUID; // JDK 11
import java.util.Date; // JDK 11

/**
 * Utility class that provides security-related functionality specific to the Payment-Eapi component.
 * This class extends the common security utilities with additional methods tailored for the
 * Payment-Eapi's security requirements, including secure logging, header validation, and credential handling.
 */
public class SecurityUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUtil.class);
    private static final String CLIENT_ID_HEADER = "X-Client-ID";
    private static final String CLIENT_SECRET_HEADER = "X-Client-Secret";
    private static final List<String> SENSITIVE_HEADERS = Arrays.asList(
            CLIENT_ID_HEADER, CLIENT_SECRET_HEADER, "Authorization");

    /**
     * Extracts and validates Client ID and Client Secret from request headers.
     *
     * @param headers Map containing request headers
     * @return Map containing validated Client ID and Client Secret
     * @throws IllegalArgumentException if credentials are missing or invalid
     */
    public static Map<String, String> extractCredentialsFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            LOGGER.error("Headers map is null");
            throw new IllegalArgumentException("Headers cannot be null");
        }

        String clientId = headers.get(CLIENT_ID_HEADER);
        String clientSecret = headers.get(CLIENT_SECRET_HEADER);

        if (clientId == null || clientId.isEmpty()) {
            LOGGER.error("Client ID is missing in headers");
            throw new IllegalArgumentException("Client ID is required");
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            LOGGER.error("Client Secret is missing in headers");
            throw new IllegalArgumentException("Client Secret is required");
        }

        // Sanitize values to prevent injection attacks
        clientId = SecurityUtils.sanitizeHeader(clientId);
        clientSecret = SecurityUtils.sanitizeHeader(clientSecret);

        Map<String, String> credentials = new HashMap<>();
        credentials.put(CLIENT_ID_HEADER, clientId);
        credentials.put(CLIENT_SECRET_HEADER, clientSecret);

        return credentials;
    }

    /**
     * Validates that Client ID and Client Secret are present and properly formatted.
     *
     * @param clientId Client ID to validate
     * @param clientSecret Client Secret to validate
     * @return true if credentials are valid, false otherwise
     */
    public static boolean validateClientCredentials(String clientId, String clientSecret) {
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.debug("Client ID is null or empty");
            return false;
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            LOGGER.debug("Client Secret is null or empty");
            return false;
        }

        // Validate clientId format (alphanumeric, minimum length)
        if (!clientId.matches("^[a-zA-Z0-9_-]{8,}$")) {
            LOGGER.debug("Client ID format is invalid");
            return false;
        }

        // Validate clientSecret format (complexity requirements)
        if (clientSecret.length() < 16) {
            LOGGER.debug("Client Secret does not meet minimum length requirement");
            return false;
        }

        return true;
    }

    /**
     * Logs messages with sensitive data masked for security.
     *
     * @param logger Logger instance to use
     * @param message Message to log
     * @param args Arguments for the message, potentially containing sensitive data
     */
    public static void logSecurely(Logger logger, String message, Object... args) {
        if (logger.isInfoEnabled()) {
            try {
                Object[] maskedArgs = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof String && isSensitiveData((String) args[i])) {
                        maskedArgs[i] = SecurityUtils.maskSensitiveData((String) args[i]);
                    } else if (args[i] instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> map = (Map<String, String>) args[i];
                        maskedArgs[i] = maskHeaders(map);
                    } else {
                        maskedArgs[i] = args[i];
                    }
                }
                logger.info(message, maskedArgs);
            } catch (Exception e) {
                logger.warn("Error during secure logging: {}", e.getMessage());
                logger.info(message, "[MASKED]");
            }
        }
    }

    /**
     * Checks if a string might contain sensitive data based on pattern matching.
     * 
     * @param data String to check
     * @return true if the data appears to be sensitive, false otherwise
     */
    private static boolean isSensitiveData(String data) {
        if (data == null) {
            return false;
        }
        
        // Check for patterns that might indicate sensitive data
        return data.matches(".*(?:secret|password|key|token|credential).*") ||
                data.matches(".*[0-9a-fA-F]{32,}.*"); // Looks like a hash or token
    }

    /**
     * Creates a standardized error response for security-related errors.
     *
     * @param errorCode Error code identifying the issue
     * @param message Human-readable error message
     * @param status HTTP status code for the error
     * @return Standardized error response
     */
    public static ErrorResponse createSecurityErrorResponse(String errorCode, String message, HttpStatus status) {
        String requestId = UUID.randomUUID().toString();
        
        LOGGER.error("Security error: code={}, message={}, status={}, requestId={}",
                errorCode, message, status, requestId);
        
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .requestId(requestId)
                .timestamp(new Date())
                .build();
    }

    /**
     * Sanitizes all headers in a header map to prevent injection attacks.
     *
     * @param headers Map of headers to sanitize
     * @return Map with sanitized header values
     */
    public static Map<String, String> sanitizeHeaderMap(Map<String, String> headers) {
        if (headers == null) {
            return new HashMap<>();
        }
        
        Map<String, String> sanitizedHeaders = new HashMap<>();
        
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String sanitizedValue = SecurityUtils.sanitizeHeader(entry.getValue());
            sanitizedHeaders.put(entry.getKey(), sanitizedValue);
        }
        
        return sanitizedHeaders;
    }

    /**
     * Checks if a header name is in the list of sensitive headers.
     *
     * @param headerName Name of the header to check
     * @return true if the header is sensitive, false otherwise
     */
    public static boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        
        String lowercaseName = headerName.toLowerCase();
        
        for (String sensitiveHeader : SENSITIVE_HEADERS) {
            if (sensitiveHeader.toLowerCase().equals(lowercaseName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Creates a copy of the headers map with sensitive values masked.
     *
     * @param headers Original headers map
     * @return Map with masked sensitive header values
     */
    public static Map<String, String> maskHeaders(Map<String, String> headers) {
        if (headers == null) {
            return new HashMap<>();
        }
        
        Map<String, String> maskedHeaders = new HashMap<>();
        
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue();
            
            if (isSensitiveHeader(headerName)) {
                maskedHeaders.put(headerName, SecurityUtils.maskSensitiveData(headerValue));
            } else {
                maskedHeaders.put(headerName, headerValue);
            }
        }
        
        return maskedHeaders;
    }

    /**
     * Generates a secure signing key for JWT tokens.
     *
     * @param seed Seed value to use for key generation
     * @return Signing key as byte array
     * @throws RuntimeException if key generation fails
     */
    public static byte[] generateSigningKey(String seed) {
        if (seed == null || seed.isEmpty()) {
            throw new IllegalArgumentException("Seed cannot be null or empty");
        }
        
        try {
            String hash = SecurityUtils.hashCredential(seed);
            return Base64.getDecoder().decode(hash);
        } catch (Exception e) {
            LOGGER.error("Error generating signing key: {}", e.getMessage());
            throw new RuntimeException("Failed to generate signing key", e);
        }
    }
}