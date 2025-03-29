package com.payment.eapi.model;

import com.payment.common.model.TokenClaims;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Collections;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Model class representing an authentication token used for secure communication
 * between Payment-Eapi and Payment-Sapi services. Contains the JWT token string,
 * claims, expiration time, and metadata.
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
     * The unique identifier (jti) of the token
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
        return jti;
    }
    
    /**
     * Gets the permissions associated with this token
     * 
     * @return list of permissions granted to this token
     */
    public List<String> getPermissions() {
        if (claims == null) {
            return Collections.emptyList();
        }
        return claims.getPermissions();
    }
    
    /**
     * Checks if the token has a specific permission
     * 
     * @param permission the permission to check
     * @return true if the token has the specified permission, false otherwise
     */
    public boolean hasPermission(String permission) {
        if (claims == null) {
            return false;
        }
        return claims.hasPermission(permission);
    }
}