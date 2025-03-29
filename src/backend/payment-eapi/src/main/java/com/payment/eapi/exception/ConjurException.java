package com.payment.eapi.exception;

/**
 * Custom exception for Conjur vault operation failures in the Payment-Eapi module.
 * This exception is thrown when there are issues with connecting to Conjur vault,
 * retrieving credentials, or other vault-related operations.
 */
public class ConjurException extends RuntimeException {
    
    /**
     * The error code associated with this Conjur exception
     */
    private String errorCode;
    
    /**
     * Constructor with error message
     *
     * @param message the error message
     */
    public ConjurException(String message) {
        super(message);
        this.errorCode = "CONJUR_ERROR";
    }
    
    /**
     * Constructor with error message and cause
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public ConjurException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CONJUR_ERROR";
    }
    
    /**
     * Constructor with error message and custom error code
     *
     * @param message the error message
     * @param errorCode the custom error code
     */
    public ConjurException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructor with error message, cause, and custom error code
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @param errorCode the custom error code
     */
    public ConjurException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Get the error code associated with this Conjur exception
     *
     * @return the error code for this Conjur exception
     */
    public String getErrorCode() {
        return errorCode;
    }
}