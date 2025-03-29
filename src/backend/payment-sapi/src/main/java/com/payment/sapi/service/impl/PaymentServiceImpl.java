package com.payment.sapi.service.impl;

import com.payment.sapi.exception.PaymentProcessingException;
import com.payment.sapi.model.PaymentRequest;
import com.payment.sapi.model.PaymentResponse;
import com.payment.sapi.repository.PaymentRepository;
import com.payment.sapi.repository.entity.PaymentEntity;
import com.payment.sapi.service.AuditService;
import com.payment.sapi.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the PaymentService interface that handles payment processing operations
 * in the Payment-Sapi component.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest paymentRequest, String clientId) {
        log.info("Processing payment request for client: {}, reference: {}", clientId, paymentRequest.getReference());
        
        // Validate payment request
        validatePaymentRequest(paymentRequest);
        
        // Check for duplicate reference
        checkDuplicateReference(clientId, paymentRequest.getReference());
        
        // Generate payment ID
        String paymentId = UUID.randomUUID().toString();
        
        // Create payment entity with PROCESSING status
        PaymentEntity paymentEntity = PaymentEntity.builder()
                .paymentId(paymentId)
                .clientId(clientId)
                .status("PROCESSING")
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .reference(paymentRequest.getReference())
                .description(paymentRequest.getDescription())
                .build();
        
        // Save initial payment record
        paymentEntity = paymentRepository.save(paymentEntity);
        
        // Log payment initiation
        auditService.logSecurityEvent("PAYMENT_INITIATED", clientId, 
                "Payment initiated with ID: " + paymentId);
        
        // Process payment with backend system
        boolean processed = processPaymentWithBackend(paymentEntity);
        
        if (processed) {
            // Update status to COMPLETED if successful
            paymentEntity.setStatus("COMPLETED");
            paymentEntity = paymentRepository.save(paymentEntity);
            
            // Log successful completion
            auditService.logSecurityEvent("PAYMENT_COMPLETED", clientId, 
                    "Payment completed successfully with ID: " + paymentId);
            
            return paymentEntity.toPaymentResponse();
        } else {
            // Update status to FAILED if processing fails
            paymentEntity.setStatus("FAILED");
            paymentEntity = paymentRepository.save(paymentEntity);
            
            // Log failure
            auditService.logSecurityEvent("PAYMENT_FAILED", clientId, 
                    "Payment processing failed for ID: " + paymentId);
            
            throw new PaymentProcessingException("Payment processing failed", "PROCESSING_ERROR");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentStatus(String paymentId, String clientId) {
        log.info("Retrieving payment status for paymentId: {}, clientId: {}", paymentId, clientId);
        
        Optional<PaymentEntity> paymentEntityOpt = paymentRepository.findByPaymentId(paymentId);
        
        if (paymentEntityOpt.isEmpty()) {
            log.debug("Payment not found with ID: {}", paymentId);
            return Optional.empty();
        }
        
        PaymentEntity paymentEntity = paymentEntityOpt.get();
        
        // Security check: Verify the payment belongs to the authenticated client
        if (!paymentEntity.getClientId().equals(clientId)) {
            // Log unauthorized access attempt
            auditService.logAuthorizationDecision(clientId, "payment/" + paymentId, "read", false);
            
            log.warn("Security violation: Client {} attempted to access payment {} belonging to client {}", 
                    clientId, paymentId, paymentEntity.getClientId());
            
            return Optional.empty();
        }
        
        // Log successful authorization
        auditService.logAuthorizationDecision(clientId, "payment/" + paymentId, "read", true);
        
        return Optional.of(paymentEntity.toPaymentResponse());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByClient(String clientId) {
        log.info("Retrieving all payments for clientId: {}", clientId);
        
        List<PaymentEntity> payments = paymentRepository.findByClientId(clientId);
        
        // Log successful retrieval
        auditService.logAuthorizationDecision(clientId, "payments", "list", true);
        
        return payments.stream()
                .map(PaymentEntity::toPaymentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getPaymentByReference(String reference, String clientId) {
        log.info("Retrieving payment for clientId: {}, reference: {}", clientId, reference);
        
        Optional<PaymentEntity> paymentEntityOpt = paymentRepository.findByClientIdAndReference(clientId, reference);
        
        // Log access
        if (paymentEntityOpt.isPresent()) {
            auditService.logAuthorizationDecision(clientId, "payment/reference/" + reference, "read", true);
        }
        
        return paymentEntityOpt.map(PaymentEntity::toPaymentResponse);
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentStatus(String paymentId, String status, String clientId) {
        log.info("Updating payment status for paymentId: {}, new status: {}", paymentId, status);
        
        // Validate status
        if (status == null || status.trim().isEmpty()) {
            throw new PaymentProcessingException("Status cannot be empty", "VALIDATION_ERROR");
        }
        
        String normalizedStatus = status.toUpperCase();
        
        // Valid status values (in a real implementation, this could be an enum or from configuration)
        List<String> validStatuses = List.of("PROCESSING", "COMPLETED", "FAILED", "CANCELLED", "REFUNDED");
        if (!validStatuses.contains(normalizedStatus)) {
            throw new PaymentProcessingException(
                    "Invalid status value: " + status, "VALIDATION_ERROR");
        }
        
        PaymentEntity paymentEntity = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentProcessingException(
                        "Payment not found with ID: " + paymentId, "PAYMENT_NOT_FOUND"));
        
        // Security check: Verify the payment belongs to the authenticated client
        if (!paymentEntity.getClientId().equals(clientId)) {
            // Log unauthorized access attempt
            auditService.logAuthorizationDecision(clientId, "payment/" + paymentId, "update", false);
            
            log.warn("Security violation: Client {} attempted to update payment {} belonging to client {}", 
                    clientId, paymentId, paymentEntity.getClientId());
            
            throw new PaymentProcessingException("Not authorized to update this payment", "UNAUTHORIZED");
        }
        
        // Log successful authorization
        auditService.logAuthorizationDecision(clientId, "payment/" + paymentId, "update", true);
        
        // Update status
        paymentEntity.setStatus(normalizedStatus);
        PaymentEntity updatedEntity = paymentRepository.save(paymentEntity);
        
        // Log status update
        auditService.logSecurityEvent("PAYMENT_STATUS_UPDATED", clientId, 
                "Payment status updated for ID: " + paymentId + ", new status: " + normalizedStatus);
        
        return updatedEntity.toPaymentResponse();
    }
    
    /**
     * Validates a payment request for required fields and business rules.
     *
     * @param paymentRequest The payment request to validate
     * @throws PaymentProcessingException if validation fails
     */
    private void validatePaymentRequest(PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new PaymentProcessingException("Payment request cannot be null", "VALIDATION_ERROR");
        }
        
        if (paymentRequest.getAmount() == null || paymentRequest.getAmount().doubleValue() <= 0) {
            throw new PaymentProcessingException("Payment amount must be greater than zero", "VALIDATION_ERROR");
        }
        
        if (paymentRequest.getCurrency() == null || !paymentRequest.getCurrency().matches("^[A-Z]{3}$")) {
            throw new PaymentProcessingException("Currency must be a valid 3-letter code", "VALIDATION_ERROR");
        }
        
        if (paymentRequest.getReference() == null || paymentRequest.getReference().trim().isEmpty()) {
            throw new PaymentProcessingException("Payment reference cannot be empty", "VALIDATION_ERROR");
        }
    }
    
    /**
     * Checks if a payment with the same reference already exists for the client.
     *
     * @param clientId The client ID
     * @param reference The payment reference
     * @throws PaymentProcessingException if a duplicate is found
     */
    private void checkDuplicateReference(String clientId, String reference) {
        if (paymentRepository.existsByClientIdAndReference(clientId, reference)) {
            throw new PaymentProcessingException(
                    "Duplicate payment reference: " + reference, "DUPLICATE_REFERENCE");
        }
    }
    
    /**
     * Processes the payment with the backend payment system (simulated).
     * In a real implementation, this would integrate with the actual payment backend.
     *
     * @param paymentEntity The payment entity to process
     * @return true if processing is successful, false otherwise
     */
    private boolean processPaymentWithBackend(PaymentEntity paymentEntity) {
        log.info("Processing payment with backend system for payment ID: {}", paymentEntity.getPaymentId());
        
        // In a real implementation, this would call the actual payment processing backend
        // For now, we'll simulate a successful processing
        
        try {
            // Simulate processing time
            Thread.sleep(500);
            
            log.info("Backend processing successful for payment ID: {}", paymentEntity.getPaymentId());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment processing interrupted", e);
            return false;
        }
    }
}