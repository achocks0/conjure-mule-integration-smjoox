package com.payment.rotation.service;

import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;

/**
 * Interface defining methods for sending notifications about credential rotation events to relevant stakeholders.
 * <p>
 * This service is responsible for notifying operations teams, security teams, and other systems about
 * the status and progress of credential rotation operations. Notifications help ensure that all
 * relevant parties are aware of credential changes and can take appropriate actions if needed.
 * <p>
 * Implementations of this interface should handle different notification channels such as email,
 * Slack, system logs, or monitoring systems as required by the organization.
 */
public interface NotificationService {

    /**
     * Sends a notification when a credential rotation process has started.
     * <p>
     * This notification informs stakeholders that a credential rotation has been initiated
     * for a specific client, allowing them to monitor the process or take preparatory actions.
     *
     * @param rotationResponse the response object containing details about the initiated rotation
     *                        including client ID, rotation ID, and initial state
     */
    void sendRotationStartedNotification(RotationResponse rotationResponse);

    /**
     * Sends a notification when a credential rotation process changes state.
     * <p>
     * This notification informs stakeholders about progression through the rotation workflow,
     * such as transitions between different states (e.g., from INITIATED to DUAL_ACTIVE).
     *
     * @param rotationResponse the response object containing details about the current rotation state
     * @param previousState the state from which the rotation process has transitioned
     */
    void sendRotationStateChangedNotification(RotationResponse rotationResponse, RotationState previousState);

    /**
     * Sends a notification when a credential rotation process has completed successfully.
     * <p>
     * This notification informs stakeholders that the rotation has been successfully completed
     * and the new credentials are now active. It should include information about both the old
     * and new credential versions for audit purposes.
     *
     * @param rotationResponse the response object containing details about the completed rotation
     *                        including success status, client ID, and credential versions
     */
    void sendRotationCompletedNotification(RotationResponse rotationResponse);

    /**
     * Sends a notification when a credential rotation process has failed.
     * <p>
     * This notification alerts stakeholders about a rotation failure that may require
     * immediate attention or manual intervention. The notification should include error
     * details to assist in troubleshooting.
     *
     * @param rotationResponse the response object containing details about the failed rotation
     * @param errorMessage a detailed error message explaining the cause of the failure
     */
    void sendRotationFailedNotification(RotationResponse rotationResponse, String errorMessage);
}