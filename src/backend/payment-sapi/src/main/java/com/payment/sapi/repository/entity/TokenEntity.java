package com.payment.sapi.repository.entity;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.util.Date;

import com.payment.sapi.model.Token;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * JPA entity class representing a token stored in the database for the Payment-Sapi service.
 * This entity is used for persisting token information, tracking token lifecycle, and supporting 
 * token validation and audit requirements.
 */
@Entity
@Table(name = "tokens", indexes = {
    @Index(name = "idx_token_jti", columnList = "jti"),
    @Index(name = "idx_token_client_id", columnList = "client_id"),
    @Index(name = "idx_token_expiration", columnList = "expiration_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenEntity {
    
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "token_string", nullable = false, length = 4000)
    private String tokenString;
    
    @Column(name = "jti", nullable = false, unique = true)
    private String jti;
    
    @Column(name = "client_id", nullable = false)
    private String clientId;
    
    @Column(name = "expiration_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date expirationTime;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    
    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date updatedAt;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "user_agent")
    private String userAgent;
    
    /**
     * JPA lifecycle callback that sets creation and update timestamps before entity persistence
     */
    @PrePersist
    public void prePersist() {
        Date now = new Date();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    /**
     * JPA lifecycle callback that updates the updatedAt timestamp before entity update
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = new Date();
    }
    
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
     * Checks if the token is in active status
     * 
     * @return true if the token status is 'ACTIVE', false otherwise
     */
    public boolean isActive() {
        if (status == null) {
            return false;
        }
        return "ACTIVE".equals(status);
    }
    
    /**
     * Checks if the token has been revoked
     * 
     * @return true if the token status is 'REVOKED', false otherwise
     */
    public boolean isRevoked() {
        if (status == null) {
            return false;
        }
        return "REVOKED".equals(status);
    }
    
    /**
     * Static factory method to create a TokenEntity from a Token model
     * 
     * @param token the Token model to convert
     * @return a new TokenEntity instance populated with data from the Token model
     */
    public static TokenEntity fromModel(Token token) {
        return TokenEntity.builder()
                .tokenString(token.getTokenString())
                .jti(token.getJti())
                .clientId(token.getClientId())
                .expirationTime(token.getExpirationTime())
                .status("ACTIVE")
                .build();
    }
    
    /**
     * Converts this entity to a Token model object
     * 
     * @return a Token model populated with data from this entity
     */
    public Token toModel() {
        return Token.builder()
                .tokenString(this.tokenString)
                .jti(this.jti)
                .clientId(this.clientId)
                .expirationTime(this.expirationTime)
                .build();
    }
}