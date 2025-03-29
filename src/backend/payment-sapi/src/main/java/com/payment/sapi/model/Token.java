package com.payment.sapi.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.payment.common.model.TokenClaims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing an authentication token used for secure communication
 * between Payment-Eapi and Payment-Sapi services. Contains the JWT token string,
 * claims, expiration time, and metadata.
 * <p>
 * This class is part of the Payment API Security Enhancement project's
 * token-based authentication mechanism, which replaces header-based
 * Client ID and Client Secret authentication with a more secure approach.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Token implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The JWT token string
     */
    private String tokenString;
    
    /**
     * The claims contained within the token
     */
    private TokenClaims claims;
    
    /**
     * The expiration time of the token
     */
    private Date expirationTime;
    
    /**
     * The JWT ID (a unique identifier for the token)
     */
    private String jti;
    
    /**
     * The client ID associated with the token
     */
    private String clientId;
    
    /**
     * Checks if the token has expired based on the expiration time
     * 
     * @return true if the token has expired, false otherwise
     */
    public boolean isExpired() {
        if (claims != null) {
            return claims.isExpired();
        }
        if (expirationTime == null) {
            return true; // Consider as expired for safety
        }
        return expirationTime.before(new Date());
    }
    
    /**
     * Gets the unique identifier (jti) of this token
     * 
     * @return the token's unique identifier (jti)
     */
    public String getTokenId() {
        if (claims != null && claims.getJti() != null) {
            return claims.getJti();
        }
        return jti;
    }
    
    /**
     * Gets the permissions associated with this token
     * 
     * @return list of permissions granted to this token
     */
    public List<String> getPermissions() {
        if (claims != null) {
            return claims.getPermissions();
        }
        return null;
    }
    
    /**
     * Checks if the token has a specific permission
     * 
     * @param permission the permission to check
     * @return true if the token has the specified permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        if (claims != null) {
            return claims.hasPermission(permission);
        }
        return false;
    }
    
    /**
     * Gets the subject (client ID) from the token claims
     * 
     * @return the subject of the token, typically the client ID
     */
    public String getSubject() {
        if (claims != null) {
            return claims.getSub();
        }
        return clientId;
    }
}