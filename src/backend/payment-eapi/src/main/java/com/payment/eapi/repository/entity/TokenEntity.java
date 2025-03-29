package com.payment.eapi.repository.entity;

import com.payment.eapi.model.Token;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.GenerationType;
import javax.persistence.Index;
import java.util.Date;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * JPA entity class representing a token stored in the database.
 * Maps to the tokens table and contains token metadata for tracking, 
 * validation, and auditing purposes as part of the token-based authentication mechanism.
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "token_string", nullable = false, length = 2048)
    private String tokenString;
    
    @Column(name = "jti", nullable = false, unique = true, length = 255)
    private String jti;
    
    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expiration_time", nullable = false)
    private Date expirationTime;
    
    @Column(name = "status", nullable = false, length = 50)
    private String status;
    
    /**
     * Converts the entity to a model object
     * 
     * @return A Token model object created from this entity
     */
    public Token toModel() {
        return Token.builder()
                .tokenString(this.tokenString)
                .expirationTime(this.expirationTime)
                .jti(this.jti)
                .clientId(this.clientId)
                .build();
    }
    
    /**
     * Creates an entity from a model object
     * 
     * @param token The Token model to convert
     * @return A TokenEntity created from the provided Token model
     */
    public static TokenEntity fromModel(Token token) {
        return TokenEntity.builder()
                .tokenString(token.getTokenString())
                .expirationTime(token.getExpirationTime())
                .jti(token.getJti())
                .clientId(token.getClientId())
                .createdAt(new Date())
                .status("ACTIVE")
                .build();
    }
}