package com.payment.sapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Model class representing a payment request with all necessary fields for processing a payment transaction.
 * This class is used for receiving payment data from Payment-Eapi and supports the internal token-based
 * authentication flow while maintaining backward compatibility with the existing API contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    /**
     * The payment amount with precise decimal arithmetic.
     * Must be greater than or equal to 0.01.
     */
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @JsonProperty("amount")
    private BigDecimal amount;

    /**
     * The three-letter currency code in ISO 4217 format.
     * Must be exactly 3 uppercase letters (e.g., USD, EUR, GBP).
     */
    @NotNull(message = "Currency cannot be null")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
    @JsonProperty("currency")
    private String currency;

    /**
     * The payment reference or identifier.
     * This is typically an invoice number or other business reference.
     */
    @NotBlank(message = "Reference cannot be blank")
    @Size(max = 50, message = "Reference cannot exceed 50 characters")
    @JsonProperty("reference")
    private String reference;

    /**
     * Optional description of the payment.
     */
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    @JsonProperty("description")
    private String description;
}