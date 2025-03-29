package com.payment.common.model;

import java.io.Serializable; // JDK 11
import java.util.Date; // JDK 11
import lombok.Builder; // 1.18.22
import lombok.Data; // 1.18.22
import lombok.NoArgsConstructor; // 1.18.22
import lombok.AllArgsConstructor; // 1.18.22

/**
 * A standardized error response model that provides a consistent structure for error responses
 * across the payment API system. Used by exception handlers to create error responses with 
 * appropriate error codes, messages, and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse implements Serializable {
    
    /**
     * Unique code identifying the type of error that occurred
     */
    private String errorCode;
    
    /**
     * Human-readable description of the error
     */
    private String message;
    
    /**
     * Unique identifier for the request that generated this error,
     * useful for tracing and debugging
     */
    private String requestId;
    
    /**
     * When the error occurred
     */
    private Date timestamp;
}