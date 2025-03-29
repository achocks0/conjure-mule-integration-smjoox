package com.payment.rotation.service;

import com.payment.rotation.model.RotationRequest;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;
import com.payment.rotation.service.impl.RotationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RotationServiceTest {

    @Mock
    private ConjurService conjurService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RotationServiceImpl rotationService;

    // Test data
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_ROTATION_ID = "test-rotation-id";
    private static final String TEST_REASON = "Test rotation reason";
    private static final String TEST_OLD_VERSION = "v1";
    private static final String TEST_NEW_VERSION = "v2";
    private static final int TEST_TRANSITION_PERIOD = 60;

    @BeforeEach
    void setUp() {
        // Setup common mocks for rotation service testing
        // Mock database operations in RotationServiceImpl
        lenient().doAnswer(invocation -> {
            String rotationId = invocation.getArgument(0);
            if (TEST_ROTATION_ID.equals(rotationId)) {
                Map<String, Object> data = new HashMap<>();
                data.put("rotationId", TEST_ROTATION_ID);
                data.put("clientId", TEST_CLIENT_ID);
                data.put("currentState", RotationState.INITIATED);
                data.put("targetState", RotationState.NEW_ACTIVE);
                data.put("oldVersion", TEST_OLD_VERSION);
                data.put("newVersion", TEST_NEW_VERSION);
                data.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
                data.put("startedAt", new Date());
                data.put("completedAt", null);
                data.put("status", "Rotation initiated");
                data.put("message", "Credential rotation initiated successfully");
                data.put("success", true);
                return data;
            }
            return null;
        }).when(rotationService).getRotationDataById(anyString());
        
        lenient().doReturn(Collections.emptyList()).when(rotationService).getRotationDataByClientId(anyString());
        lenient().doReturn(Collections.emptyList()).when(rotationService).getActiveRotationsData();
        lenient().doNothing().when(rotationService).updateRotationData(any());
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(conjurService, notificationService);
    }

    @Test
    @DisplayName("Should successfully initiate rotation when valid request is provided")
    void testInitiateRotation_Success() {
        // Create a rotation request
        RotationRequest request = RotationRequest.builder()
                .clientId(TEST_CLIENT_ID)
                .reason(TEST_REASON)
                .transitionPeriodMinutes(TEST_TRANSITION_PERIOD)
                .build();

        // Mock ConjurService to return a new credential
        Map<String, String> newCredential = createTestCredentialMap();
        when(conjurService.generateNewCredential(TEST_CLIENT_ID)).thenReturn(newCredential);
        
        // Mock storing the new credential version
        when(conjurService.storeNewCredentialVersion(eq(TEST_CLIENT_ID), eq(newCredential), anyString()))
                .thenReturn(true);
        
        // Mock active credential versions
        Map<String, Map<String, String>> activeVersions = new HashMap<>();
        activeVersions.put(TEST_OLD_VERSION, createTestCredentialMap());
        when(conjurService.getActiveCredentialVersions(TEST_CLIENT_ID)).thenReturn(activeVersions);
        
        // Mock configuration of credential transition
        when(conjurService.configureCredentialTransition(
                eq(TEST_CLIENT_ID), eq(TEST_OLD_VERSION), anyString(), eq(TEST_TRANSITION_PERIOD)))
                .thenReturn(true);
        
        // Override the getRotationsByClientId to return empty list (no active rotations)
        doReturn(Collections.emptyList()).when(rotationService).getRotationsByClientId(TEST_CLIENT_ID);
        
        // Call the service method
        RotationResponse response = rotationService.initiateRotation(request);
        
        // Verify the response
        assertNotNull(response);
        assertEquals(TEST_CLIENT_ID, response.getClientId());
        assertEquals(RotationState.INITIATED, response.getCurrentState());
        assertEquals(RotationState.NEW_ACTIVE, response.getTargetState());
        assertEquals(TEST_OLD_VERSION, response.getOldVersion());
        assertNotNull(response.getNewVersion());
        assertEquals(TEST_TRANSITION_PERIOD, response.getTransitionPeriodMinutes());
        assertNotNull(response.getStartedAt());
        assertNull(response.getCompletedAt());
        assertTrue(response.getSuccess());
        
        // Verify ConjurService methods were called
        verify(conjurService).generateNewCredential(TEST_CLIENT_ID);
        verify(conjurService).storeNewCredentialVersion(eq(TEST_CLIENT_ID), eq(newCredential), anyString());
        verify(conjurService).configureCredentialTransition(
                eq(TEST_CLIENT_ID), eq(TEST_OLD_VERSION), anyString(), eq(TEST_TRANSITION_PERIOD));
        
        // Verify NotificationService was called
        verify(notificationService).sendRotationStartedNotification(any(RotationResponse.class));
    }

    @Test
    @DisplayName("Should return error when active rotation exists and force is false")
    void testInitiateRotation_ExistingActiveRotation() {
        // Create a rotation request with forceRotation set to false
        RotationRequest request = RotationRequest.builder()
                .clientId(TEST_CLIENT_ID)
                .reason(TEST_REASON)
                .transitionPeriodMinutes(TEST_TRANSITION_PERIOD)
                .forceRotation(false)
                .build();
        
        // Mock getRotationsByClientId to return an active rotation
        List<RotationResponse> activeRotations = new ArrayList<>();
        activeRotations.add(createTestRotationResponse(RotationState.DUAL_ACTIVE));
        doReturn(activeRotations).when(rotationService).getRotationsByClientId(TEST_CLIENT_ID);
        
        // Call the service method
        RotationResponse response = rotationService.initiateRotation(request);
        
        // Verify the response indicates failure
        assertNotNull(response);
        assertEquals(TEST_CLIENT_ID, response.getClientId());
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Active rotation already exists"));
        
        // Verify that no ConjurService methods were called
        verifyNoInteractions(conjurService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should initiate new rotation when force is true despite existing active rotation")
    void testInitiateRotation_ForceRotation() {
        // Create a rotation request with forceRotation set to true
        RotationRequest request = RotationRequest.builder()
                .clientId(TEST_CLIENT_ID)
                .reason(TEST_REASON)
                .transitionPeriodMinutes(TEST_TRANSITION_PERIOD)
                .forceRotation(true)
                .build();
        
        // Mock getRotationsByClientId to return an active rotation
        List<RotationResponse> activeRotations = new ArrayList<>();
        activeRotations.add(createTestRotationResponse(RotationState.DUAL_ACTIVE));
        doReturn(activeRotations).when(rotationService).getRotationsByClientId(TEST_CLIENT_ID);
        
        // Mock other necessary method calls
        Map<String, String> newCredential = createTestCredentialMap();
        when(conjurService.generateNewCredential(TEST_CLIENT_ID)).thenReturn(newCredential);
        
        when(conjurService.storeNewCredentialVersion(eq(TEST_CLIENT_ID), eq(newCredential), anyString()))
                .thenReturn(true);
        
        Map<String, Map<String, String>> activeVersions = new HashMap<>();
        activeVersions.put(TEST_OLD_VERSION, createTestCredentialMap());
        when(conjurService.getActiveCredentialVersions(TEST_CLIENT_ID)).thenReturn(activeVersions);
        
        when(conjurService.configureCredentialTransition(
                eq(TEST_CLIENT_ID), eq(TEST_OLD_VERSION), anyString(), eq(TEST_TRANSITION_PERIOD)))
                .thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.initiateRotation(request);
        
        // Verify the response
        assertNotNull(response);
        assertEquals(TEST_CLIENT_ID, response.getClientId());
        assertEquals(RotationState.INITIATED, response.getCurrentState());
        assertTrue(response.getSuccess());
        
        // Verify ConjurService methods were called
        verify(conjurService).generateNewCredential(TEST_CLIENT_ID);
        verify(conjurService).storeNewCredentialVersion(eq(TEST_CLIENT_ID), eq(newCredential), anyString());
        verify(conjurService).configureCredentialTransition(
                eq(TEST_CLIENT_ID), eq(TEST_OLD_VERSION), anyString(), eq(TEST_TRANSITION_PERIOD));
        
        // Verify NotificationService was called
        verify(notificationService).sendRotationStartedNotification(any(RotationResponse.class));
    }

    @Test
    @DisplayName("Should return rotation status when rotation ID exists")
    void testGetRotationStatus_Found() {
        // Test data is set up in setUp() method
        
        // Call the service method
        Optional<RotationResponse> optionalResponse = rotationService.getRotationStatus(TEST_ROTATION_ID);
        
        // Verify the returned Optional contains a rotation response with expected values
        assertTrue(optionalResponse.isPresent());
        RotationResponse response = optionalResponse.get();
        assertEquals(TEST_ROTATION_ID, response.getRotationId());
        assertEquals(TEST_CLIENT_ID, response.getClientId());
        assertEquals(RotationState.INITIATED, response.getCurrentState());
    }

    @Test
    @DisplayName("Should return empty Optional when rotation ID doesn't exist")
    void testGetRotationStatus_NotFound() {
        // Call the service method with a non-existent rotation ID
        Optional<RotationResponse> optionalResponse = rotationService.getRotationStatus("non-existent-id");
        
        // Verify the returned Optional is empty
        assertFalse(optionalResponse.isPresent());
    }

    @Test
    @DisplayName("Should return all rotations for a client ID")
    void testGetRotationsByClientId() {
        // Mock database to return a list of rotation records for the test client ID
        List<Map<String, Object>> rotationsData = new ArrayList<>();
        
        Map<String, Object> rotation1 = new HashMap<>();
        rotation1.put("rotationId", TEST_ROTATION_ID + "-1");
        rotation1.put("clientId", TEST_CLIENT_ID);
        rotation1.put("currentState", RotationState.NEW_ACTIVE);
        rotation1.put("targetState", RotationState.NEW_ACTIVE);
        rotation1.put("oldVersion", TEST_OLD_VERSION);
        rotation1.put("newVersion", TEST_NEW_VERSION);
        rotation1.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotation1.put("startedAt", new Date());
        rotation1.put("completedAt", new Date());
        rotation1.put("status", "Rotation completed");
        rotation1.put("message", "Credential rotation completed successfully");
        rotation1.put("success", true);
        
        Map<String, Object> rotation2 = new HashMap<>();
        rotation2.put("rotationId", TEST_ROTATION_ID + "-2");
        rotation2.put("clientId", TEST_CLIENT_ID);
        rotation2.put("currentState", RotationState.FAILED);
        rotation2.put("targetState", RotationState.NEW_ACTIVE);
        rotation2.put("oldVersion", TEST_OLD_VERSION);
        rotation2.put("newVersion", TEST_NEW_VERSION);
        rotation2.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotation2.put("startedAt", new Date());
        rotation2.put("completedAt", new Date());
        rotation2.put("status", "Rotation failed");
        rotation2.put("message", "Error during rotation");
        rotation2.put("success", false);
        
        rotationsData.add(rotation1);
        rotationsData.add(rotation2);
        
        doReturn(rotationsData).when(rotationService).getRotationDataByClientId(TEST_CLIENT_ID);
        
        // Call the service method
        List<RotationResponse> rotations = rotationService.getRotationsByClientId(TEST_CLIENT_ID);
        
        // Verify the returned list contains rotation responses with expected values
        assertNotNull(rotations);
        assertEquals(2, rotations.size());
        
        // Verify details of the returned rotations
        RotationResponse response1 = rotations.get(0);
        assertEquals(TEST_ROTATION_ID + "-1", response1.getRotationId());
        assertEquals(TEST_CLIENT_ID, response1.getClientId());
        assertEquals(RotationState.NEW_ACTIVE, response1.getCurrentState());
        
        RotationResponse response2 = rotations.get(1);
        assertEquals(TEST_ROTATION_ID + "-2", response2.getRotationId());
        assertEquals(TEST_CLIENT_ID, response2.getClientId());
        assertEquals(RotationState.FAILED, response2.getCurrentState());
    }

    @Test
    @DisplayName("Should advance rotation from INITIATED to DUAL_ACTIVE state")
    void testAdvanceRotation_ToDualActive() {
        // Mock database to return a rotation record in INITIATED state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.INITIATED);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Rotation initiated");
        rotationData.put("message", "Credential rotation initiated successfully");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Mock ConjurService to return true for configuring credential transition
        when(conjurService.configureCredentialTransition(
                eq(TEST_CLIENT_ID), eq(TEST_OLD_VERSION), eq(TEST_NEW_VERSION), eq(TEST_TRANSITION_PERIOD)))
                .thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.advanceRotation(TEST_ROTATION_ID, RotationState.DUAL_ACTIVE);
        
        // Verify the response contains DUAL_ACTIVE as current state
        assertNotNull(response);
        assertEquals(RotationState.DUAL_ACTIVE, response.getCurrentState());
        
        // Verify ConjurService was called with correct parameters
        verify(conjurService).configureCredentialTransition(
                TEST_CLIENT_ID, TEST_OLD_VERSION, TEST_NEW_VERSION, TEST_TRANSITION_PERIOD);
        
        // Verify NotificationService was called with correct parameters
        verify(notificationService).sendRotationStateChangedNotification(
                any(RotationResponse.class), eq(RotationState.INITIATED));
    }

    @Test
    @DisplayName("Should advance rotation from DUAL_ACTIVE to OLD_DEPRECATED state")
    void testAdvanceRotation_ToOldDeprecated() {
        // Mock database to return a rotation record in DUAL_ACTIVE state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.DUAL_ACTIVE);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Dual credentials active");
        rotationData.put("message", "Both old and new credentials are active");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Mock ConjurService to return true for disabling credential version
        when(conjurService.disableCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION)).thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.advanceRotation(TEST_ROTATION_ID, RotationState.OLD_DEPRECATED);
        
        // Verify the response contains OLD_DEPRECATED as current state
        assertNotNull(response);
        assertEquals(RotationState.OLD_DEPRECATED, response.getCurrentState());
        
        // Verify ConjurService was called with correct parameters
        verify(conjurService).disableCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION);
        
        // Verify NotificationService was called with correct parameters
        verify(notificationService).sendRotationStateChangedNotification(
                any(RotationResponse.class), eq(RotationState.DUAL_ACTIVE));
    }

    @Test
    @DisplayName("Should advance rotation from OLD_DEPRECATED to NEW_ACTIVE state")
    void testAdvanceRotation_ToNewActive() {
        // Mock database to return a rotation record in OLD_DEPRECATED state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.OLD_DEPRECATED);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Old credentials deprecated");
        rotationData.put("message", "Old credentials have been deprecated");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Mock ConjurService to return true for removing credential version
        when(conjurService.removeCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION)).thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.advanceRotation(TEST_ROTATION_ID, RotationState.NEW_ACTIVE);
        
        // Verify the response contains NEW_ACTIVE as current state
        assertNotNull(response);
        assertEquals(RotationState.NEW_ACTIVE, response.getCurrentState());
        
        // Verify ConjurService was called with correct parameters
        verify(conjurService).removeCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION);
        
        // Verify NotificationService was called with correct parameters
        verify(notificationService).sendRotationCompletedNotification(any(RotationResponse.class));
    }

    @Test
    @DisplayName("Should throw exception for invalid state transition")
    void testAdvanceRotation_InvalidTransition() {
        // Mock database to return a rotation record in INITIATED state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.INITIATED);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Rotation initiated");
        rotationData.put("message", "Credential rotation initiated successfully");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Call the service method with an invalid transition (INITIATED -> NEW_ACTIVE)
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            rotationService.advanceRotation(TEST_ROTATION_ID, RotationState.NEW_ACTIVE);
        });
        
        // Verify that an IllegalArgumentException is thrown with appropriate message
        assertTrue(exception.getMessage().contains("Invalid state transition"));
        
        // Verify that no ConjurService or NotificationService methods were called
        verifyNoInteractions(conjurService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should successfully complete rotation from OLD_DEPRECATED state")
    void testCompleteRotation_Success() {
        // Mock database to return a rotation record in OLD_DEPRECATED state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.OLD_DEPRECATED);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Old credentials deprecated");
        rotationData.put("message", "Old credentials have been deprecated");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Mock ConjurService to return true for removing credential version
        when(conjurService.removeCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION)).thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.completeRotation(TEST_ROTATION_ID);
        
        // Verify the response contains NEW_ACTIVE as current state and success flag is true
        assertNotNull(response);
        assertEquals(RotationState.NEW_ACTIVE, response.getCurrentState());
        assertTrue(response.getSuccess());
        
        // Verify ConjurService was called with correct parameters
        verify(conjurService).removeCredentialVersion(TEST_CLIENT_ID, TEST_OLD_VERSION);
        
        // Verify NotificationService was called with correct parameters
        verify(notificationService).sendRotationCompletedNotification(any(RotationResponse.class));
    }

    @Test
    @DisplayName("Should throw exception when completing rotation from invalid state")
    void testCompleteRotation_InvalidState() {
        // Mock database to return a rotation record in INITIATED state (not OLD_DEPRECATED)
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.INITIATED);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Rotation initiated");
        rotationData.put("message", "Credential rotation initiated successfully");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Call the service method
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            rotationService.completeRotation(TEST_ROTATION_ID);
        });
        
        // Verify that an exception is thrown with appropriate message
        assertTrue(exception.getMessage().contains("Cannot complete rotation in state"));
        
        // Verify that no ConjurService or NotificationService methods were called
        verifyNoInteractions(conjurService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should successfully cancel rotation in progress")
    void testCancelRotation_Success() {
        // Mock database to return a rotation record in DUAL_ACTIVE state
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.DUAL_ACTIVE);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", null);
        rotationData.put("status", "Dual credentials active");
        rotationData.put("message", "Both old and new credentials are active");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Mock ConjurService to return true for removing credential version
        when(conjurService.removeCredentialVersion(TEST_CLIENT_ID, TEST_NEW_VERSION)).thenReturn(true);
        
        // Call the service method
        RotationResponse response = rotationService.cancelRotation(TEST_ROTATION_ID, TEST_REASON);
        
        // Verify the response contains FAILED as current state and success flag is false
        assertNotNull(response);
        assertEquals(RotationState.FAILED, response.getCurrentState());
        assertFalse(response.getSuccess());
        
        // Verify ConjurService was called with correct parameters
        verify(conjurService).removeCredentialVersion(TEST_CLIENT_ID, TEST_NEW_VERSION);
        
        // Verify NotificationService was called with correct parameters
        verify(notificationService).sendRotationFailedNotification(any(RotationResponse.class), eq(TEST_REASON));
    }

    @Test
    @DisplayName("Should throw exception when cancelling rotation in terminal state")
    void testCancelRotation_TerminalState() {
        // Mock database to return a rotation record in NEW_ACTIVE state (terminal state)
        Map<String, Object> rotationData = new HashMap<>();
        rotationData.put("rotationId", TEST_ROTATION_ID);
        rotationData.put("clientId", TEST_CLIENT_ID);
        rotationData.put("currentState", RotationState.NEW_ACTIVE);
        rotationData.put("targetState", RotationState.NEW_ACTIVE);
        rotationData.put("oldVersion", TEST_OLD_VERSION);
        rotationData.put("newVersion", TEST_NEW_VERSION);
        rotationData.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotationData.put("startedAt", new Date());
        rotationData.put("completedAt", new Date());
        rotationData.put("status", "Rotation completed");
        rotationData.put("message", "Credential rotation completed successfully");
        rotationData.put("success", true);
        
        doReturn(rotationData).when(rotationService).getRotationDataById(TEST_ROTATION_ID);
        
        // Call the service method
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            rotationService.cancelRotation(TEST_ROTATION_ID, TEST_REASON);
        });
        
        // Verify that an exception is thrown with appropriate message
        assertTrue(exception.getMessage().contains("Cannot cancel rotation in terminal state"));
        
        // Verify that no ConjurService or NotificationService methods were called
        verifyNoInteractions(conjurService);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Should check and advance rotations based on their state and timing")
    void testCheckRotationProgress() {
        // Mock getActiveRotations to return a list of active rotations
        List<RotationResponse> activeRotations = new ArrayList<>();
        
        // Add a DUAL_ACTIVE rotation with expired transition period
        RotationResponse dualActiveRotation = createTestRotationResponse(RotationState.DUAL_ACTIVE);
        // Set started time in the past to make transition period expired
        Date startedTime = new Date(System.currentTimeMillis() - (TEST_TRANSITION_PERIOD + 10) * 60 * 1000);
        dualActiveRotation.setStartedAt(startedTime);
        activeRotations.add(dualActiveRotation);
        
        // Add an OLD_DEPRECATED rotation where all services use new credential
        RotationResponse oldDeprecatedRotation = createTestRotationResponse(RotationState.OLD_DEPRECATED);
        activeRotations.add(oldDeprecatedRotation);
        
        doReturn(activeRotations).when(rotationService).getActiveRotations();
        
        // Mock getRotationDataById to return appropriate rotation data for each rotation
        Map<String, Object> dualActiveData = new HashMap<>();
        dualActiveData.put("rotationId", dualActiveRotation.getRotationId());
        dualActiveData.put("clientId", dualActiveRotation.getClientId());
        dualActiveData.put("currentState", RotationState.DUAL_ACTIVE);
        dualActiveData.put("targetState", dualActiveRotation.getTargetState());
        dualActiveData.put("oldVersion", dualActiveRotation.getOldVersion());
        dualActiveData.put("newVersion", dualActiveRotation.getNewVersion());
        dualActiveData.put("transitionPeriodMinutes", dualActiveRotation.getTransitionPeriodMinutes());
        dualActiveData.put("startedAt", dualActiveRotation.getStartedAt());
        dualActiveData.put("completedAt", null);
        dualActiveData.put("status", "Dual credentials active");
        dualActiveData.put("message", "Both old and new credentials are active");
        dualActiveData.put("success", true);
        
        Map<String, Object> oldDeprecatedData = new HashMap<>();
        oldDeprecatedData.put("rotationId", oldDeprecatedRotation.getRotationId());
        oldDeprecatedData.put("clientId", oldDeprecatedRotation.getClientId());
        oldDeprecatedData.put("currentState", RotationState.OLD_DEPRECATED);
        oldDeprecatedData.put("targetState", oldDeprecatedRotation.getTargetState());
        oldDeprecatedData.put("oldVersion", oldDeprecatedRotation.getOldVersion());
        oldDeprecatedData.put("newVersion", oldDeprecatedRotation.getNewVersion());
        oldDeprecatedData.put("transitionPeriodMinutes", oldDeprecatedRotation.getTransitionPeriodMinutes());
        oldDeprecatedData.put("startedAt", oldDeprecatedRotation.getStartedAt());
        oldDeprecatedData.put("completedAt", null);
        oldDeprecatedData.put("status", "Old credentials deprecated");
        oldDeprecatedData.put("message", "Old credentials have been deprecated");
        oldDeprecatedData.put("success", true);
        
        doReturn(dualActiveData)
            .when(rotationService).getRotationDataById(dualActiveRotation.getRotationId());
        doReturn(oldDeprecatedData)
            .when(rotationService).getRotationDataById(oldDeprecatedRotation.getRotationId());
        
        // Mock transition status for OLD_DEPRECATED rotation
        Map<String, Object> transitionStatus = new HashMap<>();
        transitionStatus.put("oldVersionInUse", false);
        Optional<Map<String, Object>> optTransitionStatus = Optional.of(transitionStatus);
        when(conjurService.getCredentialTransitionStatus(oldDeprecatedRotation.getClientId()))
                .thenReturn(optTransitionStatus);
        
        // Mock other necessary method calls
        when(conjurService.disableCredentialVersion(anyString(), anyString())).thenReturn(true);
        when(conjurService.removeCredentialVersion(anyString(), anyString())).thenReturn(true);
        
        // Call the service method
        List<RotationResponse> processedRotations = rotationService.checkRotationProgress();
        
        // Verify the result
        assertNotNull(processedRotations);
        
        // Verify ConjurService and NotificationService methods were called appropriately
        verify(conjurService).getCredentialTransitionStatus(anyString());
    }

    @Test
    @DisplayName("Should return all active rotations")
    void testGetActiveRotations() {
        // Mock database to return a list of active rotation records
        List<Map<String, Object>> activeRotationsData = new ArrayList<>();
        
        Map<String, Object> rotation1 = new HashMap<>();
        rotation1.put("rotationId", TEST_ROTATION_ID + "-1");
        rotation1.put("clientId", TEST_CLIENT_ID);
        rotation1.put("currentState", RotationState.INITIATED);
        rotation1.put("targetState", RotationState.NEW_ACTIVE);
        rotation1.put("oldVersion", TEST_OLD_VERSION);
        rotation1.put("newVersion", TEST_NEW_VERSION);
        rotation1.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotation1.put("startedAt", new Date());
        rotation1.put("completedAt", null);
        rotation1.put("status", "Rotation initiated");
        rotation1.put("message", "Credential rotation initiated successfully");
        rotation1.put("success", true);
        
        Map<String, Object> rotation2 = new HashMap<>();
        rotation2.put("rotationId", TEST_ROTATION_ID + "-2");
        rotation2.put("clientId", TEST_CLIENT_ID);
        rotation2.put("currentState", RotationState.DUAL_ACTIVE);
        rotation2.put("targetState", RotationState.NEW_ACTIVE);
        rotation2.put("oldVersion", TEST_OLD_VERSION);
        rotation2.put("newVersion", TEST_NEW_VERSION);
        rotation2.put("transitionPeriodMinutes", TEST_TRANSITION_PERIOD);
        rotation2.put("startedAt", new Date());
        rotation2.put("completedAt", null);
        rotation2.put("status", "Dual credentials active");
        rotation2.put("message", "Both old and new credentials are active");
        rotation2.put("success", true);
        
        activeRotationsData.add(rotation1);
        activeRotationsData.add(rotation2);
        
        doReturn(activeRotationsData).when(rotationService).getActiveRotationsData();
        
        // Call the service method
        List<RotationResponse> activeRotations = rotationService.getActiveRotations();
        
        // Verify the returned list contains rotation responses with expected values
        assertNotNull(activeRotations);
        assertEquals(2, activeRotations.size());
        
        // Verify that only non-terminal state rotations are included
        RotationResponse response1 = activeRotations.get(0);
        assertEquals(RotationState.INITIATED, response1.getCurrentState());
        
        RotationResponse response2 = activeRotations.get(1);
        assertEquals(RotationState.DUAL_ACTIVE, response2.getCurrentState());
    }

    // Helper methods
    
    private RotationResponse createTestRotationResponse(RotationState state) {
        return RotationResponse.builder()
                .rotationId(TEST_ROTATION_ID)
                .clientId(TEST_CLIENT_ID)
                .currentState(state)
                .targetState(RotationState.NEW_ACTIVE)
                .oldVersion(TEST_OLD_VERSION)
                .newVersion(TEST_NEW_VERSION)
                .transitionPeriodMinutes(TEST_TRANSITION_PERIOD)
                .startedAt(new Date())
                .completedAt(state == RotationState.NEW_ACTIVE || state == RotationState.FAILED ? new Date() : null)
                .status("Test status")
                .message("Test message")
                .success(state != RotationState.FAILED)
                .build();
    }

    private Map<String, String> createTestCredentialMap() {
        Map<String, String> credentialMap = new HashMap<>();
        credentialMap.put("clientSecret", "test-secret");
        credentialMap.put("created", new Date().toString());
        credentialMap.put("metadata", "{\"description\":\"Test credential\"}");
        return credentialMap;
    }
}