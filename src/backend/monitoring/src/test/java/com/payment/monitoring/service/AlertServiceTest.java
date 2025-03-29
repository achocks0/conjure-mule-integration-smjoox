package com.payment.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper; // 2.13.x
import com.payment.monitoring.config.AlertThresholdProperties;
import com.payment.monitoring.config.NotificationProperties;
import com.payment.monitoring.model.Alert;
import com.payment.monitoring.model.AlertStatus;
import com.payment.monitoring.model.AlertType;
import com.payment.monitoring.model.Severity;
import com.payment.monitoring.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate; // 5.6.x

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlertServiceTest {

    @Mock
    private RestTemplate restTemplate;
    
    @Mock
    private NotificationProperties notificationProperties;
    
    @Mock
    private AlertThresholdProperties alertThresholdProperties;
    
    @Mock
    private ObjectMapper objectMapper;
    
    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        // Configure notification properties for test
        NotificationProperties.PagerDutyProperties pagerDutyProps = mock(NotificationProperties.PagerDutyProperties.class);
        when(pagerDutyProps.getEventApiUrl()).thenReturn("https://events.pagerduty.com/v2/enqueue");
        when(pagerDutyProps.getRoutingKey()).thenReturn("test-routing-key");
        
        NotificationProperties.SlackProperties slackProps = mock(NotificationProperties.SlackProperties.class);
        when(slackProps.getWebhookUrl()).thenReturn("https://hooks.slack.com/services/test/webhook");
        
        NotificationProperties.EmailProperties emailProps = mock(NotificationProperties.EmailProperties.class);
        when(emailProps.getServiceUrl()).thenReturn("https://email-service/send");
        when(emailProps.getRecipients()).thenReturn(List.of("alerts@example.com"));
        when(emailProps.getAcknowledgeUrl()).thenReturn("https://monitoring.example.com/acknowledge");
        
        when(notificationProperties.getPagerDuty()).thenReturn(pagerDutyProps);
        when(notificationProperties.getSlack()).thenReturn(slackProps);
        when(notificationProperties.getEmail()).thenReturn(emailProps);
        
        // Configure REST template to return successful responses
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));
        
        // Initialize the service with mocked dependencies
        alertService = new AlertServiceImpl(restTemplate, notificationProperties, alertThresholdProperties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        // Reset all mocks
        reset(restTemplate, notificationProperties, alertThresholdProperties, objectMapper);
    }

    @Test
    void testSendSecurityAlert_Critical() {
        // Create test metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("testKey", "testValue");
        
        // Call the method with CRITICAL severity
        boolean result = alertService.sendSecurityAlert(
            AlertType.AUTHENTICATION_FAILURE, 
            "Test security alert", 
            Severity.CRITICAL, 
            metadata
        );
        
        // Verify the result
        assertTrue(result);
        
        // Verify PagerDuty, Slack, and Email notification methods were called
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the alert was added to active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        // Verify alert properties
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.AUTHENTICATION_FAILURE, alert.getType());
        assertEquals("Test security alert", alert.getMessage());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        assertEquals("testValue", alert.getMetadata().get("testKey"));
    }

    @Test
    void testSendSecurityAlert_High() {
        // Create test metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("testKey", "testValue");
        
        // Call the method with HIGH severity
        boolean result = alertService.sendSecurityAlert(
            AlertType.UNAUTHORIZED_ACCESS, 
            "Test security alert", 
            Severity.HIGH, 
            metadata
        );
        
        // Verify the result
        assertTrue(result);
        
        // Verify that Slack and Email were called but not PagerDuty (only 2 calls)
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the alert was added to active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        // Verify alert properties
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.UNAUTHORIZED_ACCESS, alert.getType());
        assertEquals("Test security alert", alert.getMessage());
        assertEquals(Severity.HIGH, alert.getSeverity());
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
    }

    @Test
    void testSendPerformanceAlert_Critical() {
        // Create test metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("testKey", "testValue");
        
        // Call the method with CRITICAL severity
        boolean result = alertService.sendPerformanceAlert(
            AlertType.API_RESPONSE_TIME, 
            "Test performance alert", 
            Severity.CRITICAL, 
            metadata
        );
        
        // Verify the result
        assertTrue(result);
        
        // Verify that PagerDuty, Slack, and Email were all called
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the alert was added to active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        // Verify alert properties
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.API_RESPONSE_TIME, alert.getType());
        assertEquals("Test performance alert", alert.getMessage());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
    }

    @Test
    void testSendAvailabilityAlert_Critical() {
        // Create test metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("testKey", "testValue");
        
        // Call the method with CRITICAL severity
        boolean result = alertService.sendAvailabilityAlert(
            AlertType.API_AVAILABILITY, 
            "Test availability alert", 
            Severity.CRITICAL, 
            metadata
        );
        
        // Verify the result
        assertTrue(result);
        
        // Verify that PagerDuty, Slack, and Email were all called
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the alert was added to active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        // Verify alert properties
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.API_AVAILABILITY, alert.getType());
        assertEquals("Test availability alert", alert.getMessage());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
    }

    @Test
    void testCheckSecurityThresholds_AuthenticationFailure_Critical() {
        // Create test tags map
        Map<String, String> tags = new HashMap<>();
        tags.put("service", "payment-api");
        
        // Setup mocks to simulate threshold values for authentication failure
        Map<String, Map<String, Double>> securityThresholds = new HashMap<>();
        Map<String, Double> authThresholds = new HashMap<>();
        authThresholds.put("critical", 10.0);
        authThresholds.put("warning", 5.0);
        securityThresholds.put("authentication", authThresholds);
        when(alertThresholdProperties.getSecurity()).thenReturn(securityThresholds);
        
        // Call the method with a value above critical threshold
        boolean result = alertService.checkSecurityThresholds("authentication.failure.rate", 15.0, tags);
        
        // Verify that sendSecurityAlert was called with appropriate parameters
        ArgumentCaptor<AlertType> alertTypeCaptor = ArgumentCaptor.forClass(AlertType.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Severity> severityCaptor = ArgumentCaptor.forClass(Severity.class);
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the result is true (threshold exceeded)
        assertTrue(result);
        
        // Verify alert details through inspecting active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.AUTHENTICATION_FAILURE, alert.getType());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
        assertTrue(alert.getMessage().contains("critical threshold"));
    }

    @Test
    void testCheckSecurityThresholds_AuthenticationFailure_Warning() {
        // Create test tags map
        Map<String, String> tags = new HashMap<>();
        tags.put("service", "payment-api");
        
        // Setup mocks to simulate threshold values for authentication failure
        Map<String, Map<String, Double>> securityThresholds = new HashMap<>();
        Map<String, Double> authThresholds = new HashMap<>();
        authThresholds.put("critical", 10.0);
        authThresholds.put("warning", 5.0);
        securityThresholds.put("authentication", authThresholds);
        when(alertThresholdProperties.getSecurity()).thenReturn(securityThresholds);
        
        // Call the method with a value above warning but below critical threshold
        boolean result = alertService.checkSecurityThresholds("authentication.failure.rate", 7.0, tags);
        
        // Verify that sendSecurityAlert was called with appropriate parameters
        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the result is true (threshold exceeded)
        assertTrue(result);
        
        // Verify alert details through inspecting active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.AUTHENTICATION_FAILURE, alert.getType());
        assertEquals(Severity.HIGH, alert.getSeverity());
        assertTrue(alert.getMessage().contains("warning threshold"));
    }

    @Test
    void testCheckPerformanceThresholds_ApiResponseTime_Critical() {
        // Create test tags map
        Map<String, String> tags = new HashMap<>();
        tags.put("service", "payment-api");
        
        // Setup mocks to simulate threshold values for API response time
        Map<String, Map<String, Double>> performanceThresholds = new HashMap<>();
        Map<String, Double> apiResponseThresholds = new HashMap<>();
        apiResponseThresholds.put("critical", 500.0);
        apiResponseThresholds.put("warning", 300.0);
        performanceThresholds.put("apiResponse", apiResponseThresholds);
        when(alertThresholdProperties.getPerformance()).thenReturn(performanceThresholds);
        
        // Call the method with a value above critical threshold
        boolean result = alertService.checkPerformanceThresholds("api.response.time", 600.0, tags);
        
        // Verify that sendPerformanceAlert was called with appropriate parameters
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the result is true (threshold exceeded)
        assertTrue(result);
        
        // Verify alert details through inspecting active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.API_RESPONSE_TIME, alert.getType());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
        assertTrue(alert.getMessage().contains("critical threshold"));
    }

    @Test
    void testCheckAvailabilityThresholds_ApiAvailability_Critical() {
        // Create test tags map
        Map<String, String> tags = new HashMap<>();
        tags.put("service", "payment-api");
        
        // Setup mocks to simulate threshold values for API availability
        Map<String, Map<String, Double>> availabilityThresholds = new HashMap<>();
        Map<String, Double> apiAvailabilityThresholds = new HashMap<>();
        apiAvailabilityThresholds.put("critical", 99.0);
        apiAvailabilityThresholds.put("warning", 99.5);
        availabilityThresholds.put("api", apiAvailabilityThresholds);
        when(alertThresholdProperties.getAvailability()).thenReturn(availabilityThresholds);
        
        // Call the method with a value below critical threshold
        boolean result = alertService.checkAvailabilityThresholds("api.availability", 98.5, tags);
        
        // Verify that sendAvailabilityAlert was called with appropriate parameters
        verify(restTemplate, times(3)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        
        // Verify the result is true (threshold exceeded)
        assertTrue(result);
        
        // Verify alert details through inspecting active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.API_AVAILABILITY, alert.getType());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
        assertTrue(alert.getMessage().contains("critical threshold"));
    }

    @Test
    void testGetActiveAlerts() {
        // Send multiple test alerts to create active alerts
        alertService.sendSecurityAlert(AlertType.AUTHENTICATION_FAILURE, "Test alert 1", Severity.CRITICAL, null);
        alertService.sendPerformanceAlert(AlertType.API_RESPONSE_TIME, "Test alert 2", Severity.HIGH, null);
        alertService.sendAvailabilityAlert(AlertType.API_AVAILABILITY, "Test alert 3", Severity.MEDIUM, null);
        
        // Call alertService.getActiveAlerts()
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        
        // Verify that the returned list contains all the expected alerts
        assertEquals(3, activeAlerts.size());
        
        // Verify the alerts have the correct types
        List<AlertType> alertTypes = new ArrayList<>();
        for (Alert alert : activeAlerts) {
            alertTypes.add(alert.getType());
        }
        
        assertTrue(alertTypes.contains(AlertType.AUTHENTICATION_FAILURE));
        assertTrue(alertTypes.contains(AlertType.API_RESPONSE_TIME));
        assertTrue(alertTypes.contains(AlertType.API_AVAILABILITY));
    }

    @Test
    void testAcknowledgeAlert() {
        // Send a test alert to create an active alert
        alertService.sendSecurityAlert(AlertType.AUTHENTICATION_FAILURE, "Test alert", Severity.CRITICAL, null);
        
        // Get the alert ID
        String alertId = alertService.getActiveAlerts().get(0).getId();
        
        // Call alertService.acknowledgeAlert with the alert ID, acknowledger name, and notes
        boolean result = alertService.acknowledgeAlert(alertId, "test-user", "Investigating the issue");
        
        // Verify that the result is true (alert acknowledged)
        assertTrue(result);
        
        // Verify that the alert status is updated to ACKNOWLEDGED
        Alert alert = alertService.getActiveAlerts().get(0);
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        
        // Verify that the alert metadata contains acknowledgement information
        assertEquals("test-user", alert.getMetadata().get("acknowledgedBy"));
        assertNotNull(alert.getMetadata().get("acknowledgedAt"));
        assertEquals("Investigating the issue", alert.getMetadata().get("acknowledgementNotes"));
    }

    @Test
    void testResolveAlert() {
        // Send a test alert to create an active alert
        alertService.sendSecurityAlert(AlertType.AUTHENTICATION_FAILURE, "Test alert", Severity.CRITICAL, null);
        
        // Get the alert ID
        String alertId = alertService.getActiveAlerts().get(0).getId();
        
        // Call alertService.resolveAlert with the alert ID, resolver name, and resolution notes
        boolean result = alertService.resolveAlert(alertId, "test-user", "Fixed the authentication issue");
        
        // Verify that the result is true (alert resolved)
        assertTrue(result);
        
        // Verify that the alert is removed from active alerts
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(0, activeAlerts.size());
        
        // Verify that the alert status is updated to RESOLVED
        List<Alert> alertHistory = alertService.getAlertHistory(0, System.currentTimeMillis() + 1000, null, null);
        assertEquals(1, alertHistory.size());
        assertEquals(AlertStatus.RESOLVED, alertHistory.get(0).getStatus());
        
        // Verify that the alert metadata contains resolution information
        assertEquals("test-user", alertHistory.get(0).getMetadata().get("resolvedBy"));
        assertNotNull(alertHistory.get(0).getMetadata().get("resolvedAt"));
        assertEquals("Fixed the authentication issue", alertHistory.get(0).getMetadata().get("resolutionNotes"));
    }

    @Test
    void testGetAlertHistory() {
        // Send multiple test alerts with different types and severities
        alertService.sendSecurityAlert(AlertType.AUTHENTICATION_FAILURE, "Security alert", Severity.CRITICAL, null);
        alertService.sendPerformanceAlert(AlertType.API_RESPONSE_TIME, "Performance alert", Severity.HIGH, null);
        alertService.sendAvailabilityAlert(AlertType.API_AVAILABILITY, "Availability alert", Severity.MEDIUM, null);
        
        // Call alertService.getAlertHistory with appropriate time range, alert type, and severity filters
        List<Alert> securityAlerts = alertService.getAlertHistory(0, System.currentTimeMillis() + 1000, 
                AlertType.AUTHENTICATION_FAILURE, null);
        List<Alert> criticalAlerts = alertService.getAlertHistory(0, System.currentTimeMillis() + 1000, 
                null, Severity.CRITICAL);
        List<Alert> allAlerts = alertService.getAlertHistory(0, System.currentTimeMillis() + 1000, 
                null, null);
        
        // Verify that the returned list contains only the alerts matching the specified criteria
        assertEquals(1, securityAlerts.size());
        assertEquals(AlertType.AUTHENTICATION_FAILURE, securityAlerts.get(0).getType());
        
        assertEquals(1, criticalAlerts.size());
        assertEquals(Severity.CRITICAL, criticalAlerts.get(0).getSeverity());
        
        assertEquals(3, allAlerts.size());
    }

    @Test
    void testNotificationFailure() {
        // Configure restTemplate to return error responses for notification requests
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("Notification service unavailable"));
        
        // Create test metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "unit-test");
        
        // Call alertService.sendSecurityAlert with test parameters
        boolean result = alertService.sendSecurityAlert(
            AlertType.AUTHENTICATION_FAILURE, 
            "Test notification failure", 
            Severity.CRITICAL, 
            metadata
        );
        
        // Verify that the alert is still added to active alerts despite notification failures
        List<Alert> activeAlerts = alertService.getActiveAlerts();
        assertEquals(1, activeAlerts.size());
        
        Alert alert = activeAlerts.get(0);
        assertEquals(AlertType.AUTHENTICATION_FAILURE, alert.getType());
        assertEquals("Test notification failure", alert.getMessage());
        assertEquals(Severity.CRITICAL, alert.getSeverity());
        assertEquals("unit-test", alert.getMetadata().get("source"));
        
        // The result should be false since notifications failed
        assertFalse(result);
    }
}