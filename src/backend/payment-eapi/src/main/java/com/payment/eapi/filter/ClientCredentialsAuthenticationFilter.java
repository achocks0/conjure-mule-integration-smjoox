package com.payment.eapi.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import com.payment.eapi.service.AuthenticationService;
import com.payment.eapi.service.ConjurService;
import com.payment.eapi.service.TokenService;
import com.payment.eapi.service.CacheService;
import com.payment.eapi.model.AuthenticationResponse;
import com.payment.eapi.exception.AuthenticationException;
import com.payment.common.monitoring.MetricsService;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Custom Spring Security filter that authenticates API requests using Client ID and Client Secret from headers.
 * This filter implements the backward compatibility layer for the Payment API Security Enhancement project,
 * allowing existing vendors to continue using header-based authentication while internally translating to
 * token-based authentication.
 */
public class ClientCredentialsAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ClientCredentialsAuthenticationFilter.class);
    private static final String CLIENT_ID_HEADER = "X-Client-ID";
    private static final String CLIENT_SECRET_HEADER = "X-Client-Secret";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    private final AuthenticationService authenticationService;
    private final ConjurService conjurService;
    private final TokenService tokenService;
    private final CacheService cacheService;
    private final MetricsService metricsService;

    /**
     * Initializes the filter with required services
     * 
     * @param authenticationService Service for authenticating requests using Client ID and Client Secret
     * @param conjurService Service for interacting with Conjur vault to retrieve and validate credentials
     * @param tokenService Service for generating and validating JWT tokens
     * @param cacheService Service for caching tokens to improve performance
     * @param metricsService Service for recording authentication metrics
     */
    public ClientCredentialsAuthenticationFilter(
            AuthenticationService authenticationService,
            ConjurService conjurService,
            TokenService tokenService,
            CacheService cacheService,
            MetricsService metricsService) {
        super();
        this.authenticationService = authenticationService;
        this.conjurService = conjurService;
        this.tokenService = tokenService;
        this.cacheService = cacheService;
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Skip authentication for public endpoints
        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // Check if request already has a valid token
            if (hasAuthorizationToken(request)) {
                String token = extractToken(request);
                if (token != null && tokenService.validateToken(token)) {
                    // Token is valid, continue with filter chain
                    filterChain.doFilter(request, response);
                    return;
                }
                // Token is invalid, fall through to Client ID/Secret authentication
            }
            
            // Check for Client ID and Client Secret headers
            if (hasClientCredentialHeaders(request)) {
                Map<String, String> headers = extractHeaders(request);
                String clientId = headers.get(CLIENT_ID_HEADER);
                
                try {
                    // Check for cached token first
                    Optional<Token> cachedToken = cacheService.retrieveToken(clientId);
                    if (cachedToken.isPresent() && !cachedToken.get().isExpired()) {
                        // Valid cached token exists, continue with filter chain
                        filterChain.doFilter(request, response);
                        return;
                    }
                    
                    // No valid cached token, authenticate with headers
                    logger.debug("Authenticating request with Client ID/Secret headers");
                    Token token = authenticationService.authenticateWithHeaders(headers);
                    
                    // Authentication successful, record metrics
                    metricsService.recordAuthenticationAttempt(clientId, true);
                    
                    // Continue with filter chain
                    filterChain.doFilter(request, response);
                    return;
                } catch (AuthenticationException e) {
                    // Authentication failed, record metrics
                    metricsService.recordAuthenticationAttempt(clientId, false);
                    
                    logger.warn("Authentication failed for client ID {}: {}", clientId, e.getMessage());
                    handleAuthenticationFailure(response, e.getMessage());
                    return;
                }
            }
            
            // No authentication credentials provided
            logger.warn("No authentication credentials provided");
            handleAuthenticationFailure(response, "Authentication credentials not provided");
            
        } catch (Exception e) {
            logger.error("Error during authentication processing", e);
            handleAuthenticationFailure(response, "Authentication error: " + e.getMessage());
        }
    }
    
    /**
     * Extracts headers from the request into a map
     * 
     * @param request The HTTP request
     * @return Map of header names to values
     */
    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        
        return headers;
    }
    
    /**
     * Checks if the request contains Client ID and Client Secret headers
     * 
     * @param request The HTTP request
     * @return True if both headers are present, false otherwise
     */
    private boolean hasClientCredentialHeaders(HttpServletRequest request) {
        return request.getHeader(CLIENT_ID_HEADER) != null &&
               request.getHeader(CLIENT_SECRET_HEADER) != null;
    }
    
    /**
     * Checks if the request contains an Authorization header with a Bearer token
     * 
     * @param request The HTTP request
     * @return True if Authorization header with Bearer token is present, false otherwise
     */
    private boolean hasAuthorizationToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        return authHeader != null && authHeader.startsWith(BEARER_PREFIX);
    }
    
    /**
     * Extracts the token from the Authorization header
     * 
     * @param request The HTTP request
     * @return The extracted token, or null if not present
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
    
    /**
     * Checks if the requested endpoint is public (health check, metrics)
     * 
     * @param request The HTTP request
     * @return True if the endpoint is public, false otherwise
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/health") || path.startsWith("/api/metrics");
    }
    
    /**
     * Handles authentication failures by setting appropriate response status and body
     * 
     * @param response The HTTP response
     * @param errorMessage The error message to include in the response
     * @throws IOException If an I/O error occurs
     */
    private void handleAuthenticationFailure(HttpServletResponse response, String errorMessage) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        
        String errorResponse = String.format(
            "{\"errorCode\":\"AUTH_ERROR\",\"message\":\"%s\",\"requestId\":\"%s\",\"timestamp\":\"%s\"}",
            errorMessage.replace("\"", "\\\""),
            request.getHeader("X-Request-ID") != null ? request.getHeader("X-Request-ID") : "unknown",
            Instant.now().toString()
        );
        
        response.getWriter().write(errorResponse);
        logger.warn("Authentication failure: {}", errorMessage);
    }
}