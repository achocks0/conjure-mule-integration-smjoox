package com.payment.rotation.service;

import com.payment.rotation.model.RotationRequest;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing credential rotation processes.
 * This service orchestrates the secure rotation of authentication credentials without service
 * disruption by supporting multiple valid credential versions during transition periods.
 */
public interface RotationService {

    /**
     * Initiates a new credential rotation process for the specified client.
     * This method starts the rotation process by generating a new credential version
     * and configuring the system to accept both old and new credentials during the transition period.
     *
     * @param request The rotation request containing parameters for the rotation process
     * @return Response containing the details of the initiated rotation process
     */
    RotationResponse initiateRotation(RotationRequest request);

    /**
     * Retrieves the current status of a credential rotation process.
     *
     * @param rotationId The unique identifier of the rotation process
     * @return Optional containing the rotation status if found
     */
    Optional<RotationResponse> getRotationStatus(String rotationId);

    /**
     * Retrieves all rotation processes for a specific client ID.
     *
     * @param clientId The client ID to retrieve rotations for
     * @return List of rotation responses for the client ID
     */
    List<RotationResponse> getRotationsByClientId(String clientId);

    /**
     * Advances a rotation process to the next state.
     * This method transitions the rotation from one state to another, performing
     * the necessary actions for each state transition (e.g., updating Conjur vault,
     * notifying services, etc.).
     *
     * @param rotationId The unique identifier of the rotation process
     * @param targetState The target state to advance the rotation to
     * @return Response containing the updated rotation details
     */
    RotationResponse advanceRotation(String rotationId, RotationState targetState);

    /**
     * Completes a rotation process by finalizing the transition to the new credential.
     * This method removes the old credential and ensures all services are using the new credential.
     *
     * @param rotationId The unique identifier of the rotation process
     * @return Response containing the completed rotation details
     */
    RotationResponse completeRotation(String rotationId);

    /**
     * Cancels an in-progress rotation process.
     * This method safely aborts the rotation process and performs necessary cleanup,
     * ensuring system stability.
     *
     * @param rotationId The unique identifier of the rotation process
     * @param reason The reason for cancellation
     * @return Response containing the cancelled rotation details
     */
    RotationResponse cancelRotation(String rotationId, String reason);

    /**
     * Checks the progress of ongoing rotations and advances them if necessary.
     * This method is typically called by a scheduled job to automatically
     * progress rotations based on their current state and timing.
     *
     * @return List of rotation responses that were processed
     */
    List<RotationResponse> checkRotationProgress();

    /**
     * Retrieves all currently active rotation processes.
     * This includes all rotations that haven't reached a terminal state (NEW_ACTIVE or FAILED).
     *
     * @return List of active rotation responses
     */
    List<RotationResponse> getActiveRotations();
}