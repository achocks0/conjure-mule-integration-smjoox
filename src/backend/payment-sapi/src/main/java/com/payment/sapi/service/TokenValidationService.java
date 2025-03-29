package com.payment.sapi.service;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;

/**
 * Service interface that defines the contract for validating JWT tokens used for authentication
 * in the Payment-Sapi component. This service is responsible for ensuring that tokens are
 * properly signed, not expired, and have the required permissions before allowing access to
 * protected resources.
 * <p>
 * This interface is part of the Payment API Security Enhancement project's token-based
 * authentication mechanism, which replaces header-based Client ID and Client Secret
 * authentication with a more secure approach for internal service communication.
 */
public interface TokenValidationService {

    /**
     * Validates a JWT token string and checks if it has the required permission.
     * This is the main entry point for token validation in the Payment-Sapi component.
     *
     * @param tokenString the JWT token string to validate
     * @param requiredPermission the permission required to access the requested resource
     * @return ValidationResult containing the validation status, error messages, and renewed token if applicable
     */
    ValidationResult validateToken(String tokenString, String requiredPermission);

    /**
     * Parses a JWT token string and creates a Token object.
     * This method extracts claims from the token string and constructs a Token instance.
     *
     * @param tokenString the JWT token string to parse
     * @return the parsed Token object, or null if parsing fails
     */
    Token parseToken(String tokenString);

    /**
     * Validates the signature of a JWT token to ensure it hasn't been tampered with.
     *
     * @param tokenString the JWT token string to validate
     * @return true if the signature is valid, false otherwise
     */
    boolean validateTokenSignature(String tokenString);

    /**
     * Checks if a token has expired based on its expiration time.
     *
     * @param token the Token object to check
     * @return true if the token is not expired, false if it is expired
     */
    boolean validateTokenExpiration(Token token);

    /**
     * Checks if a token has the required permission to access a resource.
     *
     * @param token the Token object to check
     * @param requiredPermission the permission required to access the resource
     * @return true if the token has the required permission, false otherwise
     */
    boolean validateTokenPermission(Token token, String requiredPermission);
}