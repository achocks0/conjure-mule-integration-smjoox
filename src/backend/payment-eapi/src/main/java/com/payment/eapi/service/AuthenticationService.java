package com.payment.eapi.service;

import com.payment.eapi.model.AuthenticationRequest;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.model.Token;
import com.payment.eapi.exception.AuthenticationException;

import java.util.Map;
import java.util.Optional;

/**
 * Interface defining authentication operations for the Payment API Security Enhancement project.
 * This service is responsible for authenticating vendor requests using Client ID and Client Secret,
 * generating JWT tokens for internal service communication, and managing the authentication flow
 * between external vendors and internal services.
 */
public interface AuthenticationService {

    /**
     * Authenticates a vendor using Client ID and Client Secret credentials.
     *
     * @param clientId The Client ID for authentication
     * @param clientSecret The Client Secret for authentication
     * @return JWT token for authenticated vendor
     * @throws AuthenticationException if authentication fails
     */
    Token authenticate(String clientId, String clientSecret) throws AuthenticationException;

    /**
     * Authenticates a vendor using an AuthenticationRequest object.
     *
     * @param request The authentication request containing client credentials
     * @return Response containing JWT token and expiration information
     * @throws AuthenticationException if authentication fails
     */
    AuthenticationResponse authenticate(AuthenticationRequest request) throws AuthenticationException;

    /**
     * Authenticates a vendor using headers containing Client ID and Client Secret.
     * This method supports the backward compatibility requirement by accepting credentials in headers.
     *
     * @param headers Map of request headers containing Client ID and Client Secret
     * @return JWT token for authenticated vendor
     * @throws AuthenticationException if authentication fails or headers are invalid
     */
    Token authenticateWithHeaders(Map<String, String> headers) throws AuthenticationException;

    /**
     * Validates a JWT token's signature and claims.
     *
     * @param tokenString The JWT token string to validate
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String tokenString);

    /**
     * Refreshes an expired token with a new one.
     *
     * @param tokenString The expired token string
     * @return New token if refresh is successful, empty Optional otherwise
     */
    Optional<Token> refreshToken(String tokenString);

    /**
     * Revokes authentication for a specific client.
     *
     * @param clientId The client ID for which to revoke authentication
     * @return true if revocation succeeds, false otherwise
     */
    boolean revokeAuthentication(String clientId);
}