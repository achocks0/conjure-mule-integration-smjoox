package com.payment.sapi.repository;

import com.payment.sapi.repository.entity.TokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for token entities that provides methods for CRUD operations 
 * and custom queries for token management.
 * This repository is used by the TokenValidationService and TokenRenewalService to persist, retrieve,
 * and manage token information for authentication and validation purposes.
 */
@Repository
public interface TokenRepository extends JpaRepository<TokenEntity, Long> {

    /**
     * Finds a token entity by its unique JWT ID (jti)
     * 
     * @param jti the JWT ID to search for
     * @return Optional containing the token entity if found, empty otherwise
     */
    Optional<TokenEntity> findByJti(String jti);

    /**
     * Finds a token entity by its token string value
     * 
     * @param tokenString the token string to search for
     * @return Optional containing the token entity if found, empty otherwise
     */
    Optional<TokenEntity> findByTokenString(String tokenString);

    /**
     * Finds all token entities for a specific client
     * 
     * @param clientId the client ID to search for
     * @return List of token entities for the specified client
     */
    List<TokenEntity> findByClientId(String clientId);

    /**
     * Finds all token entities for a specific client with a given status
     * 
     * @param clientId the client ID to search for
     * @param status the status to filter by (e.g., "ACTIVE", "REVOKED")
     * @return List of token entities for the specified client and status
     */
    List<TokenEntity> findByClientIdAndStatus(String clientId, String status);

    /**
     * Finds all token entities that have expired before the specified date
     * 
     * @param expirationTime the date to check expiration against
     * @return List of expired token entities
     */
    List<TokenEntity> findByExpirationTimeBefore(Date expirationTime);

    /**
     * Updates the status of a token entity
     * 
     * @param jti the JWT ID of the token to update
     * @param status the new status to set
     * @return Number of rows affected by the update
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenEntity t SET t.status = :status, t.updatedAt = CURRENT_TIMESTAMP WHERE t.jti = :jti")
    int updateStatus(String jti, String status);

    /**
     * Revokes a token by setting its status to 'REVOKED'
     * 
     * @param jti the JWT ID of the token to revoke
     * @return Number of rows affected by the update
     */
    default int revokeToken(String jti) {
        return updateStatus(jti, "REVOKED");
    }

    /**
     * Revokes all active tokens for a specific client
     * 
     * @param clientId the client ID whose tokens should be revoked
     * @return Number of rows affected by the update
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenEntity t SET t.status = 'REVOKED', t.updatedAt = CURRENT_TIMESTAMP WHERE t.clientId = :clientId AND t.status = 'ACTIVE'")
    int revokeAllClientTokens(String clientId);

    /**
     * Deletes all tokens that have expired before the specified date
     * 
     * @param expirationTime the date to check expiration against
     * @return Number of rows deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenEntity t WHERE t.expirationTime < :expirationTime")
    int deleteExpiredTokens(Date expirationTime);
}