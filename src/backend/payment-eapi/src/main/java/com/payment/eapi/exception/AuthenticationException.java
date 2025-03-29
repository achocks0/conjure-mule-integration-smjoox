package com.payment.eapi.exception;

/**
 * Custom exception for authentication failures in the Payment-Eapi module.
 * This exception is thrown when client authentication fails due to invalid credentials,
 * missing required authentication parameters, or other authentication-related issues.
 */
public class AuthenticationException extends RuntimeException {
    
    private String errorCode;
    
    /**
     * Constructor with error message.
     * 
     * @param message The error message
     */
    public AuthenticationException(String message) {
        super(message);
        this.errorCode = "AUTH_ERROR";
    }
    
    /**
     * Constructor with error message and cause.
     * 
     * @param message The error message
     * @param cause The cause of the exception
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "AUTH_ERROR";
    }
    
    /**
     * Constructor with error message and custom error code.
     * 
     * @param message The error message
     * @param errorCode The custom error code
     */
    public AuthenticationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructor with error message, cause, and custom error code.
     * 
     * @param message The error message
     * @param cause The cause of the exception
     * @param errorCode The custom error code
     */
    public AuthenticationException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Get the error code associated with this authentication exception.
     * 
     * @return The error code for this authentication exception
     */
    public String getErrorCode() {
        return errorCode;
    }
}