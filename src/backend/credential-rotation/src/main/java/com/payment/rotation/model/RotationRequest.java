package com.payment.rotation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;

/**
 * Model class representing a request to initiate or manage a credential rotation process.
 * Contains parameters needed to control the rotation process without causing service disruption.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RotationRequest {

    /**
     * The unique identifier of the client whose credentials are being rotated.
     */
    @NotBlank(message = "Client ID is required")
    @JsonProperty("client_id")
    private String clientId;

    /**
     * The reason for initiating the credential rotation.
     */
    @JsonProperty("reason")
    private String reason;

    /**
     * The duration in minutes for the transition period where both old and new credentials are valid.
     * This ensures zero-downtime during rotation by allowing existing sessions to complete.
     */
    @Min(value = 5, message = "Transition period must be at least 5 minutes")
    @JsonProperty("transition_period_minutes")
    private Integer transitionPeriodMinutes;

    /**
     * Flag to force rotation even if conditions aren't ideal.
     * Use with caution as it may impact ongoing operations.
     */
    @JsonProperty("force_rotation")
    private Boolean forceRotation;

    /**
     * The target state for the rotation process.
     * Determines the specific phase of rotation to execute.
     */
    @JsonProperty("target_state")
    private RotationState targetState;
}