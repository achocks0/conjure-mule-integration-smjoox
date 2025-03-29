package com.payment.eapi.model;

import java.io.Serializable; // JDK 11
import java.util.Date; // JDK 11

import lombok.Data; // 1.18.20
import lombok.Builder; // 1.18.20
import lombok.NoArgsConstructor; // 1.18.20
import lombok.AllArgsConstructor; // 1.18.20

/**
 * Model class representing credential information used for authentication in the 
 * Payment API Security Enhancement project. This class encapsulates client credentials
 * retrieved from Conjur vault and provides properties for credential validation,
 * rotation state tracking, and version management.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique identifier for the client
     */
    private String clientId;
    
    /**
     * Hashed version of the client secret
     */
    private String hashedSecret;
    
    /**
     * Indicates if the credential is active
     */
    private boolean active;
    
    /**
     * Version of the credential, used for tracking during rotation
     */
    private String version;
    
    /**
     * Current rotation state of the credential.
     * Possible values: null, "DUAL_ACTIVE", "OLD_DEPRECATED"
     */
    private String rotationState;
    
    /**
     * Date when the credential was created
     */
    private Date createdAt;
    
    /**
     * Date when the credential was last updated
     */
    private Date updatedAt;
    
    /**
     * Date when the credential expires, if applicable
     */
    private Date expiresAt;
    
    /**
     * Checks if the credential has expired by comparing the expiration date with the current time.
     * 
     * @return true if the credential has expired, false otherwise or if no expiration date is set
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return expiresAt.before(new Date());
    }
    
    /**
     * Checks if the credential is currently in the rotation process.
     * 
     * @return true if the credential is in rotation, false otherwise
     */
    public boolean isInRotation() {
        if (rotationState == null) {
            return false;
        }
        return "DUAL_ACTIVE".equals(rotationState) || "OLD_DEPRECATED".equals(rotationState);
    }
    
    /**
     * Checks if the credential is valid (active and not expired).
     * 
     * @return true if the credential is valid, false otherwise
     */
    public boolean isValid() {
        return active && !isExpired();
    }
}