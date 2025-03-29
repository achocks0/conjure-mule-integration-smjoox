package com.payment.sapi.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.payment.sapi.service.AuditService;
import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;
import com.payment.common.monitoring.MetricsService;

/**
 * Implementation of the AuditService interface that provides methods for logging security-relevant events
 * in the Payment-Sapi service. This service is responsible for recording authentication attempts,
 * token validations, authorization decisions, and other security events for compliance and
 * security monitoring purposes.
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditServiceImpl.class);
    
    private static final String TOKEN_VALIDATION_EVENT = "TOKEN_VALIDATION";
    private static final String TOKEN_RENEWAL_EVENT = "TOKEN_RENEWAL";
    private static final String AUTHORIZATION_EVENT = "AUTHORIZATION";
    private static final String SECURITY_EVENT = "SECURITY";
    
    private final MetricsService metricsService;
    
    /**
     * Constructs an AuditServiceImpl with the required dependencies.
     *
     * @param metricsService service for recording security metrics
     */
    public AuditServiceImpl(MetricsService metricsService) {
        this.metricsService = metricsService;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void logTokenValidation(String tokenId, boolean success, String reason) {
        String maskedTokenId = maskToken(tokenId);
        
        if (success) {
            LOGGER.info("Event: {}, TokenId: {}, Status: SUCCESS", TOKEN_VALIDATION_EVENT, maskedTokenId);
        } else {
            LOGGER.warn("Event: {}, TokenId: {}, Status: FAILURE, Reason: {}", 
                    TOKEN_VALIDATION_EVENT, maskedTokenId, reason);
        }
        
        // Record metrics for monitoring
        metricsService.recordTokenValidation("unknown", success, 0); // Duration not available here
        metricsService.incrementCounter("security.events", new String[]{"type:" + TOKEN_VALIDATION_EVENT});
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void logTokenValidationDetail(Token token, ValidationResult validationResult) {
        String tokenId = token != null ? token.getTokenId() : "unknown";
        String clientId = token != null ? token.getSubject() : "unknown";
        String maskedTokenId = maskToken(tokenId);
        
        String status = "UNKNOWN";
        if (validationResult != null) {
            if (validationResult.isValid()) {
                status = "VALID";
            } else if (validationResult.isExpired()) {
                status = "EXPIRED";
            } else if (validationResult.isForbidden()) {
                status = "FORBIDDEN";
            } else if (validationResult.isRenewed()) {
                status = "RENEWED";
            } else {
                status = "INVALID";
            }
        }
        
        String errorMsg = validationResult != null && validationResult.getErrorMessage() != null 
                ? validationResult.getErrorMessage() : "";
        
        if (validationResult != null && validationResult.isValid()) {
            LOGGER.info("Event: {}, TokenId: {}, ClientId: {}, Status: {}", 
                    TOKEN_VALIDATION_EVENT, maskedTokenId, clientId, status);
        } else {
            LOGGER.warn("Event: {}, TokenId: {}, ClientId: {}, Status: {}, Error: {}", 
                    TOKEN_VALIDATION_EVENT, maskedTokenId, clientId, status, errorMsg);
        }
        
        // Record metrics for monitoring
        boolean isValid = validationResult != null && validationResult.isValid();
        metricsService.recordTokenValidation(clientId, isValid, 0); // Duration not available here
        metricsService.incrementCounter("security.events", new String[]{"type:" + TOKEN_VALIDATION_EVENT});
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void logAuthorizationDecision(String clientId, String resource, String action, boolean allowed) {
        if (allowed) {
            LOGGER.info("Event: {}, ClientId: {}, Resource: {}, Action: {}, Decision: ALLOWED", 
                    AUTHORIZATION_EVENT, clientId, resource, action);
        } else {
            LOGGER.warn("Event: {}, ClientId: {}, Resource: {}, Action: {}, Decision: DENIED", 
                    AUTHORIZATION_EVENT, clientId, resource, action);
        }
        
        // Add to MDC for correlation in subsequent logs
        MDC.put("clientId", clientId);
        MDC.put("resource", resource);
        MDC.put("action", action);
        MDC.put("allowed", String.valueOf(allowed));
        
        // Record metrics for monitoring
        metricsService.incrementCounter("security.events", 
                new String[]{"type:" + AUTHORIZATION_EVENT, "result:" + (allowed ? "allowed" : "denied")});
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void logTokenRenewal(String oldTokenId, String newTokenId, String clientId) {
        String maskedOldTokenId = maskToken(oldTokenId);
        String maskedNewTokenId = maskToken(newTokenId);
        
        LOGGER.info("Event: {}, OldTokenId: {}, NewTokenId: {}, ClientId: {}", 
                TOKEN_RENEWAL_EVENT, maskedOldTokenId, maskedNewTokenId, clientId);
        
        // Record metrics for monitoring
        metricsService.incrementCounter("security.events", new String[]{"type:" + TOKEN_RENEWAL_EVENT});
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void logSecurityEvent(String eventType, String clientId, String details) {
        String sanitizedDetails = sanitizeDetails(details);
        
        LOGGER.info("Event: {}, Type: {}, ClientId: {}, Details: {}", 
                SECURITY_EVENT, eventType, clientId, sanitizedDetails);
        
        // Record metrics for monitoring
        metricsService.incrementCounter("security.events", new String[]{"type:" + eventType});
    }
    
    /**
     * Masks a token ID to prevent sensitive data exposure in logs.
     * 
     * @param tokenId the token ID to mask
     * @return the masked token ID
     */
    private String maskToken(String tokenId) {
        if (tokenId == null || tokenId.isEmpty()) {
            return "[EMPTY]";
        }
        
        if (tokenId.length() < 8) {
            // For very short tokens, mask all but first and last char
            return tokenId.substring(0, 1) + "***" + tokenId.substring(tokenId.length() - 1);
        }
        
        // For longer tokens, keep first 4 and last 4 chars
        return tokenId.substring(0, 4) + "***" + tokenId.substring(tokenId.length() - 4);
    }
    
    /**
     * Sanitizes log details to remove any potentially sensitive information.
     * 
     * @param details the details to sanitize
     * @return the sanitized details
     */
    private String sanitizeDetails(String details) {
        if (details == null) {
            return "";
        }
        
        // Remove potential sensitive patterns
        String sanitized = details
                .replaceAll("(?i)token[=:]\\s*[^\\s,;]+", "token=[REDACTED]")
                .replaceAll("(?i)credential[=:]\\s*[^\\s,;]+", "credential=[REDACTED]")
                .replaceAll("(?i)password[=:]\\s*[^\\s,;]+", "password=[REDACTED]")
                .replaceAll("(?i)secret[=:]\\s*[^\\s,;]+", "secret=[REDACTED]")
                .replaceAll("Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*", "Bearer [REDACTED]");
        
        return sanitized;
    }
}