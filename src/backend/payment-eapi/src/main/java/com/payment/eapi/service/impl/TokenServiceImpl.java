package com.payment.eapi.service.impl;

import com.payment.common.model.TokenClaims;
import com.payment.common.monitoring.MetricsService;
import com.payment.common.util.TokenGenerator;
import com.payment.common.util.TokenValidator;
import com.payment.eapi.model.Token;
import com.payment.eapi.service.CacheService;
import com.payment.eapi.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the TokenService interface that provides token management functionality
 * for the Payment API Security Enhancement project. This class handles generating, validating,
 * parsing, renewing, and revoking JWT tokens for authentication between services.
 */
@Service
public class TokenServiceImpl implements TokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenServiceImpl.class);
    private static final List<String> DEFAULT_PERMISSIONS = List.of("process_payment", "view_status");
    private final Set<String> REVOKED_TOKENS = ConcurrentHashMap.newKeySet();

    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;
    private final CacheService cacheService;
    private final MetricsService metricsService;

    @Value("${token.issuer:payment-eapi}")
    private String issuer;

    @Value("${token.audience:payment-sapi}")
    private String audience;

    @Value("${token.expiration-seconds:3600}")
    private long tokenExpirationSeconds;

    @Value("${token.signing-key}")
    private byte[] signingKey;

    /**
     * Constructor for TokenServiceImpl with dependency injection.
     * 
     * @param tokenGenerator utility for generating tokens
     * @param tokenValidator utility for validating tokens
     * @param cacheService service for caching tokens
     * @param metricsService service for recording metrics
     */
    public TokenServiceImpl(TokenGenerator tokenGenerator,
                           TokenValidator tokenValidator,
                           CacheService cacheService,
                           MetricsService metricsService) {
        this.tokenGenerator = tokenGenerator;
        this.tokenValidator = tokenValidator;
        this.cacheService = cacheService;
        this.metricsService = metricsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Token generateToken(String clientId) {
        LOGGER.debug("Generating token for client ID: {}", clientId);
        long startTime = System.currentTimeMillis();

        // Create token claims
        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + (tokenExpirationSeconds * 1000));
        String tokenId = UUID.randomUUID().toString();
        
        TokenClaims claims = TokenClaims.builder()
                .sub(clientId)
                .iss(issuer)
                .aud(audience)
                .iat(now)
                .exp(expirationTime)
                .jti(tokenId)
                .permissions(new ArrayList<>(DEFAULT_PERMISSIONS))
                .build();
        
        // Generate JWT token string
        String tokenString = TokenGenerator.generateToken(claims, signingKey);
        
        // Create Token object
        Token token = Token.builder()
                .tokenString(tokenString)
                .claims(claims)
                .expirationTime(expirationTime)
                .jti(tokenId)
                .clientId(clientId)
                .build();
        
        // Cache the token
        cacheService.cacheToken(token);
        
        // Record metrics
        metricsService.recordTokenGeneration(clientId, System.currentTimeMillis() - startTime);
        
        LOGGER.info("Successfully generated token with ID {} for client ID: {}", tokenId, clientId);
        return token;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Token generateToken(String clientId, List<String> permissions) {
        LOGGER.debug("Generating token with custom permissions for client ID: {}", clientId);
        long startTime = System.currentTimeMillis();

        // Create token claims
        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + (tokenExpirationSeconds * 1000));
        String tokenId = UUID.randomUUID().toString();
        
        TokenClaims claims = TokenClaims.builder()
                .sub(clientId)
                .iss(issuer)
                .aud(audience)
                .iat(now)
                .exp(expirationTime)
                .jti(tokenId)
                .permissions(new ArrayList<>(permissions))
                .build();
        
        // Generate JWT token string
        String tokenString = TokenGenerator.generateToken(claims, signingKey);
        
        // Create Token object
        Token token = Token.builder()
                .tokenString(tokenString)
                .claims(claims)
                .expirationTime(expirationTime)
                .jti(tokenId)
                .clientId(clientId)
                .build();
        
        // Cache the token
        cacheService.cacheToken(token);
        
        // Record metrics
        metricsService.recordTokenGeneration(clientId, System.currentTimeMillis() - startTime);
        
        LOGGER.info("Successfully generated token with ID {} and custom permissions for client ID: {}", tokenId, clientId);
        return token;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validateToken(String tokenString) {
        LOGGER.debug("Validating token");
        long startTime = System.currentTimeMillis();
        boolean isValid = false;
        
        try {
            // Check if token is revoked
            Optional<String> tokenIdOpt = extractTokenId(tokenString);
            if (tokenIdOpt.isPresent() && isTokenRevoked(tokenIdOpt.get())) {
                LOGGER.warn("Token validation failed: token has been revoked");
                return false;
            }
            
            // Validate token signature
            if (!TokenValidator.validateToken(tokenString, signingKey)) {
                LOGGER.warn("Token validation failed: invalid signature");
                return false;
            }
            
            // Parse token claims
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Token validation failed: unable to parse claims");
                return false;
            }
            
            // Validate audience
            if (!TokenValidator.validateTokenAudience(claims, audience)) {
                LOGGER.warn("Token validation failed: invalid audience");
                return false;
            }
            
            // Validate issuer
            if (!TokenValidator.validateTokenIssuer(claims, Collections.singletonList(issuer))) {
                LOGGER.warn("Token validation failed: invalid issuer");
                return false;
            }
            
            // Validate expiration
            if (!TokenValidator.validateTokenExpiration(claims)) {
                LOGGER.warn("Token validation failed: token expired");
                return false;
            }
            
            isValid = true;
            LOGGER.debug("Token validation successful");
            
        } catch (Exception e) {
            LOGGER.error("Error during token validation", e);
        } finally {
            // Record metrics
            String clientId = "unknown";
            try {
                TokenClaims claims = TokenValidator.parseToken(tokenString);
                if (claims != null && claims.getSub() != null) {
                    clientId = claims.getSub();
                }
            } catch (Exception e) {
                // Ignore parsing errors for metrics
            }
            
            metricsService.recordTokenValidation(clientId, isValid, System.currentTimeMillis() - startTime);
        }
        
        return isValid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Token> parseToken(String tokenString) {
        LOGGER.debug("Parsing token string");
        
        // Check if token is revoked
        Optional<String> tokenIdOpt = extractTokenId(tokenString);
        if (tokenIdOpt.isPresent() && isTokenRevoked(tokenIdOpt.get())) {
            LOGGER.warn("Token parsing failed: token has been revoked");
            return Optional.empty();
        }
        
        try {
            // Parse token claims
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims == null) {
                LOGGER.warn("Token parsing failed: unable to parse claims");
                return Optional.empty();
            }
            
            // Extract token information
            String clientId = claims.getSub();
            String tokenId = claims.getJti();
            Date expirationTime = claims.getExp();
            
            // Create Token object
            Token token = Token.builder()
                    .tokenString(tokenString)
                    .claims(claims)
                    .expirationTime(expirationTime)
                    .jti(tokenId)
                    .clientId(clientId)
                    .build();
            
            LOGGER.debug("Successfully parsed token with ID: {}", tokenId);
            return Optional.of(token);
            
        } catch (Exception e) {
            LOGGER.error("Error parsing token", e);
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Token renewToken(Token expiredToken) {
        LOGGER.debug("Renewing expired token with ID: {}", expiredToken.getTokenId());
        
        String clientId = expiredToken.getClientId();
        List<String> permissions = expiredToken.getPermissions();
        
        // Revoke the expired token
        revokeToken(expiredToken.getTokenId());
        
        // Generate a new token with the same permissions
        Token newToken = generateToken(clientId, permissions);
        
        LOGGER.info("Successfully renewed token with ID {} for client ID: {}. New token ID: {}", 
                expiredToken.getTokenId(), clientId, newToken.getTokenId());
        
        return newToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean revokeToken(String tokenId) {
        LOGGER.debug("Revoking token with ID: {}", tokenId);
        
        // Add token ID to revoked tokens set
        REVOKED_TOKENS.add(tokenId);
        
        // Remove from cache
        cacheService.invalidateTokenById(tokenId);
        
        LOGGER.info("Successfully revoked token with ID: {}", tokenId);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTokenRevoked(String tokenId) {
        return REVOKED_TOKENS.contains(tokenId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getSigningKey() {
        // Return a copy to prevent modification of the original key
        return Arrays.copyOf(signingKey, signingKey.length);
    }

    /**
     * Extracts the token ID (jti) from a token string.
     * 
     * @param tokenString the JWT token string
     * @return Optional containing the token ID if extraction was successful
     */
    private Optional<String> extractTokenId(String tokenString) {
        try {
            TokenClaims claims = TokenValidator.parseToken(tokenString);
            if (claims != null && claims.getJti() != null) {
                return Optional.of(claims.getJti());
            }
        } catch (Exception e) {
            LOGGER.error("Error extracting token ID", e);
        }
        return Optional.empty();
    }
}