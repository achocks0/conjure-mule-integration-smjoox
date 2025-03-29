package com.payment.sapi.service;

import com.payment.sapi.model.Token;
import com.payment.sapi.model.ValidationResult;

/**
 * Interface defining methods for logging security-relevant events in the Payment-Sapi service.
 * This service is responsible for recording authentication attempts, token validations,
 * authorization decisions, and other security events for compliance and security monitoring
 * purposes.
 * <p>
 * The audit logs produced by this service are crucial for:
 * <ul>
 *   <li>Security incident investigation</li>
 *   <li>Compliance with regulatory requirements (PCI-DSS, SOC2)</li>
 *   <li>Monitoring for unusual authentication or authorization patterns</li>
 *   <li>Creating an audit trail of security-relevant activities</li>
 * </ul>
 * <p>
 * Implementations of this interface must ensure sensitive data like credentials or complete
 * tokens are never logged directly, following security best practices for audit logging.
 */
public interface AuditService {

    /**
     * Logs a token validation event for audit purposes. This method logs basic information
     * about the validation attempt without including sensitive token contents.
     *
     * @param tokenId a unique identifier for the token being validated (should be masked or truncated)
     * @param success indicates whether the validation was successful
     * @param reason  description of why validation failed (if applicable)
     */
    void logTokenValidation(String tokenId, boolean success, String reason);

    /**
     * Logs detailed information about a token validation event. This method extracts relevant
     * information from the token and validation result for comprehensive audit logging.
     *
     * @param token            the token that was validated (sensitive data will be masked)
     * @param validationResult the result of the token validation operation
     */
    void logTokenValidationDetail(Token token, ValidationResult validationResult);

    /**
     * Logs an authorization decision for audit purposes. Records whether a specific client
     * was allowed to access a resource or perform an action.
     *
     * @param clientId the identifier of the client requesting access
     * @param resource the resource being accessed
     * @param action   the action being performed on the resource
     * @param allowed  indicates whether access was allowed or denied
     */
    void logAuthorizationDecision(String clientId, String resource, String action, boolean allowed);

    /**
     * Logs a token renewal event for audit purposes. This method records that a token
     * was renewed, generating a new token to replace an expired one.
     *
     * @param oldTokenId the identifier of the token being replaced
     * @param newTokenId the identifier of the newly issued token
     * @param clientId   the identifier of the client for whom the token was renewed
     */
    void logTokenRenewal(String oldTokenId, String newTokenId, String clientId);

    /**
     * Logs a general security event for audit purposes. This method can be used for
     * security-relevant events that don't fit into the other specialized categories.
     *
     * @param eventType the type of security event being logged
     * @param clientId  the identifier of the client associated with the event
     * @param details   additional details about the event (should not include sensitive data)
     */
    void logSecurityEvent(String eventType, String clientId, String details);
}