package com.payment.sapi.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Model class representing a payment response in the Payment-Sapi component.
 * This class defines the structure of payment responses returned to Payment-Eapi
 * after processing payment transactions.
 * 
 * It contains essential payment information including the payment ID, status,
 * amount, currency, reference, description, and timestamps.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    /**
     * Unique identifier for the payment transaction
     */
    @JsonProperty("payment_id")
    private String paymentId;

    /**
     * Current status of the payment (e.g., PROCESSING, COMPLETED, FAILED)
     */
    @JsonProperty("status")
    private String status;

    /**
     * The monetary amount of the payment
     */
    @JsonProperty("amount")
    private BigDecimal amount;

    /**
     * The currency code of the payment (e.g., USD, EUR)
     */
    @JsonProperty("currency")
    private String currency;

    /**
     * External reference for the payment (e.g., invoice number)
     */
    @JsonProperty("reference")
    private String reference;

    /**
     * Descriptive information about the payment
     */
    @JsonProperty("description")
    private String description;

    /**
     * Timestamp when the payment was created
     */
    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date createdAt;

    /**
     * Timestamp when the payment was last updated
     */
    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date updatedAt;
}