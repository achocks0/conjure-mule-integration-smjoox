package com.payment.rotation.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the different states of a credential rotation process.
 * Each state represents a specific phase in the credential rotation lifecycle.
 */
public enum RotationState {
    /**
     * Initial state when a rotation process has been requested but not yet started
     */
    INITIATED("initiated", "Initial state when a rotation process has been requested but not yet started"),
    
    /**
     * Both old and new credentials are active during the transition period
     */
    DUAL_ACTIVE("dual_active", "Both old and new credentials are active during the transition period"),
    
    /**
     * Old credentials are marked as deprecated but still valid for existing sessions
     */
    OLD_DEPRECATED("old_deprecated", "Old credentials are marked as deprecated but still valid for existing sessions"),
    
    /**
     * Only new credentials are active, rotation completed successfully
     */
    NEW_ACTIVE("new_active", "Only new credentials are active, rotation completed successfully"),
    
    /**
     * Rotation process failed and was rolled back or cancelled
     */
    FAILED("failed", "Rotation process failed and was rolled back or cancelled");

    private final String value;
    private final String description;

    /**
     * Constructor for RotationState enum
     * 
     * @param value The string value of the rotation state
     * @param description The human-readable description of the rotation state
     */
    RotationState(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * Returns the string value of the rotation state for JSON serialization
     * 
     * @return The string value of the rotation state
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Returns the human-readable description of the rotation state
     * 
     * @return The description of the rotation state
     */
    public String getDescription() {
        return description;
    }
}