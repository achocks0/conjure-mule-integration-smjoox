package com.payment.rotation.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Model class representing the response to a credential rotation operation.
 * Contains detailed information about the rotation process including its current state,
 * progress, and outcome.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RotationResponse implements Serializable {

    /**
     * Unique identifier for the rotation process
     */
    @JsonProperty("rotation_id")
    private String rotationId;
    
    /**
     * ID of the client whose credentials are being rotated
     */
    @JsonProperty("client_id")
    private String clientId;
    
    /**
     * Current state of the rotation process
     */
    @JsonProperty("current_state")
    private RotationState currentState;
    
    /**
     * Target state of the rotation process
     */
    @JsonProperty("target_state")
    private RotationState targetState;
    
    /**
     * Version identifier of the old credentials
     */
    @JsonProperty("old_version")
    private String oldVersion;
    
    /**
     * Version identifier of the new credentials
     */
    @JsonProperty("new_version")
    private String newVersion;
    
    /**
     * Duration of the transition period in minutes
     */
    @JsonProperty("transition_period_minutes")
    private Integer transitionPeriodMinutes;
    
    /**
     * Timestamp when the rotation process was started
     */
    @JsonProperty("started_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date startedAt;
    
    /**
     * Timestamp when the rotation process was completed
     */
    @JsonProperty("completed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date completedAt;
    
    /**
     * Status description of the rotation process
     */
    @JsonProperty("status")
    private String status;
    
    /**
     * Detailed message about the rotation process
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * Flag indicating whether the rotation was successful
     */
    @JsonProperty("success")
    private Boolean success;
}