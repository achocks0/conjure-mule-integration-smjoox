package com.payment.sapi.service;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;

/**
 * Service interface responsible for renewing JWT tokens used for authentication in the Payment-Sapi component.
 * This service handles the renewal of expired or about-to-expire tokens by communicating with the Payment-Eapi
 * service to request new tokens, ensuring continuous authentication without disrupting the user experience.
 * <p>
 * This interface is part of the Payment API Security Enhancement project that implements token-based
 * authentication for internal service communication as defined in F-002 (Token-based Authentication).
 */
public interface TokenRenewalService {
    
    /**
     * Renews an expired or about-to-expire JWT token by requesting a new token from the Payment-Eapi service.
     * This method is called when token validation determines that a token needs renewal.
     * 
     * @param token The token to be renewed
     * @return ValidationResult containing either the renewed token or an error message if renewal failed
     */
    ValidationResult renewToken(Token token);
    
    /**
     * Sends a token renewal request to the Payment-Eapi service.
     * This method handles the communication with the Payment-Eapi service to obtain a new token.
     * 
     * @param tokenString The current token string to be renewed
     * @return The renewed Token object or null if renewal fails
     */
    Token requestTokenRenewal(String tokenString);
    
    /**
     * Determines if a token should be renewed based on its expiration time.
     * Tokens that are expired or about to expire (within the configured threshold) should be renewed.
     * 
     * @param token The token to check for renewal
     * @return true if the token should be renewed, false otherwise
     */
    boolean shouldRenew(Token token);
}