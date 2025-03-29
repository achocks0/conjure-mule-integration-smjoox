package com.payment.common.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class that provides core security functionality for the Payment API Security Enhancement project.
 * Contains methods for secure cryptographic operations, credential handling, and security-related utilities.
 */
public class SecurityUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityUtils.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SALT_LENGTH = 16;

    /**
     * Hashes a credential (like a client secret) with a random salt using SHA-256.
     *
     * @param credential The credential to hash
     * @return Base64 encoded string containing the salt and hash
     * @throws RuntimeException if a hashing error occurs
     */
    public static String hashCredential(String credential) {
        if (credential == null || credential.isEmpty()) {
            throw new IllegalArgumentException("Credential cannot be null or empty");
        }

        try {
            // Generate a random salt
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.nextBytes(salt);

            // Create message digest instance
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            
            // Update with salt and credential
            md.update(salt);
            md.update(credential.getBytes());
            
            // Compute the hash
            byte[] hash = md.digest();
            
            // Combine salt and hash
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            // Encode with Base64
            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Error hashing credential: {}", e.getMessage());
            throw new RuntimeException("Error hashing credential", e);
        }
    }

    /**
     * Validates a credential against a stored hash.
     *
     * @param credential The credential to validate
     * @param storedHash The stored hash to validate against
     * @return true if the credential matches the stored hash, false otherwise
     */
    public static boolean validateCredential(String credential, String storedHash) {
        if (credential == null || credential.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            LOGGER.warn("Credential or stored hash is null or empty");
            return false;
        }

        try {
            // Decode the stored hash
            byte[] combined = Base64.getDecoder().decode(storedHash);
            
            // Extract salt and hash
            byte[] salt = new byte[SALT_LENGTH];
            byte[] storedHashBytes = new byte[combined.length - SALT_LENGTH];
            
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, storedHashBytes, 0, storedHashBytes.length);
            
            // Create message digest instance
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            
            // Update with salt and credential
            md.update(salt);
            md.update(credential.getBytes());
            
            // Compute the hash
            byte[] computedHash = md.digest();
            
            // Compare in constant time
            return constantTimeEquals(computedHash, storedHashBytes);
        } catch (Exception e) {
            LOGGER.error("Error validating credential: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates an HMAC signature for the given data using the provided key.
     *
     * @param data The data to sign
     * @param key The key to use for signing
     * @return HMAC signature
     * @throws RuntimeException if a signing error occurs
     */
    public static byte[] generateHmac(byte[] data, byte[] key) {
        if (data == null || key == null) {
            throw new IllegalArgumentException("Data and key cannot be null");
        }

        try {
            // Create Mac instance
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            
            // Initialize with key
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            // Generate HMAC
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOGGER.error("Error generating HMAC: {}", e.getMessage());
            throw new RuntimeException("Error generating HMAC", e);
        }
    }

    /**
     * Compares two byte arrays in constant time to prevent timing attacks.
     *
     * @param a First byte array
     * @param b Second byte array
     * @return true if the arrays are equal, false otherwise
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }

        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        
        return result == 0;
    }

    /**
     * Generates a cryptographically secure random string of the specified length.
     *
     * @param length The length of the random string
     * @return Random string
     * @throws IllegalArgumentException if length is not positive
     */
    public static String generateSecureRandomString(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }

        // Define character set for random string
        String charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(charset.length());
            sb.append(charset.charAt(randomIndex));
        }
        
        return sb.toString();
    }

    /**
     * Masks sensitive data for logging purposes.
     *
     * @param data The sensitive data to mask
     * @return Masked string
     */
    public static String maskSensitiveData(String data) {
        if (data == null) {
            return "[null]";
        }
        
        if (data.isEmpty()) {
            return "[empty]";
        }
        
        if (data.length() < 8) {
            return "****";
        }
        
        return data.substring(0, 4) + "****" + data.substring(data.length() - 4);
    }

    /**
     * Sanitizes header values to prevent injection attacks.
     *
     * @param headerValue The header value to sanitize
     * @return Sanitized header value
     */
    public static String sanitizeHeader(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        
        // Remove control characters, newlines, and carriage returns
        String sanitized = headerValue.replaceAll("[\\p{Cntrl}\\n\\r]", "");
        
        // Remove any HTML/XML tags
        sanitized = sanitized.replaceAll("<[^>]*>", "");
        
        return sanitized;
    }
}