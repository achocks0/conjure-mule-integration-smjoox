package com.payment.sapi.repository;

import com.payment.sapi.repository.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository interface for payment entities that provides
 * methods for CRUD operations and custom queries for payment data.
 * This repository is used by the PaymentService to persist and retrieve payment information.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Finds a payment entity by its unique payment ID.
     *
     * @param paymentId the unique payment identifier
     * @return Optional containing the payment entity if found, empty otherwise
     */
    Optional<PaymentEntity> findByPaymentId(String paymentId);

    /**
     * Finds all payment entities for a specific client.
     *
     * @param clientId the client identifier
     * @return List of payment entities for the specified client
     */
    List<PaymentEntity> findByClientId(String clientId);

    /**
     * Finds a payment entity by client ID and reference.
     *
     * @param clientId the client identifier
     * @param reference the payment reference
     * @return Optional containing the payment entity if found, empty otherwise
     */
    Optional<PaymentEntity> findByClientIdAndReference(String clientId, String reference);

    /**
     * Checks if a payment with the specified client ID and reference exists.
     *
     * @param clientId the client identifier
     * @param reference the payment reference
     * @return true if a payment with the specified client ID and reference exists, false otherwise
     */
    boolean existsByClientIdAndReference(String clientId, String reference);
    
    /**
     * Saves a payment entity to the database.
     * Note: This method is inherited from JpaRepository but explicitly declared here for clarity.
     *
     * @param entity the payment entity to save
     * @return the saved payment entity with any generated IDs populated
     */
    PaymentEntity save(PaymentEntity entity);
}