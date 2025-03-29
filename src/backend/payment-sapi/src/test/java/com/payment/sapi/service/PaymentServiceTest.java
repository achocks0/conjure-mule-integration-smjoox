package com.payment.sapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.payment.sapi.exception.PaymentProcessingException;
import com.payment.sapi.model.PaymentRequest;
import com.payment.sapi.model.PaymentResponse;
import com.payment.sapi.repository.PaymentRepository;
import com.payment.sapi.repository.entity.PaymentEntity;
import com.payment.sapi.service.impl.PaymentServiceImpl;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private AuditService auditService;
    
    @InjectMocks
    private PaymentServiceImpl paymentService;
    
    @BeforeEach
    void setUp() {
        // Reset all mocks and set up common test data
    }

    @Test
    void testProcessPayment_Success() {
        // Create a valid payment request
        PaymentRequest request = createValidPaymentRequest();
        String clientId = "test-client";
        
        // Mock repository to return false for existsByClientIdAndReference
        when(paymentRepository.existsByClientIdAndReference(eq(clientId), eq(request.getReference())))
            .thenReturn(false);
        
        // Mock repository save method to return entities with appropriate status
        // First call returns PROCESSING status
        PaymentEntity processingEntity = PaymentEntity.builder()
                .paymentId("test-payment-id")
                .clientId(clientId)
                .status("PROCESSING")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .reference(request.getReference())
                .description(request.getDescription())
                .createdAt(new Date())
                .build();
        
        // Second call returns COMPLETED status
        PaymentEntity completedEntity = PaymentEntity.builder()
                .paymentId("test-payment-id")
                .clientId(clientId)
                .status("COMPLETED")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .reference(request.getReference())
                .description(request.getDescription())
                .createdAt(new Date())
                .build();
        
        // Use answer to return different values on consecutive calls
        when(paymentRepository.save(any(PaymentEntity.class)))
            .thenReturn(processingEntity)
            .thenReturn(completedEntity);
        
        // Call paymentService.processPayment
        PaymentResponse response = paymentService.processPayment(request, clientId);
        
        // Verify the payment response has correct payment ID, status, and reference
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getReference()).isEqualTo(request.getReference());
        
        // Verify repository methods were called with correct parameters
        verify(paymentRepository).existsByClientIdAndReference(eq(clientId), eq(request.getReference()));
        verify(paymentRepository, times(2)).save(any(PaymentEntity.class));
        
        // Verify audit logging was performed
        verify(auditService).logSecurityEvent(eq("PAYMENT_INITIATED"), eq(clientId), anyString());
        verify(auditService).logSecurityEvent(eq("PAYMENT_COMPLETED"), eq(clientId), anyString());
    }

    @Test
    void testProcessPayment_DuplicateReference() {
        // Create a valid payment request
        PaymentRequest request = createValidPaymentRequest();
        String clientId = "test-client";
        
        // Mock repository to return true for existsByClientIdAndReference
        when(paymentRepository.existsByClientIdAndReference(eq(clientId), eq(request.getReference())))
            .thenReturn(true);
        
        // Expect PaymentProcessingException to be thrown
        assertThatThrownBy(() -> paymentService.processPayment(request, clientId))
                .isInstanceOf(PaymentProcessingException.class)
                .extracting("errorCode")
                .isEqualTo("DUPLICATE_REFERENCE");
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).existsByClientIdAndReference(eq(clientId), eq(request.getReference()));
        
        // Verify save was never called
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    void testProcessPayment_InvalidRequest() {
        // Create an invalid payment request (null amount)
        PaymentRequest request = PaymentRequest.builder()
                .amount(null)
                .currency("USD")
                .reference("INV-12345")
                .description("Test payment")
                .build();
        String clientId = "test-client";
        
        // Expect PaymentProcessingException to be thrown
        assertThatThrownBy(() -> paymentService.processPayment(request, clientId))
                .isInstanceOf(PaymentProcessingException.class)
                .extracting("errorCode")
                .isEqualTo("VALIDATION_ERROR");
        
        // Verify repository methods were not called
        verify(paymentRepository, never()).existsByClientIdAndReference(anyString(), anyString());
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    void testGetPaymentStatus_Success() {
        // Create a payment entity with known payment ID and client ID
        String paymentId = "payment-123";
        String clientId = "test-client";
        PaymentEntity entity = createPaymentEntity(paymentId, clientId, "COMPLETED", "INV-12345");
        
        // Mock repository to return the payment entity for findByPaymentId
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(entity));
        
        // Call paymentService.getPaymentStatus
        Optional<PaymentResponse> responseOpt = paymentService.getPaymentStatus(paymentId, clientId);
        
        // Verify the returned Optional contains the expected payment response
        assertThat(responseOpt).isPresent();
        PaymentResponse response = responseOpt.get();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
        
        // Verify audit logging
        verify(auditService).logAuthorizationDecision(eq(clientId), anyString(), eq("read"), eq(true));
    }

    @Test
    void testGetPaymentStatus_NotFound() {
        // Mock repository to return empty Optional for findByPaymentId
        String paymentId = "non-existent";
        String clientId = "test-client";
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        
        // Call paymentService.getPaymentStatus
        Optional<PaymentResponse> responseOpt = paymentService.getPaymentStatus(paymentId, clientId);
        
        // Verify the returned Optional is empty
        assertThat(responseOpt).isEmpty();
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
        
        // Verify no audit logging occurred
        verify(auditService, never()).logAuthorizationDecision(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void testGetPaymentStatus_UnauthorizedClient() {
        // Create a payment entity with known payment ID and client ID
        String paymentId = "payment-123";
        String actualClientId = "actual-client";
        String requestingClientId = "unauthorized-client";
        PaymentEntity entity = createPaymentEntity(paymentId, actualClientId, "COMPLETED", "INV-12345");
        
        // Mock repository to return the payment entity for findByPaymentId
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(entity));
        
        // Call paymentService.getPaymentStatus with a different client ID
        Optional<PaymentResponse> responseOpt = paymentService.getPaymentStatus(paymentId, requestingClientId);
        
        // Verify the returned Optional is empty
        assertThat(responseOpt).isEmpty();
        
        // Verify audit logging was performed for security event
        verify(auditService).logAuthorizationDecision(eq(requestingClientId), anyString(), eq("read"), eq(false));
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
    }

    @Test
    void testGetPaymentsByClient() {
        // Create a list of payment entities for a client ID
        String clientId = "test-client";
        List<PaymentEntity> entities = new ArrayList<>();
        entities.add(createPaymentEntity("payment-1", clientId, "COMPLETED", "INV-1"));
        entities.add(createPaymentEntity("payment-2", clientId, "PROCESSING", "INV-2"));
        
        // Mock repository to return the list for findByClientId
        when(paymentRepository.findByClientId(clientId)).thenReturn(entities);
        
        // Call paymentService.getPaymentsByClient
        List<PaymentResponse> responses = paymentService.getPaymentsByClient(clientId);
        
        // Verify the returned list contains the expected payment responses
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getPaymentId()).isEqualTo("payment-1");
        assertThat(responses.get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(responses.get(1).getPaymentId()).isEqualTo("payment-2");
        assertThat(responses.get(1).getStatus()).isEqualTo("PROCESSING");
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByClientId(clientId);
        
        // Verify audit logging
        verify(auditService).logAuthorizationDecision(eq(clientId), eq("payments"), eq("list"), eq(true));
    }

    @Test
    void testGetPaymentByReference_Success() {
        // Create a payment entity with known reference and client ID
        String reference = "INV-12345";
        String clientId = "test-client";
        PaymentEntity entity = createPaymentEntity("payment-123", clientId, "COMPLETED", reference);
        
        // Mock repository to return the payment entity for findByClientIdAndReference
        when(paymentRepository.findByClientIdAndReference(clientId, reference)).thenReturn(Optional.of(entity));
        
        // Call paymentService.getPaymentByReference
        Optional<PaymentResponse> responseOpt = paymentService.getPaymentByReference(reference, clientId);
        
        // Verify the returned Optional contains the expected payment response
        assertThat(responseOpt).isPresent();
        PaymentResponse response = responseOpt.get();
        assertThat(response.getPaymentId()).isEqualTo("payment-123");
        assertThat(response.getReference()).isEqualTo(reference);
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByClientIdAndReference(clientId, reference);
        
        // Verify audit logging
        verify(auditService).logAuthorizationDecision(eq(clientId), anyString(), eq("read"), eq(true));
    }

    @Test
    void testGetPaymentByReference_NotFound() {
        // Mock repository to return empty Optional for findByClientIdAndReference
        String reference = "non-existent";
        String clientId = "test-client";
        when(paymentRepository.findByClientIdAndReference(clientId, reference)).thenReturn(Optional.empty());
        
        // Call paymentService.getPaymentByReference
        Optional<PaymentResponse> responseOpt = paymentService.getPaymentByReference(reference, clientId);
        
        // Verify the returned Optional is empty
        assertThat(responseOpt).isEmpty();
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByClientIdAndReference(clientId, reference);
    }

    @Test
    void testUpdatePaymentStatus_Success() {
        // Create a payment entity with known payment ID and client ID
        String paymentId = "payment-123";
        String clientId = "test-client";
        String newStatus = "COMPLETED";
        PaymentEntity entity = createPaymentEntity(paymentId, clientId, "PROCESSING", "INV-12345");
        
        // Updated entity after status change
        PaymentEntity updatedEntity = createPaymentEntity(paymentId, clientId, newStatus, "INV-12345");
        
        // Mock repository to return the payment entity for findByPaymentId
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(entity));
        when(paymentRepository.save(any(PaymentEntity.class))).thenReturn(updatedEntity);
        
        // Call paymentService.updatePaymentStatus with new status
        PaymentResponse response = paymentService.updatePaymentStatus(paymentId, newStatus, clientId);
        
        // Verify the returned payment response has the updated status
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo(newStatus);
        
        // Verify repository methods were called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
        verify(paymentRepository).save(any(PaymentEntity.class));
        
        // Verify audit logging was performed
        verify(auditService).logAuthorizationDecision(eq(clientId), anyString(), eq("update"), eq(true));
        verify(auditService).logSecurityEvent(eq("PAYMENT_STATUS_UPDATED"), eq(clientId), anyString());
    }

    @Test
    void testUpdatePaymentStatus_NotFound() {
        // Mock repository to return empty Optional for findByPaymentId
        String paymentId = "non-existent";
        String clientId = "test-client";
        String newStatus = "COMPLETED";
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        
        // Expect PaymentProcessingException to be thrown
        assertThatThrownBy(() -> paymentService.updatePaymentStatus(paymentId, newStatus, clientId))
                .isInstanceOf(PaymentProcessingException.class)
                .extracting("errorCode")
                .isEqualTo("PAYMENT_NOT_FOUND");
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
        
        // Verify save was not called
        verify(paymentRepository, never()).save(any(PaymentEntity.class));
    }

    @Test
    void testUpdatePaymentStatus_UnauthorizedClient() {
        // Create a payment entity with known payment ID and client ID
        String paymentId = "payment-123";
        String actualClientId = "actual-client";
        String requestingClientId = "unauthorized-client";
        String newStatus = "COMPLETED";
        PaymentEntity entity = createPaymentEntity(paymentId, actualClientId, "PROCESSING", "INV-12345");
        
        // Mock repository to return the payment entity for findByPaymentId
        when(paymentRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(entity));
        
        // Expect PaymentProcessingException to be thrown
        assertThatThrownBy(() -> paymentService.updatePaymentStatus(paymentId, newStatus, requestingClientId))
                .isInstanceOf(PaymentProcessingException.class)
                .extracting("errorCode")
                .isEqualTo("UNAUTHORIZED");
        
        // Verify audit logging was performed for security event
        verify(auditService).logAuthorizationDecision(eq(requestingClientId), anyString(), eq("update"), eq(false));
        
        // Verify repository method was called with correct parameters
        verify(paymentRepository).findByPaymentId(paymentId);
    }

    /**
     * Helper method to create a valid payment request for tests
     */
    private PaymentRequest createValidPaymentRequest() {
        return PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("INV-12345")
                .description("Test payment")
                .build();
    }

    /**
     * Helper method to create a payment entity for tests
     */
    private PaymentEntity createPaymentEntity(String paymentId, String clientId, String status, String reference) {
        return PaymentEntity.builder()
                .paymentId(paymentId)
                .clientId(clientId)
                .status(status)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference(reference)
                .description("Test payment")
                .createdAt(new Date())
                .build();
    }
}