package com.payment.eapi.repository.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Index;
import java.util.Date;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entity class representing credential information stored in the database.
 * This entity maps to the credential table and contains metadata about client credentials
 * used for authentication, supporting multiple credential versions during rotation.
 */
@Entity
@Table(name = "credential", indexes = {
    @Index(name = "idx_credential_client_id", columnList = "clientId", unique = true),
    @Index(name = "idx_credential_active", columnList = "active"),
    @Index(name = "idx_credential_rotation", columnList = "rotationState")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialEntity {

    /**
     * Auto-generated primary key for the credential record
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the client
     */
    @Column(nullable = false, length = 50)
    private String clientId;

    /**
     * Hashed version of the client secret, never stored in plain text
     */
    @Column(nullable = false)
    private String hashedSecret;

    /**
     * Flag indicating whether this credential is active
     */
    @Column(nullable = false)
    private boolean active;

    /**
     * Version identifier of the credential, used for tracking during rotation
     */
    @Column(nullable = false, length = 50)
    private String version;

    /**
     * Current state in the rotation process (null, DUAL_ACTIVE, OLD_DEPRECATED)
     */
    @Column(length = 20)
    private String rotationState;

    /**
     * Timestamp when the credential was created
     */
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    /**
     * Timestamp when the credential was last updated
     */
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;

    /**
     * Timestamp when the credential expires (if applicable)
     */
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiresAt;

    /**
     * Checks if the credential has expired by comparing the expiration date with the current time
     * 
     * @return true if the credential has expired, false otherwise
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false; // No expiration date means it doesn't expire
        }
        return expiresAt.before(new Date());
    }

    /**
     * Checks if the credential is currently in the rotation process
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
     * Checks if the credential is valid (active and not expired)
     * 
     * @return true if the credential is valid, false otherwise
     */
    public boolean isValid() {
        return active && !isExpired();
    }

    /**
     * Converts the entity to a Credential model object
     * 
     * @return a Credential model object with data from this entity
     */
    public Credential toModel() {
        return Credential.builder()
                .id(id)
                .clientId(clientId)
                .hashedSecret(hashedSecret)
                .active(active)
                .version(version)
                .rotationState(rotationState)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Creates a CredentialEntity from a Credential model object
     * 
     * @param credential the Credential model to convert from
     * @return a CredentialEntity with data from the model object
     */
    public static CredentialEntity fromModel(Credential credential) {
        return CredentialEntity.builder()
                .id(credential.getId())
                .clientId(credential.getClientId())
                .hashedSecret(credential.getHashedSecret())
                .active(credential.isActive())
                .version(credential.getVersion())
                .rotationState(credential.getRotationState())
                .createdAt(credential.getCreatedAt())
                .updatedAt(credential.getUpdatedAt())
                .expiresAt(credential.getExpiresAt())
                .build();
    }
}