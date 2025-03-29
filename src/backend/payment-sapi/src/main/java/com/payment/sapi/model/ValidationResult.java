package com.payment.sapi.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing the result of token validation operations in the Payment-Sapi component.
 * This class encapsulates the validation status, error messages, and renewed token information
 * when applicable.
 * <p>
 * Used as part of the token-based authentication mechanism to communicate the outcome
 * of token validation processes between components.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indicates if the token validation was successful
     */
    private boolean isValid;
    
    /**
     * Indicates if the token has expired
     */
    private boolean isExpired;
    
    /**
     * Indicates if the token lacks required permissions
     */
    private boolean isForbidden;
    
    /**
     * Indicates if the token was renewed
     */
    private boolean isRenewed;
    
    /**
     * Error message in case validation failed
     */
    private String errorMessage;
    
    /**
     * Renewed token string when a token is renewed
     */
    private String renewedTokenString;
    
    /**
     * Static factory method to create a successful validation result
     * 
     * @return A ValidationResult instance indicating successful validation
     */
    public static ValidationResult valid() {
        return ValidationResult.builder()
                .isValid(true)
                .isExpired(false)
                .isForbidden(false)
                .isRenewed(false)
                .build();
    }
    
    /**
     * Static factory method to create a failed validation result with an error message
     * 
     * @param errorMessage the error message describing why validation failed
     * @return A ValidationResult instance indicating failed validation with error message
     */
    public static ValidationResult invalid(String errorMessage) {
        return ValidationResult.builder()
                .isValid(false)
                .isExpired(false)
                .isForbidden(false)
                .isRenewed(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * Static factory method to create a validation result for an expired token
     * 
     * @param errorMessage the error message describing the expiration issue
     * @return A ValidationResult instance indicating token expiration
     */
    public static ValidationResult expired(String errorMessage) {
        return ValidationResult.builder()
                .isValid(false)
                .isExpired(true)
                .isForbidden(false)
                .isRenewed(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * Static factory method to create a validation result for insufficient permissions
     * 
     * @param errorMessage the error message describing the permission issue
     * @return A ValidationResult instance indicating insufficient permissions
     */
    public static ValidationResult forbidden(String errorMessage) {
        return ValidationResult.builder()
                .isValid(false)
                .isExpired(false)
                .isForbidden(true)
                .isRenewed(false)
                .errorMessage(errorMessage)
                .build();
    }
    
    /**
     * Static factory method to create a validation result for a renewed token
     * 
     * @param renewedTokenString the new token string after renewal
     * @return A ValidationResult instance containing the renewed token
     */
    public static ValidationResult renewed(String renewedTokenString) {
        return ValidationResult.builder()
                .isValid(true)
                .isExpired(false)
                .isForbidden(false)
                .isRenewed(true)
                .renewedTokenString(renewedTokenString)
                .build();
    }
    
    /**
     * Checks if the validation result contains an error
     * 
     * @return true if the validation failed, false otherwise
     */
    public boolean hasError() {
        return !isValid;
    }
}