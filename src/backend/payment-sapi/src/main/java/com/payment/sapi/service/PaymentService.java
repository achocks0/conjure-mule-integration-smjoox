package com.payment.sapi.service;

import com.payment.sapi.model.PaymentRequest;
import com.payment.sapi.model.PaymentResponse;

import java.util.List;
import java.util.Optional;

/**
 * Service interface that defines payment processing operations in the Payment-Sapi component.
 * This interface provides methods for processing payment requests, retrieving payment status,
 * and managing payment transactions with proper authentication and authorization.
 * 
 * The implementation of this interface will enforce token-based authentication for internal
 * service communication while supporting the backward compatibility requirements with
 * existing vendor integrations.
 */
public interface PaymentService {

    /**
     * Processes a payment request and returns a payment response.
     * 
     * @param paymentRequest The payment request containing transaction details
     * @param clientId The authenticated client ID extracted from the JWT token
     * @return PaymentResponse containing the payment transaction details and status
     * @throws IllegalArgumentException if the payment request is invalid
     * @throws SecurityException if the client is not authorized to process payments
     */
    PaymentResponse processPayment(PaymentRequest paymentRequest, String clientId);

    /**
     * Retrieves the status of a payment by its payment ID.
     * 
     * @param paymentId The unique identifier of the payment
     * @param clientId The authenticated client ID extracted from the JWT token
     * @return Optional containing the payment response if found and belongs to the client,
     *         empty otherwise
     * @throws SecurityException if the client is not authorized to access the payment
     */
    Optional<PaymentResponse> getPaymentStatus(String paymentId, String clientId);

    /**
     * Retrieves all payments for a specific client.
     * 
     * @param clientId The authenticated client ID extracted from the JWT token
     * @return List of payment responses for the client
     * @throws SecurityException if the client is not authorized to access the payments
     */
    List<PaymentResponse> getPaymentsByClient(String clientId);

    /**
     * Retrieves a payment by its reference for a specific client.
     * 
     * @param reference The payment reference (e.g., invoice number)
     * @param clientId The authenticated client ID extracted from the JWT token
     * @return Optional containing the payment response if found, empty otherwise
     * @throws SecurityException if the client is not authorized to access the payment
     */
    Optional<PaymentResponse> getPaymentByReference(String reference, String clientId);

    /**
     * Updates the status of an existing payment.
     * 
     * @param paymentId The unique identifier of the payment
     * @param status The new status to set
     * @param clientId The authenticated client ID extracted from the JWT token
     * @return Updated payment response with the new status
     * @throws IllegalArgumentException if the payment ID or status is invalid
     * @throws SecurityException if the client is not authorized to update the payment
     * @throws IllegalStateException if the payment cannot be updated due to its current state
     */
    PaymentResponse updatePaymentStatus(String paymentId, String status, String clientId);
}