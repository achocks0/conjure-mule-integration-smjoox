package com.payment.common.monitoring;

/**
 * Interface defining methods for collecting and recording metrics related to authentication,
 * token operations, API performance, and Conjur vault operations. This service provides a
 * standardized way to monitor security and performance aspects of the Payment API Security
 * Enhancement project.
 * 
 * Implementations of this interface integrate with monitoring systems like Prometheus through
 * Micrometer to provide real-time visibility into the application's behavior and performance.
 */
public interface MetricsService {
    
    /**
     * Records an authentication attempt with its outcome for monitoring authentication success/failure rates.
     * This method is used to track security metrics related to client authentication attempts.
     *
     * @param clientId The ID of the client making the authentication attempt
     * @param success Whether the authentication attempt was successful
     */
    void recordAuthenticationAttempt(String clientId, boolean success);
    
    /**
     * Records a token generation event for monitoring token creation rates and performance.
     * This method is used to track metrics related to JWT token generation operations.
     *
     * @param clientId The ID of the client for whom the token is generated
     * @param durationMs The duration of the token generation operation in milliseconds
     */
    void recordTokenGeneration(String clientId, long durationMs);
    
    /**
     * Records a token validation event for monitoring token validation rates and results.
     * This method is used to track metrics related to JWT token validation operations.
     *
     * @param clientId The ID of the client whose token is being validated
     * @param valid Whether the token was valid
     * @param durationMs The duration of the token validation operation in milliseconds
     */
    void recordTokenValidation(String clientId, boolean valid, long durationMs);
    
    /**
     * Records an API request with its outcome and duration for monitoring API performance.
     * This method is used to track metrics related to API request processing.
     *
     * @param endpoint The API endpoint that was called
     * @param method The HTTP method used (GET, POST, etc.)
     * @param statusCode The HTTP status code returned
     * @param durationMs The duration of the API request processing in milliseconds
     */
    void recordApiRequest(String endpoint, String method, int statusCode, long durationMs);
    
    /**
     * Records a Conjur vault operation for monitoring credential access and performance.
     * This method is used to track metrics related to Conjur vault operations.
     *
     * @param operationType The type of operation performed (e.g., "retrieve", "authenticate")
     * @param success Whether the operation was successful
     * @param durationMs The duration of the Conjur operation in milliseconds
     */
    void recordConjurOperation(String operationType, boolean success, long durationMs);
    
    /**
     * Records a credential rotation event for monitoring rotation success rates.
     * This method is used to track metrics related to credential rotation operations.
     *
     * @param clientId The ID of the client whose credentials are being rotated
     * @param status The status of the rotation (e.g., "started", "completed", "failed")
     * @param durationMs The duration of the credential rotation operation in milliseconds
     */
    void recordCredentialRotation(String clientId, String status, long durationMs);
    
    /**
     * Increments a custom counter metric for generic counting needs.
     * This method is used for custom metrics that don't fit the predefined categories.
     *
     * @param name The name of the counter metric
     * @param tags Additional tags to categorize the metric
     */
    void incrementCounter(String name, String[] tags);
    
    /**
     * Records a value for a gauge metric to track variable values over time.
     * This method is used for metrics that can increase and decrease over time.
     *
     * @param name The name of the gauge metric
     * @param value The value to record
     * @param tags Additional tags to categorize the metric
     */
    void recordGaugeValue(String name, double value, String[] tags);
    
    /**
     * Records a timing measurement for performance monitoring.
     * This method is used for custom performance metrics that don't fit the predefined categories.
     *
     * @param name The name of the timing metric
     * @param durationMs The duration to record in milliseconds
     * @param tags Additional tags to categorize the metric
     */
    void recordTiming(String name, long durationMs, String[] tags);
}