package com.payment.eapi.service.impl;

import com.payment.eapi.service.AuthenticationService;
import com.payment.eapi.service.ConjurService;
import com.payment.eapi.service.TokenService;
import com.payment.eapi.service.CacheService;
import com.payment.eapi.model.AuthenticationRequest;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.model.Token;
import com.payment.eapi.model.Credential;
import com.payment.eapi.exception.AuthenticationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the AuthenticationService interface that provides authentication functionality
 * for the Payment API Security Enhancement project. This service handles vendor authentication using
 * Client ID and Client Secret, generates JWT tokens for internal service communication, and manages
 * the authentication flow between external vendors and internal services.
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    
    private final ConjurService conjurService;
    private final TokenService tokenService;
    private final CacheService cacheService;
    
    /**
     * Constructor for AuthenticationServiceImpl with dependency injection
     *
     * @param conjurService service for retrieving and validating credentials from Conjur vault
     * @param tokenService service for generating and managing JWT tokens
     * @param cacheService service for caching tokens and credentials
     */
    @Autowired
    public AuthenticationServiceImpl(ConjurService conjurService, 
                                   TokenService tokenService,
                                   CacheService cacheService) {
        this.conjurService = conjurService;
        this.tokenService = tokenService;
        this.cacheService = cacheService;
    }
    
    /**
     * Authenticates a vendor using Client ID and Client Secret credentials.
     *
     * @param clientId The Client ID for authentication
     * @param clientSecret The Client Secret for authentication
     * @return JWT token for authenticated vendor
     * @throws AuthenticationException if authentication fails
     */
    @Override
    public Token authenticate(String clientId, String clientSecret) throws AuthenticationException {
        logger.debug("Authenticating client: {}", clientId);
        
        // Validate input parameters
        if (clientId == null || clientId.trim().isEmpty() || 
            clientSecret == null || clientSecret.trim().isEmpty()) {
            logger.error("Authentication failed: Invalid credentials format");
            throw new AuthenticationException("Client ID and Client Secret cannot be null or empty");
        }
        
        try {
            // Check if there is a cached token for this client
            Optional<Token> cachedToken = cacheService.retrieveToken(clientId);
            if (cachedToken.isPresent() && !cachedToken.get().isExpired()) {
                logger.debug("Using cached token for client: {}", clientId);
                return cachedToken.get();
            }
            
            // Validate credentials against Conjur vault
            if (validateCredentials(clientId, clientSecret)) {
                // Generate a new token
                Token token = tokenService.generateToken(clientId);
                
                // Cache the token
                cacheService.cacheToken(token);
                
                logger.info("Authentication successful for client: {}", clientId);
                return token;
            } else {
                logger.warn("Authentication failed: Invalid credentials for client: {}", clientId);
                throw new AuthenticationException("Invalid credentials");
            }
        } catch (AuthenticationException ae) {
            // Re-throw authentication exceptions
            throw ae;
        } catch (Exception e) {
            logger.error("Authentication failed for client: {}", clientId, e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticates a vendor using an AuthenticationRequest object.
     *
     * @param request The authentication request containing client credentials
     * @return Response containing JWT token and expiration information
     * @throws AuthenticationException if authentication fails
     */
    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) throws AuthenticationException {
        if (request == null) {
            logger.error("Authentication failed: Request object is null");
            throw new AuthenticationException("Authentication request cannot be null");
        }
        
        try {
            // Extract credentials from the request
            String clientId = request.getClientId();
            String clientSecret = request.getClientSecret();
            
            // Authenticate using the other authenticate method
            Token token = authenticate(clientId, clientSecret);
            
            // Convert Token to AuthenticationResponse
            return AuthenticationResponse.fromToken(token);
        } catch (AuthenticationException ae) {
            // Re-throw authentication exceptions
            throw ae;
        } catch (Exception e) {
            logger.error("Authentication failed: Unexpected error", e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Authenticates a vendor using headers containing Client ID and Client Secret.
     * This method supports the backward compatibility requirement by accepting credentials in headers.
     *
     * @param headers Map of request headers containing Client ID and Client Secret
     * @return JWT token for authenticated vendor
     * @throws AuthenticationException if authentication fails or headers are invalid
     */
    @Override
    public Token authenticateWithHeaders(Map<String, String> headers) throws AuthenticationException {
        if (headers == null) {
            logger.error("Authentication failed: Headers map is null");
            throw new AuthenticationException("Headers cannot be null");
        }
        
        try {
            // Extract credentials from headers
            String clientId = headers.get("X-Client-ID");
            String clientSecret = headers.get("X-Client-Secret");
            
            // Validate that both clientId and clientSecret are present
            if (clientId == null || clientId.trim().isEmpty()) {
                logger.error("Authentication failed: Missing Client ID in headers");
                throw new AuthenticationException("Client ID is missing in headers");
            }
            
            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                logger.error("Authentication failed: Missing Client Secret in headers");
                throw new AuthenticationException("Client Secret is missing in headers");
            }
            
            // Authenticate using extracted credentials
            return authenticate(clientId, clientSecret);
        } catch (AuthenticationException ae) {
            // Re-throw authentication exceptions
            throw ae;
        } catch (Exception e) {
            logger.error("Authentication with headers failed: Unexpected error", e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validates a JWT token's signature and claims.
     *
     * @param tokenString The JWT token string to validate
     * @return true if the token is valid, false otherwise
     */
    @Override
    public boolean validateToken(String tokenString) {
        if (tokenString == null || tokenString.trim().isEmpty()) {
            logger.error("Token validation failed: Token string is null or empty");
            return false;
        }
        
        try {
            boolean isValid = tokenService.validateToken(tokenString);
            logger.debug("Token validation result: {}", isValid);
            return isValid;
        } catch (Exception e) {
            logger.error("Token validation failed: Unexpected error", e);
            return false;
        }
    }
    
    /**
     * Refreshes an expired token with a new one.
     *
     * @param tokenString The expired token string
     * @return New token if refresh is successful, empty Optional otherwise
     */
    @Override
    public Optional<Token> refreshToken(String tokenString) {
        if (tokenString == null || tokenString.trim().isEmpty()) {
            logger.error("Token refresh failed: Token string is null or empty");
            return Optional.empty();
        }
        
        try {
            // Parse the token to extract information
            Optional<Token> optionalToken = tokenService.parseToken(tokenString);
            
            if (optionalToken.isEmpty()) {
                logger.error("Token refresh failed: Unable to parse token");
                return Optional.empty();
            }
            
            Token token = optionalToken.get();
            
            // Check if the token is expired
            if (!token.isExpired()) {
                logger.debug("Token is not expired, no need to refresh");
                return optionalToken;
            }
            
            // Generate a new token based on the expired one
            Token newToken = tokenService.renewToken(token);
            
            // Cache the new token
            cacheService.cacheToken(newToken);
            
            logger.info("Token refreshed successfully for client: {}", token.getClientId());
            return Optional.of(newToken);
        } catch (Exception e) {
            logger.error("Token refresh failed: Unexpected error", e);
            return Optional.empty();
        }
    }
    
    /**
     * Revokes authentication for a specific client.
     *
     * @param clientId The client ID for which to revoke authentication
     * @return true if revocation succeeds, false otherwise
     */
    @Override
    public boolean revokeAuthentication(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            logger.error("Authentication revocation failed: Client ID is null or empty");
            return false;
        }
        
        try {
            // First check if there is a cached token for this client
            Optional<Token> cachedToken = cacheService.retrieveToken(clientId);
            
            // If a token exists, revoke it
            if (cachedToken.isPresent()) {
                tokenService.revokeToken(cachedToken.get().getTokenId());
            }
            
            // Invalidate all cached data for this client
            cacheService.invalidateAllForClient(clientId);
            
            logger.info("Authentication revoked successfully for client: {}", clientId);
            return true;
        } catch (Exception e) {
            logger.error("Authentication revocation failed for client: {}", clientId, e);
            return false;
        }
    }
    
    /**
     * Private helper method to validate client credentials.
     *
     * @param clientId The Client ID to validate
     * @param clientSecret The Client Secret to validate
     * @return true if credentials are valid, false otherwise
     */
    private boolean validateCredentials(String clientId, String clientSecret) {
        try {
            return conjurService.validateCredentialsWithFallback(clientId, clientSecret);
        } catch (Exception e) {
            logger.error("Error validating credentials for client: {}", clientId, e);
            return false;
        }
    }
}