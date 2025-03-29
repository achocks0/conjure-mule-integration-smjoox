package com.payment.monitoring.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper; // 2.13.x
import com.payment.monitoring.config.AlertThresholdProperties;
import com.payment.monitoring.config.NotificationProperties;
import com.payment.monitoring.model.Alert;
import com.payment.monitoring.model.AlertImpl;
import com.payment.monitoring.model.AlertStatus;
import com.payment.monitoring.model.AlertType;
import com.payment.monitoring.model.Severity;
import com.payment.monitoring.service.AlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // 5.6.x

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the AlertService interface that provides functionality for checking metrics
 * against defined thresholds and sending alerts to appropriate channels when thresholds are exceeded.
 */
@Service
@Slf4j
public class AlertServiceImpl implements AlertService {

    private final RestTemplate restTemplate;
    private final NotificationProperties notificationProperties;
    private final AlertThresholdProperties alertThresholdProperties;
    private final ObjectMapper objectMapper;
    private final List<Alert> activeAlerts;
    private final List<Alert> alertHistory;

    /**
     * Constructs an AlertServiceImpl with the required dependencies
     *
     * @param restTemplate RestTemplate for making HTTP requests to notification services
     * @param notificationProperties Configuration properties for notification channels
     * @param alertThresholdProperties Configuration properties for alert thresholds
     * @param objectMapper ObjectMapper for JSON serialization/deserialization
     */
    public AlertServiceImpl(RestTemplate restTemplate,
                           NotificationProperties notificationProperties,
                           AlertThresholdProperties alertThresholdProperties,
                           ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.notificationProperties = notificationProperties;
        this.alertThresholdProperties = alertThresholdProperties;
        this.objectMapper = objectMapper;
        this.activeAlerts = new ArrayList<>();
        this.alertHistory = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendSecurityAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata) {
        log.info("Sending security alert: type={}, severity={}, message={}", alertType, severity, message);
        
        // Create a new alert
        Alert alert = new AlertImpl(
            UUID.randomUUID().toString(),
            alertType,
            message,
            severity,
            System.currentTimeMillis(),
            AlertStatus.ACTIVE,
            metadata != null ? metadata : new HashMap<>()
        );
        
        // Add to active alerts and history
        activeAlerts.add(alert);
        alertHistory.add(alert);
        
        // Send to appropriate channels based on severity
        boolean success = true;
        
        // Critical alerts go to PagerDuty
        if (severity == Severity.CRITICAL) {
            success = success && sendToPagerDuty(alert);
        }
        
        // Critical and High alerts go to email
        if (severity == Severity.CRITICAL || severity == Severity.HIGH) {
            success = success && sendToEmail(alert);
        }
        
        // All alerts go to Slack
        success = success && sendToSlack(alert);
        
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendPerformanceAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata) {
        log.info("Sending performance alert: type={}, severity={}, message={}", alertType, severity, message);
        
        // Create a new alert
        Alert alert = new AlertImpl(
            UUID.randomUUID().toString(),
            alertType,
            message,
            severity,
            System.currentTimeMillis(),
            AlertStatus.ACTIVE,
            metadata != null ? metadata : new HashMap<>()
        );
        
        // Add to active alerts and history
        activeAlerts.add(alert);
        alertHistory.add(alert);
        
        // Send to appropriate channels based on severity
        boolean success = true;
        
        // Critical alerts go to PagerDuty
        if (severity == Severity.CRITICAL) {
            success = success && sendToPagerDuty(alert);
        }
        
        // Critical and High alerts go to email
        if (severity == Severity.CRITICAL || severity == Severity.HIGH) {
            success = success && sendToEmail(alert);
        }
        
        // All alerts go to Slack
        success = success && sendToSlack(alert);
        
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendAvailabilityAlert(AlertType alertType, String message, Severity severity, Map<String, Object> metadata) {
        log.info("Sending availability alert: type={}, severity={}, message={}", alertType, severity, message);
        
        // Create a new alert
        Alert alert = new AlertImpl(
            UUID.randomUUID().toString(),
            alertType,
            message,
            severity,
            System.currentTimeMillis(),
            AlertStatus.ACTIVE,
            metadata != null ? metadata : new HashMap<>()
        );
        
        // Add to active alerts and history
        activeAlerts.add(alert);
        alertHistory.add(alert);
        
        // Send to appropriate channels based on severity
        boolean success = true;
        
        // Critical alerts go to PagerDuty
        if (severity == Severity.CRITICAL) {
            success = success && sendToPagerDuty(alert);
        }
        
        // Critical and High alerts go to email
        if (severity == Severity.CRITICAL || severity == Severity.HIGH) {
            success = success && sendToEmail(alert);
        }
        
        // All alerts go to Slack
        success = success && sendToSlack(alert);
        
        return success;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkSecurityThresholds(String metricName, double value, Map<String, String> tags) {
        log.debug("Checking security thresholds for metric: {}, value: {}", metricName, value);
        
        // Get security thresholds
        Map<String, Map<String, Double>> securityThresholds = alertThresholdProperties.getSecurity();
        boolean alertSent = false;
        
        // Create metadata map for alerts
        Map<String, Object> metadata = new HashMap<>();
        if (tags != null) {
            metadata.putAll(tags);
        }
        metadata.put("metricName", metricName);
        metadata.put("value", value);
        
        // Check thresholds based on metric name
        switch (metricName) {
            case "authentication.failure.rate":
                // Authentication failure rate (percentage)
                if (value >= securityThresholds.get("authentication").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.AUTHENTICATION_FAILURE,
                        String.format("Authentication failure rate of %.2f%% exceeds critical threshold (%.2f%%)",
                            value, securityThresholds.get("authentication").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= securityThresholds.get("authentication").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.AUTHENTICATION_FAILURE,
                        String.format("Authentication failure rate of %.2f%% exceeds warning threshold (%.2f%%)",
                            value, securityThresholds.get("authentication").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "unauthorized.access.attempts":
                // Unauthorized access attempts (count)
                if (value >= securityThresholds.get("unauthorized").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.UNAUTHORIZED_ACCESS,
                        String.format("%.0f unauthorized access attempts exceed critical threshold (%.0f)",
                            value, securityThresholds.get("unauthorized").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= securityThresholds.get("unauthorized").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.UNAUTHORIZED_ACCESS,
                        String.format("%.0f unauthorized access attempts exceed warning threshold (%.0f)",
                            value, securityThresholds.get("unauthorized").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "token.validation.failure.rate":
                // Token validation failure rate (percentage)
                if (value >= securityThresholds.get("tokenValidation").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.TOKEN_VALIDATION_FAILURE,
                        String.format("Token validation failure rate of %.2f%% exceeds critical threshold (%.2f%%)",
                            value, securityThresholds.get("tokenValidation").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= securityThresholds.get("tokenValidation").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.TOKEN_VALIDATION_FAILURE,
                        String.format("Token validation failure rate of %.2f%% exceeds warning threshold (%.2f%%)",
                            value, securityThresholds.get("tokenValidation").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "credential.access.anomaly":
                // Credential access anomaly (deviation from normal)
                if (value >= securityThresholds.get("credentialAccess").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.CREDENTIAL_ACCESS_ANOMALY,
                        String.format("Credential access anomaly factor of %.2f exceeds critical threshold (%.2f)",
                            value, securityThresholds.get("credentialAccess").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= securityThresholds.get("credentialAccess").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendSecurityAlert(
                        AlertType.CREDENTIAL_ACCESS_ANOMALY,
                        String.format("Credential access anomaly factor of %.2f exceeds warning threshold (%.2f)",
                            value, securityThresholds.get("credentialAccess").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            default:
                log.warn("Unknown security metric: {}", metricName);
                break;
        }
        
        return alertSent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkPerformanceThresholds(String metricName, double value, Map<String, String> tags) {
        log.debug("Checking performance thresholds for metric: {}, value: {}", metricName, value);
        
        // Get performance thresholds
        Map<String, Map<String, Double>> performanceThresholds = alertThresholdProperties.getPerformance();
        boolean alertSent = false;
        
        // Create metadata map for alerts
        Map<String, Object> metadata = new HashMap<>();
        if (tags != null) {
            metadata.putAll(tags);
        }
        metadata.put("metricName", metricName);
        metadata.put("value", value);
        
        // Check thresholds based on metric name
        switch (metricName) {
            case "api.response.time":
                // API response time (milliseconds)
                if (value >= performanceThresholds.get("apiResponse").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.API_RESPONSE_TIME,
                        String.format("API response time of %.2f ms exceeds critical threshold (%.2f ms)",
                            value, performanceThresholds.get("apiResponse").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= performanceThresholds.get("apiResponse").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.API_RESPONSE_TIME,
                        String.format("API response time of %.2f ms exceeds warning threshold (%.2f ms)",
                            value, performanceThresholds.get("apiResponse").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "authentication.time":
                // Authentication time (milliseconds)
                if (value >= performanceThresholds.get("authentication").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.AUTHENTICATION_TIME,
                        String.format("Authentication time of %.2f ms exceeds critical threshold (%.2f ms)",
                            value, performanceThresholds.get("authentication").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= performanceThresholds.get("authentication").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.AUTHENTICATION_TIME,
                        String.format("Authentication time of %.2f ms exceeds warning threshold (%.2f ms)",
                            value, performanceThresholds.get("authentication").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "token.generation.time":
                // Token generation time (milliseconds)
                if (value >= performanceThresholds.get("tokenGeneration").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.TOKEN_GENERATION_TIME,
                        String.format("Token generation time of %.2f ms exceeds critical threshold (%.2f ms)",
                            value, performanceThresholds.get("tokenGeneration").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= performanceThresholds.get("tokenGeneration").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.TOKEN_GENERATION_TIME,
                        String.format("Token generation time of %.2f ms exceeds warning threshold (%.2f ms)",
                            value, performanceThresholds.get("tokenGeneration").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "conjur.response.time":
                // Conjur response time (milliseconds)
                if (value >= performanceThresholds.get("conjurResponse").get("critical")) {
                    // Critical threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.CONJUR_RESPONSE_TIME,
                        String.format("Conjur response time of %.2f ms exceeds critical threshold (%.2f ms)",
                            value, performanceThresholds.get("conjurResponse").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value >= performanceThresholds.get("conjurResponse").get("warning")) {
                    // Warning threshold exceeded
                    alertSent = sendPerformanceAlert(
                        AlertType.CONJUR_RESPONSE_TIME,
                        String.format("Conjur response time of %.2f ms exceeds warning threshold (%.2f ms)",
                            value, performanceThresholds.get("conjurResponse").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            default:
                log.warn("Unknown performance metric: {}", metricName);
                break;
        }
        
        return alertSent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkAvailabilityThresholds(String metricName, double value, Map<String, String> tags) {
        log.debug("Checking availability thresholds for metric: {}, value: {}", metricName, value);
        
        // Get availability thresholds
        Map<String, Map<String, Double>> availabilityThresholds = alertThresholdProperties.getAvailability();
        boolean alertSent = false;
        
        // Create metadata map for alerts
        Map<String, Object> metadata = new HashMap<>();
        if (tags != null) {
            metadata.putAll(tags);
        }
        metadata.put("metricName", metricName);
        metadata.put("value", value);
        
        // Check thresholds based on metric name
        switch (metricName) {
            case "api.availability":
                // API availability (percentage)
                if (value < availabilityThresholds.get("api").get("critical")) {
                    // Critical threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.API_AVAILABILITY,
                        String.format("API availability of %.2f%% is below critical threshold (%.2f%%)",
                            value, availabilityThresholds.get("api").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value < availabilityThresholds.get("api").get("warning")) {
                    // Warning threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.API_AVAILABILITY,
                        String.format("API availability of %.2f%% is below warning threshold (%.2f%%)",
                            value, availabilityThresholds.get("api").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "conjur.availability":
                // Conjur availability (percentage)
                if (value < availabilityThresholds.get("conjur").get("critical")) {
                    // Critical threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.CONJUR_AVAILABILITY,
                        String.format("Conjur vault availability of %.2f%% is below critical threshold (%.2f%%)",
                            value, availabilityThresholds.get("conjur").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value < availabilityThresholds.get("conjur").get("warning")) {
                    // Warning threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.CONJUR_AVAILABILITY,
                        String.format("Conjur vault availability of %.2f%% is below warning threshold (%.2f%%)",
                            value, availabilityThresholds.get("conjur").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "redis.availability":
                // Redis availability (percentage)
                if (value < availabilityThresholds.get("redis").get("critical")) {
                    // Critical threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.REDIS_AVAILABILITY,
                        String.format("Redis cache availability of %.2f%% is below critical threshold (%.2f%%)",
                            value, availabilityThresholds.get("redis").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value < availabilityThresholds.get("redis").get("warning")) {
                    // Warning threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.REDIS_AVAILABILITY,
                        String.format("Redis cache availability of %.2f%% is below warning threshold (%.2f%%)",
                            value, availabilityThresholds.get("redis").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            case "database.availability":
                // Database availability (percentage)
                if (value < availabilityThresholds.get("database").get("critical")) {
                    // Critical threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.DATABASE_AVAILABILITY,
                        String.format("Database availability of %.2f%% is below critical threshold (%.2f%%)",
                            value, availabilityThresholds.get("database").get("critical")),
                        Severity.CRITICAL,
                        metadata
                    );
                } else if (value < availabilityThresholds.get("database").get("warning")) {
                    // Warning threshold exceeded (below minimum)
                    alertSent = sendAvailabilityAlert(
                        AlertType.DATABASE_AVAILABILITY,
                        String.format("Database availability of %.2f%% is below warning threshold (%.2f%%)",
                            value, availabilityThresholds.get("database").get("warning")),
                        Severity.HIGH,
                        metadata
                    );
                }
                break;
                
            default:
                log.warn("Unknown availability metric: {}", metricName);
                break;
        }
        
        return alertSent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acknowledgeAlert(String alertId, String acknowledgedBy, String notes) {
        log.info("Acknowledging alert: id={}, acknowledgedBy={}", alertId, acknowledgedBy);
        
        for (Alert alert : activeAlerts) {
            if (alert.getId().equals(alertId)) {
                try {
                    // Update alert status
                    AlertImpl alertImpl = (AlertImpl) alert;
                    alertImpl.setStatus(AlertStatus.ACKNOWLEDGED);
                    
                    // Update metadata
                    Map<String, Object> metadata = new HashMap<>(alertImpl.getMetadata());
                    metadata.put("acknowledgedBy", acknowledgedBy);
                    metadata.put("acknowledgedAt", System.currentTimeMillis());
                    metadata.put("acknowledgementNotes", notes);
                    alertImpl.setMetadata(metadata);
                    
                    log.info("Alert acknowledged successfully: id={}", alertId);
                    return true;
                } catch (ClassCastException e) {
                    log.error("Failed to cast Alert to AlertImpl: id={}, error={}", alertId, e.getMessage(), e);
                    return false;
                }
            }
        }
        
        log.warn("Alert not found for acknowledgement: id={}", alertId);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean resolveAlert(String alertId, String resolvedBy, String resolutionNotes) {
        log.info("Resolving alert: id={}, resolvedBy={}", alertId, resolvedBy);
        
        for (int i = 0; i < activeAlerts.size(); i++) {
            Alert alert = activeAlerts.get(i);
            if (alert.getId().equals(alertId)) {
                try {
                    // Update alert status
                    AlertImpl alertImpl = (AlertImpl) alert;
                    alertImpl.setStatus(AlertStatus.RESOLVED);
                    
                    // Update metadata
                    Map<String, Object> metadata = new HashMap<>(alertImpl.getMetadata());
                    metadata.put("resolvedBy", resolvedBy);
                    metadata.put("resolvedAt", System.currentTimeMillis());
                    metadata.put("resolutionNotes", resolutionNotes);
                    alertImpl.setMetadata(metadata);
                    
                    // Remove from active alerts
                    activeAlerts.remove(i);
                    
                    log.info("Alert resolved successfully: id={}", alertId);
                    return true;
                } catch (ClassCastException e) {
                    log.error("Failed to cast Alert to AlertImpl: id={}, error={}", alertId, e.getMessage(), e);
                    return false;
                }
            }
        }
        
        log.warn("Alert not found for resolution: id={}", alertId);
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Alert> getAlertHistory(long startTime, long endTime, AlertType alertType, Severity severity) {
        List<Alert> filteredAlerts = new ArrayList<>();
        
        for (Alert alert : alertHistory) {
            // Check if alert timestamp is within the specified time range
            if (alert.getTimestamp() < startTime || alert.getTimestamp() > endTime) {
                continue;
            }
            
            // Check if alert type matches (if specified)
            if (alertType != null && alert.getType() != alertType) {
                continue;
            }
            
            // Check if severity matches (if specified)
            if (severity != null && alert.getSeverity() != severity) {
                continue;
            }
            
            // Alert matches all criteria
            filteredAlerts.add(alert);
        }
        
        return filteredAlerts;
    }

    /**
     * Sends an alert to PagerDuty
     *
     * @param alert The alert to send
     * @return True if the alert was successfully sent, false otherwise
     */
    private boolean sendToPagerDuty(Alert alert) {
        log.info("Sending alert to PagerDuty: id={}, type={}, severity={}", 
            alert.getId(), alert.getType(), alert.getSeverity());
        
        try {
            // Create PagerDuty payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("routing_key", notificationProperties.getPagerDuty().getRoutingKey());
            payload.put("event_action", "trigger");
            
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("summary", alert.getMessage());
            eventPayload.put("severity", mapSeverityToPagerDutySeverity(alert.getSeverity()));
            eventPayload.put("source", "Payment API Monitoring");
            eventPayload.put("custom_details", alert.getMetadata());
            
            payload.put("payload", eventPayload);
            
            // Create HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            // Send request to PagerDuty
            restTemplate.postForEntity(
                notificationProperties.getPagerDuty().getEventApiUrl(),
                request,
                String.class
            );
            
            log.info("Successfully sent alert to PagerDuty: id={}", alert.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send alert to PagerDuty: id={}, error={}", alert.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Maps internal severity levels to PagerDuty severity levels
     *
     * @param severity Internal severity level
     * @return PagerDuty severity level
     */
    private String mapSeverityToPagerDutySeverity(Severity severity) {
        switch (severity) {
            case CRITICAL:
                return "critical";
            case HIGH:
                return "error";
            case MEDIUM:
                return "warning";
            case LOW:
                return "info";
            default:
                return "warning";
        }
    }

    /**
     * Sends an alert to Slack
     *
     * @param alert The alert to send
     * @return True if the alert was successfully sent, false otherwise
     */
    private boolean sendToSlack(Alert alert) {
        log.info("Sending alert to Slack: id={}, type={}, severity={}", 
            alert.getId(), alert.getType(), alert.getSeverity());
        
        try {
            // Create Slack payload
            Map<String, Object> payload = new HashMap<>();
            
            // Main text
            payload.put("text", String.format("[%s] %s Alert: %s", 
                alert.getSeverity(), 
                alert.getType().toString().replace("_", " "), 
                alert.getMessage()));
            
            // Create attachment with details
            List<Map<String, Object>> attachments = new ArrayList<>();
            Map<String, Object> attachment = new HashMap<>();
            
            attachment.put("title", alert.getType().toString().replace("_", " "));
            attachment.put("text", alert.getMessage());
            attachment.put("color", getSlackColorForSeverity(alert.getSeverity()));
            
            // Add fields from metadata
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Map.Entry<String, Object> entry : alert.getMetadata().entrySet()) {
                Map<String, Object> field = new HashMap<>();
                field.put("title", entry.getKey());
                field.put("value", entry.getValue().toString());
                field.put("short", true);
                fields.add(field);
            }
            
            // Add alert ID and timestamp fields
            Map<String, Object> idField = new HashMap<>();
            idField.put("title", "Alert ID");
            idField.put("value", alert.getId());
            idField.put("short", true);
            fields.add(idField);
            
            Map<String, Object> timeField = new HashMap<>();
            timeField.put("title", "Timestamp");
            timeField.put("value", alert.getTimestamp());
            timeField.put("short", true);
            fields.add(timeField);
            
            attachment.put("fields", fields);
            attachments.add(attachment);
            
            payload.put("attachments", attachments);
            
            // Create HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            // Send request to Slack
            restTemplate.postForEntity(
                notificationProperties.getSlack().getWebhookUrl(),
                request,
                String.class
            );
            
            log.info("Successfully sent alert to Slack: id={}", alert.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send alert to Slack: id={}, error={}", alert.getId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Maps severity levels to Slack attachment colors
     *
     * @param severity Severity level
     * @return Slack attachment color
     */
    private String getSlackColorForSeverity(Severity severity) {
        switch (severity) {
            case CRITICAL:
                return "#FF0000"; // Red
            case HIGH:
                return "#FFA500"; // Orange
            case MEDIUM:
                return "#FFFF00"; // Yellow
            case LOW:
                return "#008000"; // Green
            default:
                return "#808080"; // Gray
        }
    }

    /**
     * Sends an alert to email
     *
     * @param alert The alert to send
     * @return True if the alert was successfully sent, false otherwise
     */
    private boolean sendToEmail(Alert alert) {
        log.info("Sending alert to email: id={}, type={}, severity={}", 
            alert.getId(), alert.getType(), alert.getSeverity());
        
        try {
            // Create email payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", notificationProperties.getEmail().getRecipients());
            payload.put("subject", String.format("[%s] %s Alert: %s", 
                alert.getSeverity(), 
                alert.getType().toString().replace("_", " "), 
                alert.getMessage()));
            
            // Build email body
            StringBuilder body = new StringBuilder();
            body.append("<h2>").append(alert.getType().toString().replace("_", " ")).append(" Alert</h2>");
            body.append("<p><strong>Severity:</strong> ").append(alert.getSeverity()).append("</p>");
            body.append("<p><strong>Message:</strong> ").append(alert.getMessage()).append("</p>");
            body.append("<p><strong>Alert ID:</strong> ").append(alert.getId()).append("</p>");
            body.append("<p><strong>Timestamp:</strong> ").append(alert.getTimestamp()).append("</p>");
            
            // Add metadata section
            body.append("<h3>Additional Information:</h3>");
            body.append("<ul>");
            for (Map.Entry<String, Object> entry : alert.getMetadata().entrySet()) {
                body.append("<li><strong>").append(entry.getKey()).append(":</strong> ")
                    .append(entry.getValue().toString()).append("</li>");
            }
            body.append("</ul>");
            
            // Add action links
            body.append("<p>To acknowledge this alert, please click <a href=\"");
            body.append(notificationProperties.getEmail().getAcknowledgeUrl())
                .append("?alertId=").append(alert.getId());
            body.append("\">here</a>.</p>");
            
            payload.put("body", body.toString());
            
            // Create HTTP request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            
            // Send request to email service
            restTemplate.postForEntity(
                notificationProperties.getEmail().getServiceUrl(),
                request,
                String.class
            );
            
            log.info("Successfully sent alert to email: id={}", alert.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send alert to email: id={}, error={}", alert.getId(), e.getMessage(), e);
            return false;
        }
    }
}