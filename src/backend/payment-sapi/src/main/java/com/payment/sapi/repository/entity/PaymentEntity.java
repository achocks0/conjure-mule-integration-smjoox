package com.payment.sapi.repository.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.Date;

import com.payment.sapi.model.PaymentResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity class representing a payment transaction in the database.
 * This entity maps to the 'payments' table and stores all payment-related information
 * including payment ID, client ID, status, amount, currency, reference, description, and timestamps.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_payment_id", columnList = "payment_id"),
    @Index(name = "idx_payments_client_id", columnList = "client_id"),
    @Index(name = "idx_payments_reference", columnList = "client_id,reference")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true, length = 36)
    private String paymentId;

    @Column(name = "client_id", nullable = false, length = 50)
    private String clientId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference", nullable = false, length = 50)
    private String reference;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Column(name = "updated_at")
    private Date updatedAt;

    /**
     * Sets the created_at timestamp before the entity is persisted.
     */
    @PrePersist
    public void prePersist() {
        createdAt = new Date();
    }

    /**
     * Sets the updated_at timestamp before the entity is updated.
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = new Date();
    }

    /**
     * Converts the entity to a PaymentResponse model for API responses.
     *
     * @return PaymentResponse object with data from this entity
     */
    public PaymentResponse toPaymentResponse() {
        return PaymentResponse.builder()
                .paymentId(this.paymentId)
                .status(this.status)
                .amount(this.amount)
                .currency(this.currency)
                .reference(this.reference)
                .description(this.description)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }
}