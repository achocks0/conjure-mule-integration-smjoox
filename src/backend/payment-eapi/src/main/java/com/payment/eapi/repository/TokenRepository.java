package com.payment.eapi.repository;

import com.payment.eapi.repository.entity.TokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for managing token entities in the database.
 * Provides methods for CRUD operations and custom queries for token management.
 */
@Repository
public interface TokenRepository extends JpaRepository<TokenEntity, Long> {

    /**
     * Finds a token entity by its JWT ID (jti)
     *
     * @param jti The JWT ID to search for
     * @return The token entity if found, empty Optional otherwise
     */
    Optional<TokenEntity> findByJti(String jti);

    /**
     * Finds a token entity by client ID
     *
     * @param clientId The client ID to search for
     * @return The token entity if found, empty Optional otherwise
     */
    Optional<TokenEntity> findByClientId(String clientId);

    /**
     * Finds token entities by client ID and status
     *
     * @param clientId The client ID to search for
     * @param status The status to search for
     * @return List of token entities matching the criteria
     */
    List<TokenEntity> findByClientIdAndStatus(String clientId, String status);

    /**
     * Finds all expired token entities
     *
     * @param currentDate The current date to check against token expiration
     * @return List of expired token entities
     */
    @Query("SELECT t FROM TokenEntity t WHERE t.expirationTime < :currentDate AND t.status = 'ACTIVE'")
    List<TokenEntity> findExpiredTokens(@Param("currentDate") Date currentDate);

    /**
     * Finds the IDs of expired tokens for batch processing
     *
     * @param currentDate The current date to check against token expiration
     * @param limit Maximum number of results to return
     * @param offset Number of results to skip
     * @return List of expired token JTIs
     */
    @Query("SELECT t.jti FROM TokenEntity t WHERE t.expirationTime < :currentDate AND t.status = 'ACTIVE' ORDER BY t.expirationTime ASC LIMIT :limit OFFSET :offset")
    List<String> findExpiredTokenIds(@Param("currentDate") Date currentDate, @Param("limit") int limit, @Param("offset") int offset);

    /**
     * Updates the status of multiple tokens in a batch operation
     *
     * @param tokenIds List of token JTIs to update
     * @param status The new status to set
     * @return Number of tokens updated
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenEntity t SET t.status = :status WHERE t.jti IN :tokenIds")
    int updateStatusBatch(@Param("tokenIds") List<String> tokenIds, @Param("status") String status);

    /**
     * Updates the status of a single token
     *
     * @param jti The JWT ID of the token to update
     * @param status The new status to set
     * @return Number of tokens updated (0 or 1)
     */
    @Modifying
    @Transactional
    @Query("UPDATE TokenEntity t SET t.status = :status WHERE t.jti = :jti")
    int updateStatus(@Param("jti") String jti, @Param("status") String status);

    /**
     * Deletes expired tokens from the database
     *
     * @param expirationThreshold Date threshold for deletion
     * @return Number of tokens deleted
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TokenEntity t WHERE t.expirationTime < :expirationThreshold")
    int deleteExpiredTokens(@Param("expirationThreshold") Date expirationThreshold);

    /**
     * Counts the number of active tokens for a specific client
     *
     * @param clientId The client ID to count tokens for
     * @return Count of active tokens
     */
    @Query("SELECT COUNT(t) FROM TokenEntity t WHERE t.clientId = :clientId AND t.status = 'ACTIVE'")
    long countActiveTokensForClient(@Param("clientId") String clientId);
}