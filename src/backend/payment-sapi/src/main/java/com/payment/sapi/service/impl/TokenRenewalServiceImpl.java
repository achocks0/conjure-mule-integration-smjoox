package com.payment.sapi.service.impl;

import com.payment.sapi.service.TokenRenewalService;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.util.TokenUtil;
import com.payment.sapi.service.CacheService;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.Calendar;

/**
 * Implementation of the TokenRenewalService interface that handles the renewal of JWT tokens
 * used for authentication in the Payment-Sapi component. This service communicates with the
 * Payment-Eapi service to request new tokens when existing tokens are expired or about to expire,
 * ensuring continuous authentication without disrupting the user experience.
 */
@Service
public class TokenRenewalServiceImpl implements TokenRenewalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenRenewalServiceImpl.class);
    
    private final RestTemplate restTemplate;
    private final CacheService cacheService;
    
    @Value("${payment.eapi.url}")
    private String eapiUrl;
    
    @Value("${payment.eapi.token.renewal.endpoint:/api/v1/tokens/renew}")
    private String tokenRenewalEndpoint;
    
    @Value("${payment.token.renewal.threshold.seconds:300}")
    private int renewalThresholdSeconds;
    
    /**
     * Constructs a new TokenRenewalServiceImpl with the required dependencies
     *
     * @param restTemplate the RestTemplate used for HTTP communication with Payment-Eapi
     * @param cacheService the CacheService used for storing renewed tokens
     */
    public TokenRenewalServiceImpl(RestTemplate restTemplate, CacheService cacheService) {
        this.restTemplate = restTemplate;
        this.cacheService = cacheService;
    }
    
    /**
     * Renews an expired or about-to-expire JWT token by requesting a new token from the Payment-Eapi service.
     *
     * @param token The token to be renewed
     * @return ValidationResult containing either the renewed token or an error message if renewal failed
     */
    @Override
    public ValidationResult renewToken(Token token) {
        LOGGER.debug("Attempting to renew token: {}", token != null ? token.getTokenId() : "null");
        
        if (token == null) {
            LOGGER.warn("Cannot renew null token");
            return ValidationResult.invalid("Cannot renew null token");
        }
        
        String tokenString = token.getTokenString();
        if (tokenString == null || tokenString.isEmpty()) {
            LOGGER.warn("Token string is null or empty for token ID: {}", token.getTokenId());
            return ValidationResult.invalid("Token string is null or empty");
        }
        
        Token newToken = requestTokenRenewal(tokenString);
        
        if (newToken == null) {
            LOGGER.warn("Token renewal failed for token ID: {}", token.getTokenId());
            return ValidationResult.invalid("Token renewal failed");
        }
        
        // Store the new token in the cache
        cacheService.storeToken(newToken);
        
        LOGGER.info("Successfully renewed token. Old ID: {}, New ID: {}", token.getTokenId(), newToken.getTokenId());
        return ValidationResult.renewed(newToken.getTokenString());
    }
    
    /**
     * Sends a token renewal request to the Payment-Eapi service.
     *
     * @param tokenString The current token string to be renewed
     * @return The renewed Token object or null if renewal fails
     */
    @Override
    public Token requestTokenRenewal(String tokenString) {
        LOGGER.debug("Sending token renewal request to Payment-EAPI");
        
        if (tokenString == null || tokenString.isEmpty()) {
            LOGGER.warn("Cannot renew null or empty token string");
            return null;
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", TokenUtil.formatBearerToken(tokenString));
        
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("token", tokenString);
        
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
        
        try {
            // Ensure the URL is properly formed by handling trailing slashes
            String url = eapiUrl;
            if (!url.endsWith("/") && !tokenRenewalEndpoint.startsWith("/")) {
                url += "/";
            } else if (url.endsWith("/") && tokenRenewalEndpoint.startsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            url += tokenRenewalEndpoint;
            
            LOGGER.debug("Sending token renewal request to URL: {}", url);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();
                
                if (responseBody.containsKey("token")) {
                    String newTokenString = (String) responseBody.get("token");
                    if (newTokenString != null && !newTokenString.isEmpty()) {
                        LOGGER.debug("Successfully received renewed token from Payment-EAPI");
                        return TokenUtil.createToken(newTokenString);
                    } else {
                        LOGGER.warn("Received null or empty token in response");
                    }
                } else {
                    LOGGER.warn("Response does not contain 'token' field");
                }
            } else {
                LOGGER.warn("Failed to renew token. Response status: {}", response.getStatusCode());
            }
            
            return null;
        } catch (Exception e) {
            LOGGER.error("Error during token renewal request: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Determines if a token should be renewed based on its expiration time.
     * Tokens that are expired or about to expire (within the configured threshold) should be renewed.
     *
     * @param token The token to check for renewal
     * @return true if the token should be renewed, false otherwise
     */
    @Override
    public boolean shouldRenew(Token token) {
        if (token == null) {
            LOGGER.debug("Cannot determine renewal need for null token");
            return false;
        }
        
        // If the token is already expired, it should be renewed
        if (token.isExpired()) {
            LOGGER.debug("Token is expired and should be renewed");
            return true;
        }
        
        // Get the token's expiration time
        Date expirationTime = token.getExpirationTime();
        
        if (expirationTime == null) {
            LOGGER.debug("Token has no expiration time, considering it for renewal");
            return true;
        }
        
        // Check if the token will expire within the renewal threshold
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.SECOND, renewalThresholdSeconds);
        Date renewalThreshold = calendar.getTime();
        
        boolean shouldRenew = expirationTime.before(renewalThreshold);
        if (shouldRenew) {
            LOGGER.debug("Token will expire soon and should be renewed");
        }
        
        return shouldRenew;
    }
}