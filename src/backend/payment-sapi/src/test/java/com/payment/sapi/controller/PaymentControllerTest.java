package com.payment.sapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.sapi.model.PaymentRequest;
import com.payment.sapi.model.PaymentResponse;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.sapi.service.PaymentService;
import com.payment.sapi.service.TokenValidationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private TokenValidationService tokenValidationService;

    @InjectMocks
    private PaymentController paymentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TEST_TOKEN = "test_token";
    private static final String TEST_CLIENT_ID = "test_client_id";
    private static final String AUTHORIZATION_HEADER = "Bearer " + TEST_TOKEN;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController).build();
        
        // Configure token parsing to return a Token with the test client ID as subject
        Token token = Token.builder()
                .clientId(TEST_CLIENT_ID)
                .build();
        when(tokenValidationService.parseToken(TEST_TOKEN)).thenReturn(token);
    }

    @Test
    void testProcessPayment_Success() throws Exception {
        // Arrange
        PaymentRequest request = createTestPaymentRequest();
        PaymentResponse response = createTestPaymentResponse();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("process_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.processPayment(any(PaymentRequest.class), eq(TEST_CLIENT_ID)))
            .thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.payment_id").value(response.getPaymentId()))
            .andExpect(jsonPath("$.status").value(response.getStatus()))
            .andExpect(jsonPath("$.reference").value(response.getReference()));
        
        verify(paymentService, times(1)).processPayment(any(PaymentRequest.class), eq(TEST_CLIENT_ID));
    }

    @Test
    void testProcessPayment_InvalidToken() throws Exception {
        // Arrange
        PaymentRequest request = createTestPaymentRequest();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("process_payment")))
            .thenReturn(ValidationResult.invalid("Invalid token"));
        
        // Act & Assert
        mockMvc.perform(post("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        
        verify(paymentService, never()).processPayment(any(PaymentRequest.class), anyString());
    }

    @Test
    void testProcessPayment_ExpiredToken() throws Exception {
        // Arrange
        PaymentRequest request = createTestPaymentRequest();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("process_payment")))
            .thenReturn(ValidationResult.expired("Token has expired"));
        
        // Act & Assert
        mockMvc.perform(post("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
        
        verify(paymentService, never()).processPayment(any(PaymentRequest.class), anyString());
    }

    @Test
    void testProcessPayment_InsufficientPermissions() throws Exception {
        // Arrange
        PaymentRequest request = createTestPaymentRequest();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("process_payment")))
            .thenReturn(ValidationResult.forbidden("Insufficient permissions"));
        
        // Act & Assert
        mockMvc.perform(post("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
        
        verify(paymentService, never()).processPayment(any(PaymentRequest.class), anyString());
    }

    @Test
    void testGetPaymentStatus_Success() throws Exception {
        // Arrange
        String paymentId = "pmt-123456";
        PaymentResponse response = createTestPaymentResponse();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.getPaymentStatus(eq(paymentId), eq(TEST_CLIENT_ID)))
            .thenReturn(Optional.of(response));
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/{paymentId}", paymentId)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.payment_id").value(response.getPaymentId()))
            .andExpect(jsonPath("$.status").value(response.getStatus()))
            .andExpect(jsonPath("$.reference").value(response.getReference()));
        
        verify(paymentService, times(1)).getPaymentStatus(eq(paymentId), eq(TEST_CLIENT_ID));
    }

    @Test
    void testGetPaymentStatus_NotFound() throws Exception {
        // Arrange
        String paymentId = "pmt-non-existent";
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.getPaymentStatus(eq(paymentId), eq(TEST_CLIENT_ID)))
            .thenReturn(Optional.empty());
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/{paymentId}", paymentId)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isNotFound());
        
        verify(paymentService, times(1)).getPaymentStatus(eq(paymentId), eq(TEST_CLIENT_ID));
    }

    @Test
    void testGetPaymentStatus_InvalidToken() throws Exception {
        // Arrange
        String paymentId = "pmt-123456";
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.invalid("Invalid token"));
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/{paymentId}", paymentId)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isUnauthorized());
        
        verify(paymentService, never()).getPaymentStatus(anyString(), anyString());
    }

    @Test
    void testGetPaymentsByClient_Success() throws Exception {
        // Arrange
        List<PaymentResponse> responses = new ArrayList<>();
        responses.add(createTestPaymentResponse());
        responses.add(createTestPaymentResponse());
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.getPaymentsByClient(eq(TEST_CLIENT_ID)))
            .thenReturn(responses);
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));
        
        verify(paymentService, times(1)).getPaymentsByClient(eq(TEST_CLIENT_ID));
    }

    @Test
    void testGetPaymentsByClient_InvalidToken() throws Exception {
        // Arrange
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.invalid("Invalid token"));
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments")
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isUnauthorized());
        
        verify(paymentService, never()).getPaymentsByClient(anyString());
    }

    @Test
    void testGetPaymentByReference_Success() throws Exception {
        // Arrange
        String reference = "INV-12345";
        PaymentResponse response = createTestPaymentResponse();
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.getPaymentByReference(eq(reference), eq(TEST_CLIENT_ID)))
            .thenReturn(Optional.of(response));
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/reference/{reference}", reference)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.payment_id").value(response.getPaymentId()))
            .andExpect(jsonPath("$.status").value(response.getStatus()))
            .andExpect(jsonPath("$.reference").value(response.getReference()));
        
        verify(paymentService, times(1)).getPaymentByReference(eq(reference), eq(TEST_CLIENT_ID));
    }

    @Test
    void testGetPaymentByReference_NotFound() throws Exception {
        // Arrange
        String reference = "INV-non-existent";
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.valid());
        when(paymentService.getPaymentByReference(eq(reference), eq(TEST_CLIENT_ID)))
            .thenReturn(Optional.empty());
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/reference/{reference}", reference)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isNotFound());
        
        verify(paymentService, times(1)).getPaymentByReference(eq(reference), eq(TEST_CLIENT_ID));
    }

    @Test
    void testGetPaymentByReference_InvalidToken() throws Exception {
        // Arrange
        String reference = "INV-12345";
        
        when(tokenValidationService.validateToken(eq(TEST_TOKEN), eq("view_payment")))
            .thenReturn(ValidationResult.invalid("Invalid token"));
        
        // Act & Assert
        mockMvc.perform(get("/internal/v1/payments/reference/{reference}", reference)
                .header("Authorization", AUTHORIZATION_HEADER))
            .andExpect(status().isUnauthorized());
        
        verify(paymentService, never()).getPaymentByReference(anyString(), anyString());
    }

    private PaymentRequest createTestPaymentRequest() {
        return PaymentRequest.builder()
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("INV-12345")
                .description("Test payment")
                .build();
    }

    private PaymentResponse createTestPaymentResponse() {
        return PaymentResponse.builder()
                .paymentId("pmt-123456")
                .status("PROCESSING")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .reference("INV-12345")
                .description("Test payment")
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();
    }
}