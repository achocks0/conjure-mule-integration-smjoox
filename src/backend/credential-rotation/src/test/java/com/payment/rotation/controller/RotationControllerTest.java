package com.payment.rotation.controller;

import com.payment.rotation.model.RotationRequest;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;
import com.payment.rotation.service.RotationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RotationControllerTest {

    @Mock
    private RotationService rotationService;

    @InjectMocks
    private RotationController rotationController;

    private String clientId;
    private String rotationId;
    private String reason;
    private RotationRequest rotationRequest;
    private RotationResponse rotationResponse;

    @BeforeEach
    void setUp() {
        // Initialize test data
        clientId = "test-client-123";
        rotationId = "rot-456789";
        reason = "Security policy compliance";
        
        // Create a sample rotation request
        rotationRequest = RotationRequest.builder()
                .clientId(clientId)
                .reason(reason)
                .transitionPeriodMinutes(30)
                .forceRotation(false)
                .build();
        
        // Create a sample rotation response
        rotationResponse = RotationResponse.builder()
                .rotationId(rotationId)
                .clientId(clientId)
                .currentState(RotationState.INITIATED)
                .startedAt(new Date())
                .success(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully initiate a credential rotation")
    void testInitiateRotation_Success() {
        // Arrange
        when(rotationService.initiateRotation(any(RotationRequest.class))).thenReturn(rotationResponse);
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.initiateRotation(rotationRequest);
        
        // Assert
        verify(rotationService).initiateRotation(rotationRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(rotationResponse);
        assertThat(response.getBody().getRotationId()).isEqualTo(rotationId);
        assertThat(response.getBody().getClientId()).isEqualTo(clientId);
        assertThat(response.getBody().getCurrentState()).isEqualTo(RotationState.INITIATED);
        assertThat(response.getBody().getSuccess()).isTrue();
    }

    @Test
    @DisplayName("Should handle errors during rotation initiation")
    void testInitiateRotation_Error() {
        // Arrange
        when(rotationService.initiateRotation(any(RotationRequest.class)))
                .thenThrow(new RuntimeException("Service unavailable"));
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.initiateRotation(rotationRequest);
        
        // Assert
        verify(rotationService).initiateRotation(rotationRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Service unavailable");
    }

    @Test
    @DisplayName("Should return rotation status when found")
    void testGetRotationStatus_Found() {
        // Arrange
        when(rotationService.getRotationStatus(rotationId)).thenReturn(Optional.of(rotationResponse));
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.getRotationStatus(rotationId);
        
        // Assert
        verify(rotationService).getRotationStatus(rotationId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rotationResponse);
    }

    @Test
    @DisplayName("Should return 404 when rotation not found")
    void testGetRotationStatus_NotFound() {
        // Arrange
        when(rotationService.getRotationStatus(rotationId)).thenReturn(Optional.empty());
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.getRotationStatus(rotationId);
        
        // Assert
        verify(rotationService).getRotationStatus(rotationId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("Should return all rotations for a client")
    void testGetRotationsByClientId() {
        // Arrange
        List<RotationResponse> rotations = new ArrayList<>();
        rotations.add(rotationResponse);
        
        when(rotationService.getRotationsByClientId(clientId)).thenReturn(rotations);
        
        // Act
        ResponseEntity<List<RotationResponse>> response = rotationController.getRotationsByClientId(clientId);
        
        // Assert
        verify(rotationService).getRotationsByClientId(clientId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(rotations);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getClientId()).isEqualTo(clientId);
    }

    @Test
    @DisplayName("Should return all active rotations")
    void testGetActiveRotations() {
        // Arrange
        List<RotationResponse> activeRotations = new ArrayList<>();
        activeRotations.add(rotationResponse);
        
        when(rotationService.getActiveRotations()).thenReturn(activeRotations);
        
        // Act
        ResponseEntity<List<RotationResponse>> response = rotationController.getActiveRotations();
        
        // Assert
        verify(rotationService).getActiveRotations();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(activeRotations);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("Should advance rotation to the next state")
    void testAdvanceRotation() {
        // Arrange
        RotationState targetState = RotationState.DUAL_ACTIVE;
        RotationResponse advancedResponse = RotationResponse.builder()
                .rotationId(rotationId)
                .clientId(clientId)
                .currentState(targetState)
                .success(true)
                .build();
        
        when(rotationService.advanceRotation(rotationId, targetState)).thenReturn(advancedResponse);
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.advanceRotation(rotationId, targetState);
        
        // Assert
        verify(rotationService).advanceRotation(rotationId, targetState);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(advancedResponse);
        assertThat(response.getBody().getCurrentState()).isEqualTo(targetState);
    }

    @Test
    @DisplayName("Should complete a rotation process")
    void testCompleteRotation() {
        // Arrange
        RotationResponse completedResponse = RotationResponse.builder()
                .rotationId(rotationId)
                .clientId(clientId)
                .currentState(RotationState.NEW_ACTIVE)
                .completedAt(new Date())
                .success(true)
                .build();
        
        when(rotationService.completeRotation(rotationId)).thenReturn(completedResponse);
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.completeRotation(rotationId);
        
        // Assert
        verify(rotationService).completeRotation(rotationId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(completedResponse);
        assertThat(response.getBody().getCurrentState()).isEqualTo(RotationState.NEW_ACTIVE);
        assertThat(response.getBody().getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should cancel a rotation process")
    void testCancelRotation() {
        // Arrange
        String cancellationReason = "Business decision";
        RotationResponse cancelledResponse = RotationResponse.builder()
                .rotationId(rotationId)
                .clientId(clientId)
                .currentState(RotationState.FAILED)
                .completedAt(new Date())
                .success(false)
                .message("Rotation cancelled: " + cancellationReason)
                .build();
        
        when(rotationService.cancelRotation(rotationId, cancellationReason)).thenReturn(cancelledResponse);
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.cancelRotation(rotationId, cancellationReason);
        
        // Assert
        verify(rotationService).cancelRotation(rotationId, cancellationReason);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cancelledResponse);
        assertThat(response.getBody().getCurrentState()).isEqualTo(RotationState.FAILED);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains(cancellationReason);
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException properly")
    void testHandleIllegalArgumentException() {
        // Arrange
        String errorMessage = "Invalid client ID format";
        when(rotationService.initiateRotation(any(RotationRequest.class)))
                .thenThrow(new IllegalArgumentException(errorMessage));
        
        // Act
        ResponseEntity<RotationResponse> response = rotationController.initiateRotation(rotationRequest);
        
        // Assert
        verify(rotationService).initiateRotation(rotationRequest);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo(errorMessage);
    }
}