package com.payment.sapi.filter;

import com.payment.sapi.service.TokenValidationService;
import com.payment.sapi.service.TokenRenewalService;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.common.model.ErrorResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Spring Security filter that authenticates requests using JWT tokens,
 * validates them, and sets up the security context for authorized requests.
 * This filter is part of the Payment API Security Enhancement project,
 * implementing token-based authentication for internal service communication.
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TokenAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final TokenValidationService tokenValidationService;
    private final TokenRenewalService tokenRenewalService;
    private final String audience;
    private final List<String> issuers;

    /**
     * Initializes the filter with required services and configuration.
     *
     * @param tokenValidationService service to validate JWT tokens
     * @param tokenRenewalService service to renew expired tokens
     * @param audience the expected audience value for tokens
     * @param issuers list of trusted token issuers
     */
    public TokenAuthenticationFilter(TokenValidationService tokenValidationService,
                                    TokenRenewalService tokenRenewalService,
                                    String audience,
                                    List<String> issuers) {
        if (tokenValidationService == null) {
            throw new IllegalArgumentException("TokenValidationService cannot be null");
        }
        if (tokenRenewalService == null) {
            throw new IllegalArgumentException("TokenRenewalService cannot be null");
        }
        if (audience == null || audience.isEmpty()) {
            throw new IllegalArgumentException("Audience cannot be null or empty");
        }
        if (issuers == null || issuers.isEmpty()) {
            throw new IllegalArgumentException("Issuers cannot be null or empty");
        }
        
        this.tokenValidationService = tokenValidationService;
        this.tokenRenewalService = tokenRenewalService;
        this.audience = audience;
        this.issuers = issuers;
        
        logger.info("Initialized TokenAuthenticationFilter with audience '{}' and issuers: {}", 
                audience, String.join(", ", issuers));
    }

    /**
     * Core filter method that processes each request to validate JWT tokens
     * and set up security context.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param filterChain the filter chain for request processing
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response,
                                  FilterChain filterChain) 
                                  throws ServletException, IOException {
        
        // Extract the JWT token from the Authorization header
        String tokenString = extractToken(request);
        
        // If no token found, continue the filter chain without authentication
        if (tokenString == null) {
            logger.debug("No token found in request to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }
        
        // Determine the required permission based on the request path
        String requiredPermission = determineRequiredPermission(request);
        logger.debug("Required permission for {} is {}", request.getRequestURI(), requiredPermission);
        
        // Validate the token
        ValidationResult validationResult = tokenValidationService.validateToken(tokenString, requiredPermission);
        
        if (validationResult.isValid()) {
            // If token is valid, set up authentication
            logger.debug("Token validation successful for request to {}", request.getRequestURI());
            
            // If token was renewed, update the Authorization header with new token
            if (validationResult.isRenewed()) {
                String renewedToken = validationResult.getRenewedTokenString();
                logger.debug("Token renewed for request to {}", request.getRequestURI());
                response.setHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + renewedToken);
                tokenString = renewedToken;
            }
            
            // Parse the token to create Authentication object
            Token token = tokenValidationService.parseToken(tokenString);
            if (token != null) {
                // Create Authentication object and set in SecurityContextHolder
                Authentication authentication = createAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Authentication set for client ID: {} in request to {}", 
                        token.getSubject(), request.getRequestURI());
            }
            
            // Continue the filter chain
            filterChain.doFilter(request, response);
        } else {
            // If token validation failed, check if it's expired and can be renewed
            if (validationResult.isExpired()) {
                // Parse the expired token
                Token expiredToken = tokenValidationService.parseToken(tokenString);
                
                if (expiredToken != null && tokenRenewalService.shouldRenew(expiredToken)) {
                    logger.debug("Attempting to renew expired token for request to {}", request.getRequestURI());
                    
                    // Try to renew the token
                    ValidationResult renewalResult = tokenRenewalService.renewToken(expiredToken);
                    
                    if (renewalResult.isRenewed()) {
                        // Token renewal was successful
                        String renewedToken = renewalResult.getRenewedTokenString();
                        logger.debug("Token renewed successfully for request to {}", request.getRequestURI());
                        
                        // Update the Authorization header with new token
                        response.setHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + renewedToken);
                        
                        // Parse the new token to create Authentication object
                        Token newToken = tokenValidationService.parseToken(renewedToken);
                        if (newToken != null) {
                            // Create Authentication object and set in SecurityContextHolder
                            Authentication authentication = createAuthentication(newToken);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            logger.debug("Authentication set after token renewal for client ID: {} in request to {}", 
                                    newToken.getSubject(), request.getRequestURI());
                        }
                        
                        // Continue the filter chain
                        filterChain.doFilter(request, response);
                        return;
                    }
                }
            }
            
            // Handle authentication failure
            logger.warn("Token validation failed for request to {}: {}", 
                    request.getRequestURI(), validationResult.getErrorMessage());
            handleAuthenticationFailure(response, validationResult);
        }
    }

    /**
     * Extracts the JWT token from the Authorization header.
     *
     * @param request the HTTP request
     * @return the extracted token or null if not found
     */
    protected String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.isEmpty()) {
            return null;
        }
        
        if (header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        
        return null;
    }

    /**
     * Determines the required permission based on the request path.
     *
     * @param request the HTTP request
     * @return the required permission for the requested resource
     */
    protected String determineRequiredPermission(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Check if the path matches payment endpoints
        if (path.matches(".*/payments(/.*)?") && !path.contains("/status")) {
            return "process_payment";
        }
        
        // Check if the path matches status endpoints
        if (path.matches(".*/payments/status(/.*)?") || path.contains("/status")) {
            return "view_status";
        }
        
        // Return null for paths that don't require specific permissions
        return null;
    }

    /**
     * Creates an Authentication object based on the validated token.
     *
     * @param token the validated Token object
     * @return the Authentication object for the security context
     */
    protected Authentication createAuthentication(Token token) {
        String clientId = token.getSubject();
        List<String> permissions = token.getPermissions();
        
        // Create authorities from permissions
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (permissions != null) {
            for (String permission : permissions) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }
        
        // Create and return Authentication object with client ID as principal,
        // token as credentials, and permissions as authorities
        return new UsernamePasswordAuthenticationToken(clientId, token, authorities);
    }

    /**
     * Handles authentication failures by returning appropriate error responses.
     *
     * @param response the HTTP response
     * @param validationResult the validation result containing error details
     * @throws IOException if an I/O error occurs during response writing
     */
    protected void handleAuthenticationFailure(HttpServletResponse response, 
                                              ValidationResult validationResult) throws IOException {
        response.setContentType("application/json");
        
        // Determine appropriate HTTP status code
        if (validationResult.isForbidden()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            logger.warn("Access forbidden: {}", validationResult.getErrorMessage());
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            logger.warn("Unauthorized access: {}", validationResult.getErrorMessage());
        }
        
        // Create error response
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(validationResult.isForbidden() ? "FORBIDDEN" : "UNAUTHORIZED")
                .message(validationResult.getErrorMessage())
                .requestId(UUID.randomUUID().toString())
                .timestamp(new Date())
                .build();
        
        // Write error response to output stream
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }
}