package com.payment.rotation.service.impl;

import com.payment.rotation.model.RotationRequest;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;
import com.payment.rotation.service.ConjurService;
import com.payment.rotation.service.NotificationService;
import com.payment.rotation.service.RotationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the RotationService interface that manages the credential rotation process.
 * This service orchestrates the secure rotation of authentication credentials without service 
 * disruption, supporting multiple valid credential versions during transition periods.
 */
@Service
public class RotationServiceImpl implements RotationService {

    private static final Logger logger = LoggerFactory.getLogger(RotationServiceImpl.class);
    private static final int DEFAULT_TRANSITION_PERIOD_MINUTES = 60;

    @Autowired
    private ConjurService conjurService;
    
    @Autowired
    private NotificationService notificationService;
    
    /**
     * Initiates a new credential rotation process for the specified client.
     * 
     * @param request The rotation request containing parameters for the rotation process
     * @return Response containing the details of the initiated rotation process
     */
    @Override
    @Transactional
    public RotationResponse initiateRotation(RotationRequest request) {
        // Validate the rotation request parameters
        validateRotationRequest(request);
        
        String clientId = request.getClientId();
        logger.info("Initiating credential rotation for client ID: {}", clientId);
        
        // Check if there is already an active rotation for the client ID
        List<RotationResponse> activeRotations = getRotationsByClientId(clientId).stream()
                .filter(r -> r.getCurrentState() != RotationState.NEW_ACTIVE && r.getCurrentState() != RotationState.FAILED)
                .collect(java.util.stream.Collectors.toList());
        
        // If active rotation exists and forceRotation is false, return error response
        if (!activeRotations.isEmpty() && !Boolean.TRUE.equals(request.getForceRotation())) {
            String message = "Active rotation already exists for client ID: " + clientId;
            logger.warn(message);
            return RotationResponse.builder()
                    .clientId(clientId)
                    .success(false)
                    .message(message)
                    .build();
        }
        
        try {
            // Generate a new credential using ConjurService
            Map<String, String> newCredential = conjurService.generateNewCredential(clientId);
            
            // Create a unique version identifier for the new credential
            String newVersion = UUID.randomUUID().toString();
            
            // Store the new credential version in Conjur vault
            boolean stored = conjurService.storeNewCredentialVersion(clientId, newCredential, newVersion);
            if (!stored) {
                String message = "Failed to store new credential version for client ID: " + clientId;
                logger.error(message);
                return RotationResponse.builder()
                        .clientId(clientId)
                        .success(false)
                        .message(message)
                        .build();
            }
            
            // Get the current active credential version
            Map<String, Map<String, String>> activeVersions = conjurService.getActiveCredentialVersions(clientId);
            String oldVersion = activeVersions.keySet().stream()
                    .filter(v -> !v.equals(newVersion))
                    .findFirst()
                    .orElse(null);
            
            // Create a rotation record with INITIATED state
            String rotationId = UUID.randomUUID().toString();
            
            // Set transition period (use default if not specified)
            int transitionPeriodMinutes = request.getTransitionPeriodMinutes() != null ? 
                    request.getTransitionPeriodMinutes() : DEFAULT_TRANSITION_PERIOD_MINUTES;
            
            // Configure credential transition in Conjur vault
            boolean configured = conjurService.configureCredentialTransition(
                    clientId, oldVersion, newVersion, transitionPeriodMinutes);
            
            if (!configured) {
                // Cleanup if transition configuration fails
                conjurService.removeCredentialVersion(clientId, newVersion);
                String message = "Failed to configure credential transition for client ID: " + clientId;
                logger.error(message);
                return RotationResponse.builder()
                        .clientId(clientId)
                        .success(false)
                        .message(message)
                        .build();
            }
            
            // Build the rotation response
            RotationResponse response = RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId(clientId)
                    .currentState(RotationState.INITIATED)
                    .targetState(RotationState.NEW_ACTIVE)
                    .oldVersion(oldVersion)
                    .newVersion(newVersion)
                    .transitionPeriodMinutes(transitionPeriodMinutes)
                    .startedAt(new Date())
                    .status("Rotation initiated")
                    .message("Credential rotation initiated successfully")
                    .success(true)
                    .build();
            
            // Send rotation started notification via NotificationService
            notificationService.sendRotationStartedNotification(response);
            
            // Log the rotation initiation event
            logger.info("Credential rotation initiated for client ID: {}, rotation ID: {}", clientId, rotationId);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error initiating rotation for client ID {}: {}", clientId, e.getMessage(), e);
            return RotationResponse.builder()
                    .clientId(clientId)
                    .success(false)
                    .message("Error initiating rotation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Retrieves the current status of a credential rotation process.
     * 
     * @param rotationId The unique identifier of the rotation process
     * @return Optional containing the rotation status if found
     */
    @Override
    public Optional<RotationResponse> getRotationStatus(String rotationId) {
        // Validate the rotation ID
        if (rotationId == null || rotationId.isEmpty()) {
            throw new IllegalArgumentException("Rotation ID cannot be null or empty");
        }
        
        logger.debug("Retrieving rotation status for rotation ID: {}", rotationId);
        
        // Retrieve the rotation record from the database
        Map<String, Object> rotationData = getRotationDataById(rotationId);
        
        // If the rotation record is not found, return an empty Optional
        if (rotationData == null) {
            logger.debug("No rotation found for rotation ID: {}", rotationId);
            return Optional.empty();
        }
        
        // Convert the rotation record to a rotation response
        RotationResponse response = createRotationResponse(rotationData);
        return Optional.of(response);
    }

    /**
     * Retrieves all rotation processes for a specific client ID.
     * 
     * @param clientId The client ID to retrieve rotations for
     * @return List of rotation responses for the client ID
     */
    @Override
    public List<RotationResponse> getRotationsByClientId(String clientId) {
        // Validate the client ID
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        
        logger.debug("Retrieving rotations for client ID: {}", clientId);
        
        // Retrieve all rotation records for the client ID from the database
        List<Map<String, Object>> rotationsData = getRotationDataByClientId(clientId);
        
        // Convert the rotation records to rotation responses
        List<RotationResponse> responses = new ArrayList<>();
        for (Map<String, Object> rotationData : rotationsData) {
            responses.add(createRotationResponse(rotationData));
        }
        
        return responses;
    }

    /**
     * Advances a rotation process to the next state.
     * 
     * @param rotationId The unique identifier of the rotation process
     * @param targetState The target state to advance the rotation to
     * @return Response containing the updated rotation details
     */
    @Override
    @Transactional
    public RotationResponse advanceRotation(String rotationId, RotationState targetState) {
        // Validate the rotation ID and target state
        if (rotationId == null || rotationId.isEmpty()) {
            throw new IllegalArgumentException("Rotation ID cannot be null or empty");
        }
        if (targetState == null) {
            throw new IllegalArgumentException("Target state cannot be null");
        }
        
        logger.info("Advancing rotation {} to state {}", rotationId, targetState);
        
        // Retrieve the current rotation record from the database
        Map<String, Object> rotationData = getRotationDataById(rotationId);
        
        // If rotation not found, throw exception
        if (rotationData == null) {
            throw new IllegalArgumentException("No rotation found for rotation ID: " + rotationId);
        }
        
        RotationState currentState = (RotationState) rotationData.get("currentState");
        
        // Verify that the target state is a valid next state from the current state
        if (!isValidNextState(currentState, targetState)) {
            String message = "Invalid state transition from " + currentState + " to " + targetState;
            logger.warn(message);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .targetState(targetState)
                    .success(false)
                    .message(message)
                    .build();
        }
        
        // Store the previous state for notification purposes
        RotationState previousState = currentState;
        
        // Perform the state transition actions based on the target state
        boolean success = true;
        String message = "Rotation advanced to " + targetState;
        
        String clientId = (String) rotationData.get("clientId");
        String oldVersion = (String) rotationData.get("oldVersion");
        String newVersion = (String) rotationData.get("newVersion");
        
        try {
            switch (targetState) {
                case DUAL_ACTIVE:
                    // Configure both credentials as active
                    success = conjurService.configureCredentialTransition(
                            clientId, oldVersion, newVersion, 
                            (Integer) rotationData.get("transitionPeriodMinutes"));
                    break;
                    
                case OLD_DEPRECATED:
                    // Mark old credential as deprecated
                    success = conjurService.disableCredentialVersion(clientId, oldVersion);
                    break;
                    
                case NEW_ACTIVE:
                    // Remove old credential, mark rotation as complete
                    success = conjurService.removeCredentialVersion(clientId, oldVersion);
                    rotationData.put("completedAt", new Date());
                    break;
                    
                case FAILED:
                    // Mark as failed
                    rotationData.put("completedAt", new Date());
                    message = "Rotation failed";
                    break;
                    
                default:
                    success = false;
                    message = "Unexpected target state: " + targetState;
                    logger.error(message);
            }
            
            if (success) {
                // Update the rotation record with the new state
                rotationData.put("currentState", targetState);
                rotationData.put("status", message);
                updateRotationData(rotationData);
                
                logger.info("Rotation {} advanced to state {}", rotationId, targetState);
            } else {
                message = "Failed to advance rotation to state: " + targetState;
                logger.error(message);
            }
            
            RotationResponse response = createRotationResponse(rotationData);
            
            // Send rotation state changed notification via NotificationService
            notificationService.sendRotationStateChangedNotification(response, previousState);
            
            return response;
            
        } catch (Exception e) {
            // Log the rotation state change event
            logger.error("Error advancing rotation {}: {}", rotationId, e.getMessage(), e);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .targetState(targetState)
                    .success(false)
                    .message("Error advancing rotation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Completes a rotation process by finalizing the transition to the new credential.
     * 
     * @param rotationId The unique identifier of the rotation process
     * @return Response containing the completed rotation details
     */
    @Override
    @Transactional
    public RotationResponse completeRotation(String rotationId) {
        // Validate the rotation ID
        if (rotationId == null || rotationId.isEmpty()) {
            throw new IllegalArgumentException("Rotation ID cannot be null or empty");
        }
        
        logger.info("Completing rotation for rotation ID: {}", rotationId);
        
        // Retrieve the current rotation record from the database
        Map<String, Object> rotationData = getRotationDataById(rotationId);
        
        // If rotation not found, throw exception
        if (rotationData == null) {
            throw new IllegalArgumentException("No rotation found for rotation ID: " + rotationId);
        }
        
        RotationState currentState = (RotationState) rotationData.get("currentState");
        
        // Verify that the rotation is in a state that can be completed (OLD_DEPRECATED)
        if (currentState != RotationState.OLD_DEPRECATED) {
            String message = "Cannot complete rotation in state: " + currentState + ". Expected: " + RotationState.OLD_DEPRECATED;
            logger.warn(message);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .success(false)
                    .message(message)
                    .build();
        }
        
        String clientId = (String) rotationData.get("clientId");
        String oldVersion = (String) rotationData.get("oldVersion");
        
        try {
            // Remove the old credential version from Conjur vault
            boolean removed = conjurService.removeCredentialVersion(clientId, oldVersion);
            
            if (removed) {
                // Update the rotation record to NEW_ACTIVE state
                rotationData.put("currentState", RotationState.NEW_ACTIVE);
                // Set the completion timestamp
                rotationData.put("completedAt", new Date());
                rotationData.put("status", "Rotation completed");
                rotationData.put("message", "Credential rotation completed successfully");
                // Mark the rotation as successful
                rotationData.put("success", true);
                
                updateRotationData(rotationData);
                
                logger.info("Rotation {} completed successfully", rotationId);
            } else {
                String message = "Failed to remove old credential version during rotation completion";
                logger.error(message);
                rotationData.put("status", message);
                rotationData.put("message", message);
                rotationData.put("success", false);
            }
            
            RotationResponse response = createRotationResponse(rotationData);
            
            // Send rotation completed notification via NotificationService
            notificationService.sendRotationCompletedNotification(response);
            
            return response;
            
        } catch (Exception e) {
            // Log the rotation completion event
            logger.error("Error completing rotation {}: {}", rotationId, e.getMessage(), e);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .success(false)
                    .message("Error completing rotation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Cancels an in-progress rotation process.
     * 
     * @param rotationId The unique identifier of the rotation process
     * @param reason The reason for cancellation
     * @return Response containing the cancelled rotation details
     */
    @Override
    @Transactional
    public RotationResponse cancelRotation(String rotationId, String reason) {
        // Validate the rotation ID and reason
        if (rotationId == null || rotationId.isEmpty()) {
            throw new IllegalArgumentException("Rotation ID cannot be null or empty");
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason cannot be null or empty");
        }
        
        logger.info("Cancelling rotation for rotation ID: {}, reason: {}", rotationId, reason);
        
        // Retrieve the current rotation record from the database
        Map<String, Object> rotationData = getRotationDataById(rotationId);
        
        // If rotation not found, throw exception
        if (rotationData == null) {
            throw new IllegalArgumentException("No rotation found for rotation ID: " + rotationId);
        }
        
        RotationState currentState = (RotationState) rotationData.get("currentState");
        
        // Verify that the rotation is in a state that can be cancelled (not NEW_ACTIVE or FAILED)
        if (currentState == RotationState.NEW_ACTIVE || currentState == RotationState.FAILED) {
            String message = "Cannot cancel rotation in terminal state: " + currentState;
            logger.warn(message);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .success(false)
                    .message(message)
                    .build();
        }
        
        String clientId = (String) rotationData.get("clientId");
        String oldVersion = (String) rotationData.get("oldVersion");
        String newVersion = (String) rotationData.get("newVersion");
        
        boolean cleanupSuccess = true;
        
        try {
            // Perform cleanup actions based on the current state
            switch (currentState) {
                case INITIATED:
                    // For INITIATED: Remove new credential version
                    cleanupSuccess = conjurService.removeCredentialVersion(clientId, newVersion);
                    break;
                    
                case DUAL_ACTIVE:
                    // For DUAL_ACTIVE: Remove new credential version, reconfigure old credential as sole active version
                    cleanupSuccess = conjurService.removeCredentialVersion(clientId, newVersion);
                    if (cleanupSuccess) {
                        Optional<Map<String, String>> oldCredOptional = conjurService.retrieveCredentialVersion(clientId, oldVersion);
                        if (oldCredOptional.isPresent()) {
                            cleanupSuccess = conjurService.storeCredential(clientId, oldCredOptional.get());
                        } else {
                            cleanupSuccess = false;
                            logger.error("Failed to retrieve old credential version during cancellation");
                        }
                    }
                    break;
                    
                case OLD_DEPRECATED:
                    // For OLD_DEPRECATED: Reactivate old credential, remove new credential version
                    Optional<Map<String, String>> oldCredOptional = conjurService.retrieveCredentialVersion(clientId, oldVersion);
                    if (oldCredOptional.isPresent()) {
                        cleanupSuccess = conjurService.storeCredential(clientId, oldCredOptional.get());
                        if (cleanupSuccess) {
                            cleanupSuccess = conjurService.removeCredentialVersion(clientId, newVersion);
                        }
                    } else {
                        cleanupSuccess = false;
                        logger.error("Failed to retrieve old credential version during cancellation");
                    }
                    break;
            }
            
            // Update the rotation record to FAILED state
            rotationData.put("currentState", RotationState.FAILED);
            // Set the completion timestamp
            rotationData.put("completedAt", new Date());
            // Set the failure message with the provided reason
            String message = "Rotation cancelled: " + reason;
            rotationData.put("status", "Cancelled");
            rotationData.put("message", message);
            // Mark the rotation as unsuccessful
            rotationData.put("success", false);
            
            updateRotationData(rotationData);
            
            RotationResponse response = createRotationResponse(rotationData);
            
            // Send rotation failed notification via NotificationService
            notificationService.sendRotationFailedNotification(response, reason);
            
            // Log the rotation cancellation event
            if (cleanupSuccess) {
                logger.info("Rotation {} cancelled successfully", rotationId);
            } else {
                logger.warn("Rotation {} cancelled with cleanup issues", rotationId);
            }
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error cancelling rotation {}: {}", rotationId, e.getMessage(), e);
            return RotationResponse.builder()
                    .rotationId(rotationId)
                    .clientId((String) rotationData.get("clientId"))
                    .currentState(currentState)
                    .success(false)
                    .message("Error cancelling rotation: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Checks the progress of ongoing rotations and advances them if necessary.
     * 
     * @return List of rotation responses that were processed
     */
    @Override
    @Scheduled(fixedDelayString = "${rotation.check.interval:60000}")
    public List<RotationResponse> checkRotationProgress() {
        // Retrieve all active rotation processes
        List<RotationResponse> activeRotations = getActiveRotations();
        // Initialize a list to store processed rotation responses
        List<RotationResponse> processedRotations = new ArrayList<>();
        
        logger.debug("Checking progress of {} active rotations", activeRotations.size());
        
        for (RotationResponse rotation : activeRotations) {
            try {
                RotationState currentState = rotation.getCurrentState();
                String rotationId = rotation.getRotationId();
                
                // Check if it needs to be advanced based on its state and timing
                if (currentState == RotationState.DUAL_ACTIVE) {
                    // For DUAL_ACTIVE rotations, check if the transition period has expired
                    Date startedAt = rotation.getStartedAt();
                    int transitionMinutes = rotation.getTransitionPeriodMinutes();
                    Date transitionEnd = new Date(startedAt.getTime() + (transitionMinutes * 60 * 1000));
                    
                    if (new Date().after(transitionEnd)) {
                        // If expired, advance to OLD_DEPRECATED state
                        RotationResponse updated = advanceRotation(rotationId, RotationState.OLD_DEPRECATED);
                        processedRotations.add(updated);
                        logger.info("Automatically advanced rotation {} to OLD_DEPRECATED", rotationId);
                    }
                } else if (currentState == RotationState.OLD_DEPRECATED) {
                    // For OLD_DEPRECATED rotations, check if all services are using the new credential
                    Optional<Map<String, Object>> transitionStatus = 
                            conjurService.getCredentialTransitionStatus(rotation.getClientId());
                    
                    if (transitionStatus.isPresent()) {
                        Map<String, Object> status = transitionStatus.get();
                        boolean oldVersionInUse = Boolean.TRUE.equals(status.get("oldVersionInUse"));
                        
                        if (!oldVersionInUse) {
                            // If all services are using the new credential, advance to NEW_ACTIVE state
                            RotationResponse updated = completeRotation(rotationId);
                            processedRotations.add(updated);
                            logger.info("Automatically completed rotation {}", rotationId);
                        }
                    }
                }
            } catch (Exception e) {
                // Add the processed rotation response to the result list
                logger.error("Error processing rotation {}: {}", rotation.getRotationId(), e.getMessage(), e);
            }
        }
        
        // Log the number of rotations processed
        logger.debug("Processed {} rotations during automatic check", processedRotations.size());
        
        return processedRotations;
    }

    /**
     * Retrieves all currently active rotation processes.
     * 
     * @return List of active rotation responses
     */
    @Override
    public List<RotationResponse> getActiveRotations() {
        logger.debug("Retrieving all active rotations");
        
        // Retrieve all rotation records that are not in a terminal state (NEW_ACTIVE or FAILED) from the database
        List<Map<String, Object>> activeRotationsData = getActiveRotationsData();
        
        // Convert the rotation records to rotation responses
        List<RotationResponse> responses = new ArrayList<>();
        for (Map<String, Object> rotationData : activeRotationsData) {
            responses.add(createRotationResponse(rotationData));
        }
        
        return responses;
    }

    /**
     * Checks if a target state is a valid next state from the current state.
     * 
     * @param currentState The current state of the rotation
     * @param targetState The target state to transition to
     * @return True if the target state is a valid next state, false otherwise
     */
    private boolean isValidNextState(RotationState currentState, RotationState targetState) {
        switch (currentState) {
            case INITIATED:
                return targetState == RotationState.DUAL_ACTIVE || targetState == RotationState.FAILED;
            case DUAL_ACTIVE:
                return targetState == RotationState.OLD_DEPRECATED || targetState == RotationState.FAILED;
            case OLD_DEPRECATED:
                return targetState == RotationState.NEW_ACTIVE || targetState == RotationState.FAILED;
            case NEW_ACTIVE:
            case FAILED:
                // Terminal states cannot transition to any other state
                return false;
            default:
                return false;
        }
    }

    /**
     * Creates a rotation response object from rotation data.
     * 
     * @param rotationData Map containing rotation data fields
     * @return Rotation response object
     */
    private RotationResponse createRotationResponse(Map<String, Object> rotationData) {
        return RotationResponse.builder()
                .rotationId((String) rotationData.get("rotationId"))
                .clientId((String) rotationData.get("clientId"))
                .currentState((RotationState) rotationData.get("currentState"))
                .targetState((RotationState) rotationData.get("targetState"))
                .oldVersion((String) rotationData.get("oldVersion"))
                .newVersion((String) rotationData.get("newVersion"))
                .transitionPeriodMinutes((Integer) rotationData.get("transitionPeriodMinutes"))
                .startedAt((Date) rotationData.get("startedAt"))
                .completedAt((Date) rotationData.get("completedAt"))
                .status((String) rotationData.get("status"))
                .message((String) rotationData.get("message"))
                .success((Boolean) rotationData.get("success"))
                .build();
    }

    /**
     * Validates a rotation request.
     * 
     * @param request The rotation request to validate
     * @throws IllegalArgumentException if the request is invalid
     */
    private void validateRotationRequest(RotationRequest request) {
        if (request.getClientId() == null || request.getClientId().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
        
        if (request.getTransitionPeriodMinutes() != null && request.getTransitionPeriodMinutes() <= 0) {
            throw new IllegalArgumentException("Transition period must be greater than zero");
        }
        
        if (request.getTargetState() != null) {
            // Validate it is a valid state for manual transition
            RotationState targetState = request.getTargetState();
            if (targetState == null) {
                throw new IllegalArgumentException("Target state cannot be null");
            }
        }
    }
    
    // Database access methods - would be implemented using actual repository in production
    
    private Map<String, Object> getRotationDataById(String rotationId) {
        // In a real implementation, this would retrieve data from a database
        // This is a placeholder implementation
        return null; 
    }
    
    private List<Map<String, Object>> getRotationDataByClientId(String clientId) {
        // In a real implementation, this would retrieve data from a database
        return new ArrayList<>();
    }
    
    private void updateRotationData(Map<String, Object> rotationData) {
        // In a real implementation, this would update data in a database
    }
    
    private List<Map<String, Object>> getActiveRotationsData() {
        // In a real implementation, this would retrieve data from a database
        return new ArrayList<>();
    }
}