package com.payment.common.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Model class representing the claims contained within a JWT token.
 * Used for authentication between services in the Payment API Security Enhancement project.
 * Contains standard JWT claims (sub, iss, aud, exp, iat, jti) and custom claims (permissions).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenClaims implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Subject - typically the client ID for whom the token was issued
     */
    private String sub;
    
    /**
     * Issuer - identifies the principal that issued the token (e.g., "payment-eapi")
     */
    private String iss;
    
    /**
     * Audience - identifies the recipients that the token is intended for (e.g., "payment-sapi")
     */
    private String aud;
    
    /**
     * Expiration time - identifies the expiration time on or after which the token must not be accepted
     */
    private Date exp;
    
    /**
     * Issued at - identifies the time at which the token was issued
     */
    private Date iat;
    
    /**
     * JWT ID - provides a unique identifier for the token
     */
    private String jti;
    
    /**
     * Custom claim - permissions assigned to the token, defining allowed operations
     */
    private List<String> permissions;
    
    /**
     * Checks if the token has expired based on the expiration date
     * 
     * @return true if the token has expired, false otherwise
     */
    public boolean isExpired() {
        if (exp == null) {
            return true; // Consider as expired for safety
        }
        return exp.before(new Date());
    }
    
    /**
     * Checks if the token has a specific permission
     * 
     * @param permission the permission to check
     * @return true if the token has the specified permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        if (permission == null) {
            return false; // Null permission check
        }
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains(permission);
    }
}