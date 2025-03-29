package com.payment.rotation.service.impl;

import com.payment.common.monitoring.MetricsService;
import com.payment.monitoring.service.AlertService;
import com.payment.monitoring.service.AlertType;
import com.payment.monitoring.service.Severity;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;
import com.payment.rotation.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of the NotificationService interface that handles sending notifications 
 * about credential rotation events to relevant stakeholders.
 * <p>
 * This service is responsible for notifying operations teams, security teams, and other systems 
 * about the status and progress of credential rotation operations.
 */
@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final MetricsService metricsService;
    private final AlertService alertService;

    /**
     * Constructs a new NotificationServiceImpl with the required dependencies.
     *
     * @param metricsService service for recording metrics related to credential rotation
     * @param alertService service for sending alerts about credential rotation events
     */
    public NotificationServiceImpl(MetricsService metricsService, AlertService alertService) {
        this.metricsService = metricsService;
        this.alertService = alertService;
    }

    @Override
    public void sendRotationStartedNotification(RotationResponse rotationResponse) {
        try {
            String rotationId = rotationResponse.getRotationId();
            String clientId = rotationResponse.getClientId();
            RotationState state = rotationResponse.getCurrentState();

            log.info("Credential rotation started: rotationId={}, clientId={}, initialState={}",
                    rotationId, clientId, state);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rotationId", rotationId);
            metadata.put("clientId", clientId);
            metadata.put("state", state.getValue());
            metadata.put("stateDescription", state.getDescription());
            metadata.put("event", "ROTATION_STARTED");

            metricsService.recordCredentialRotation("rotation_started", metadata);
            alertService.sendSecurityAlert(
                    AlertType.CREDENTIAL_ROTATION,
                    Severity.MEDIUM,
                    "Credential rotation started for client " + clientId,
                    metadata
            );
        } catch (Exception e) {
            log.error("Failed to send rotation started notification", e);
        }
    }

    @Override
    public void sendRotationStateChangedNotification(RotationResponse rotationResponse, RotationState previousState) {
        try {
            String rotationId = rotationResponse.getRotationId();
            String clientId = rotationResponse.getClientId();
            RotationState currentState = rotationResponse.getCurrentState();

            log.info("Credential rotation state changed: rotationId={}, clientId={}, previousState={}, currentState={}",
                    rotationId, clientId, previousState, currentState);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rotationId", rotationId);
            metadata.put("clientId", clientId);
            metadata.put("previousState", previousState.getValue());
            metadata.put("previousStateDescription", previousState.getDescription());
            metadata.put("currentState", currentState.getValue());
            metadata.put("currentStateDescription", currentState.getDescription());
            metadata.put("event", "ROTATION_STATE_CHANGED");

            metricsService.recordCredentialRotation("rotation_state_changed", metadata);
            alertService.sendSecurityAlert(
                    AlertType.CREDENTIAL_ROTATION,
                    Severity.MEDIUM,
                    String.format("Credential rotation state changed for client %s: %s → %s",
                            clientId, previousState.getValue(), currentState.getValue()),
                    metadata
            );
        } catch (Exception e) {
            log.error("Failed to send rotation state changed notification", e);
        }
    }

    @Override
    public void sendRotationCompletedNotification(RotationResponse rotationResponse) {
        try {
            String rotationId = rotationResponse.getRotationId();
            String clientId = rotationResponse.getClientId();
            String oldVersion = rotationResponse.getOldVersion();
            String newVersion = rotationResponse.getNewVersion();

            log.info("Credential rotation completed successfully: rotationId={}, clientId={}, oldVersion={}, newVersion={}",
                    rotationId, clientId, oldVersion, newVersion);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rotationId", rotationId);
            metadata.put("clientId", clientId);
            metadata.put("oldVersion", oldVersion);
            metadata.put("newVersion", newVersion);
            metadata.put("status", rotationResponse.getStatus());
            metadata.put("event", "ROTATION_COMPLETED");
            if (rotationResponse.getCompletedAt() != null) {
                metadata.put("completedAt", rotationResponse.getCompletedAt().toString());
            }

            metricsService.recordCredentialRotation("rotation_completed", metadata);
            alertService.sendSecurityAlert(
                    AlertType.CREDENTIAL_ROTATION,
                    Severity.MEDIUM,
                    String.format("Credential rotation completed successfully for client %s: %s → %s",
                            clientId, oldVersion, newVersion),
                    metadata
            );
        } catch (Exception e) {
            log.error("Failed to send rotation completed notification", e);
        }
    }

    @Override
    public void sendRotationFailedNotification(RotationResponse rotationResponse, String errorMessage) {
        try {
            String rotationId = rotationResponse.getRotationId();
            String clientId = rotationResponse.getClientId();
            RotationState state = rotationResponse.getCurrentState();

            log.error("Credential rotation failed: rotationId={}, clientId={}, state={}, error={}",
                    rotationId, clientId, state, errorMessage);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("rotationId", rotationId);
            metadata.put("clientId", clientId);
            metadata.put("state", state.getValue());
            metadata.put("stateDescription", state.getDescription());
            metadata.put("errorMessage", errorMessage);
            metadata.put("status", rotationResponse.getStatus());
            metadata.put("event", "ROTATION_FAILED");

            metricsService.recordCredentialRotation("rotation_failed", metadata);
            alertService.sendSecurityAlert(
                    AlertType.CREDENTIAL_ROTATION,
                    Severity.HIGH,
                    String.format("Credential rotation failed for client %s: %s",
                            clientId, errorMessage),
                    metadata
            );
        } catch (Exception e) {
            log.error("Failed to send rotation failed notification", e);
        }
    }
}