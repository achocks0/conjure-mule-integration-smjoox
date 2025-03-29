package com.payment.eapi.repository;

import com.payment.eapi.repository.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing credential entities in the database.
 * Provides methods for storing, retrieving, and managing credentials to support
 * authentication and credential rotation functionality.
 */
@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, Long> {

    /**
     * Finds a credential entity by client ID
     *
     * @param clientId the client identifier
     * @return an Optional containing the credential entity if found, empty otherwise
     */
    Optional<CredentialEntity> findByClientId(String clientId);

    /**
     * Finds an active credential entity by client ID
     *
     * @param clientId the client identifier
     * @param active the active status to filter by
     * @return an Optional containing the credential entity if found, empty otherwise
     */
    Optional<CredentialEntity> findByClientIdAndActive(String clientId, boolean active);

    /**
     * Finds credential entities by rotation state
     *
     * @param rotationState the rotation state to filter by
     * @return a list of credential entities with the specified rotation state
     */
    List<CredentialEntity> findByRotationState(String rotationState);

    /**
     * Finds credential entities by client ID and rotation state
     *
     * @param clientId the client identifier
     * @param rotationState the rotation state to filter by
     * @return a list of credential entities matching the criteria
     */
    List<CredentialEntity> findByClientIdAndRotationState(String clientId, String rotationState);

    /**
     * Updates the rotation state of a credential entity
     *
     * @param clientId the client identifier
     * @param rotationState the new rotation state
     * @return the number of rows affected
     */
    @Query("UPDATE CredentialEntity c SET c.rotationState = :rotationState WHERE c.clientId = :clientId")
    int updateRotationState(@Param("clientId") String clientId, @Param("rotationState") String rotationState);

    /**
     * Updates the active status of a credential entity
     *
     * @param clientId the client identifier
     * @param active the new active status
     * @return the number of rows affected
     */
    @Query("UPDATE CredentialEntity c SET c.active = :active WHERE c.clientId = :clientId")
    int updateActiveStatus(@Param("clientId") String clientId, @Param("active") boolean active);

    /**
     * Finds credential entities that have expired
     *
     * @return a list of expired credential entities
     */
    @Query("SELECT c FROM CredentialEntity c WHERE c.expiresAt IS NOT NULL AND c.expiresAt < CURRENT_TIMESTAMP")
    List<CredentialEntity> findExpiredCredentials();

    /**
     * Counts credential entities by client ID, active status, and rotation state
     *
     * @param clientId the client identifier
     * @param active the active status
     * @param rotationState the rotation state
     * @return the count of matching credential entities
     */
    long countByClientIdAndActiveAndRotationState(String clientId, boolean active, String rotationState);
}