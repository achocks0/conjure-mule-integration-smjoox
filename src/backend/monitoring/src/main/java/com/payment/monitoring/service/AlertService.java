package com.payment.monitoring.service;

import java.util.Map; // JDK 11
import java.util.List; // JDK 11

/**
 * Interface defining methods for checking metrics against thresholds and sending alerts 
 * when thresholds are exceeded.
 */
public interface AlertService {
    
    /**
     * Sends a security alert to appropriate channels based on severity
     *
     * @param alertType The type of security alert
     * @param message The alert message
     * @param severity The severity of the alert
     * @param metadata Additional information about the alert
     * @return True if the alert was successfully sent, false otherwise
     */
    boolean sendSecurityAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata);
    
    /**
     * Sends a performance alert to appropriate channels based on severity
     *
     * @param alertType The type of performance alert
     * @param message The alert message
     * @param severity The severity of the alert
     * @param metadata Additional information about the alert
     * @return True if the alert was successfully sent, false otherwise
     */
    boolean sendPerformanceAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata);
    
    /**
     * Sends an availability alert to appropriate channels based on severity
     *
     * @param alertType The type of availability alert
     * @param message The alert message
     * @param severity The severity of the alert
     * @param metadata Additional information about the alert
     * @return True if the alert was successfully sent, false otherwise
     */
    boolean sendAvailabilityAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata);
    
    /**
     * Checks security metrics against defined thresholds and sends alerts if thresholds are exceeded
     *
     * @param metricName The name of the metric to check
     * @param value The current value of the metric
     * @param tags Additional metadata tags associated with the metric
     * @return True if any threshold was exceeded and an alert was sent, false otherwise
     */
    boolean checkSecurityThresholds(String metricName, double value, Map<String, String> tags);
    
    /**
     * Checks performance metrics against defined thresholds and sends alerts if thresholds are exceeded
     *
     * @param metricName The name of the metric to check
     * @param value The current value of the metric
     * @param tags Additional metadata tags associated with the metric
     * @return True if any threshold was exceeded and an alert was sent, false otherwise
     */
    boolean checkPerformanceThresholds(String metricName, double value, Map<String, String> tags);
    
    /**
     * Checks availability metrics against defined thresholds and sends alerts if thresholds are exceeded
     *
     * @param metricName The name of the metric to check
     * @param value The current value of the metric
     * @param tags Additional metadata tags associated with the metric
     * @return True if any threshold was exceeded and an alert was sent, false otherwise
     */
    boolean checkAvailabilityThresholds(String metricName, double value, Map<String, String> tags);
    
    /**
     * Retrieves a list of currently active alerts
     *
     * @return List of currently active alerts
     */
    List<Alert> getActiveAlerts();
    
    /**
     * Acknowledges an alert to indicate it is being addressed
     *
     * @param alertId The unique identifier of the alert
     * @param acknowledgedBy The identifier of the person acknowledging the alert
     * @param notes Notes regarding the acknowledgement
     * @return True if the alert was successfully acknowledged, false otherwise
     */
    boolean acknowledgeAlert(String alertId, String acknowledgedBy, String notes);
    
    /**
     * Resolves an alert to indicate the issue has been fixed
     *
     * @param alertId The unique identifier of the alert
     * @param resolvedBy The identifier of the person resolving the alert
     * @param resolutionNotes Notes regarding the resolution
     * @return True if the alert was successfully resolved, false otherwise
     */
    boolean resolveAlert(String alertId, String resolvedBy, String resolutionNotes);
    
    /**
     * Retrieves the alert history for a specified time period
     *
     * @param startTime The start time of the period (milliseconds since epoch)
     * @param endTime The end time of the period (milliseconds since epoch)
     * @param alertType Optional filter for alert type (can be null for all types)
     * @param severity Optional filter for severity (can be null for all severities)
     * @return List of alerts matching the specified criteria
     */
    List<Alert> getAlertHistory(long startTime, long endTime, AlertType alertType, Severity severity);
}

/**
 * Interface representing an alert with its properties and metadata
 */
public interface Alert {
    /**
     * Gets the unique identifier of the alert
     *
     * @return The alert ID
     */
    String getId();
    
    /**
     * Gets the type of the alert
     *
     * @return The alert type
     */
    AlertType getType();
    
    /**
     * Gets the alert message
     *
     * @return The alert message
     */
    String getMessage();
    
    /**
     * Gets the severity of the alert
     *
     * @return The alert severity
     */
    Severity getSeverity();
    
    /**
     * Gets the timestamp when the alert was created
     *
     * @return The alert timestamp (milliseconds since epoch)
     */
    long getTimestamp();
    
    /**
     * Gets the current status of the alert
     *
     * @return The alert status
     */
    AlertStatus getStatus();
    
    /**
     * Gets the metadata associated with the alert
     *
     * @return The alert metadata
     */
    Map<String, Object> getMetadata();
}

/**
 * Enum defining types of alerts that can be generated by the system
 */
public enum AlertType {
    // Security alert types
    AUTHENTICATION_FAILURE,
    UNAUTHORIZED_ACCESS,
    TOKEN_VALIDATION_FAILURE,
    CREDENTIAL_ACCESS_ANOMALY,
    
    // Performance alert types
    API_RESPONSE_TIME,
    AUTHENTICATION_TIME,
    TOKEN_GENERATION_TIME,
    CONJUR_RESPONSE_TIME,
    
    // Availability alert types
    API_AVAILABILITY,
    CONJUR_AVAILABILITY,
    REDIS_AVAILABILITY,
    DATABASE_AVAILABILITY
}

/**
 * Enum defining severity levels for alerts
 */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Enum defining possible statuses for alerts
 */
public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED,
    CLOSED
}