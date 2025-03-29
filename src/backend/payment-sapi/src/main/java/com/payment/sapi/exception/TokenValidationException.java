package com.payment.sapi.exception;

import java.lang.RuntimeException;
import lombok.Getter;

/**
 * Custom exception class that represents token validation failures in the Payment-Sapi component.
 * This exception is thrown when JWT token validation fails due to issues such as 
 * invalid signature, token expiration, or insufficient permissions.
 */
@Getter
public class TokenValidationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    private final String errorCode;
    
    /**
     * Constructor that creates a TokenValidationException with a specific error message.
     *
     * @param message The error message
     */
    public TokenValidationException(String message) {
        super(message);
        this.errorCode = "TOKEN_INVALID";
    }
    
    /**
     * Constructor that creates a TokenValidationException with a specific error message and error code.
     *
     * @param message The error message
     * @param errorCode The specific error code
     */
    public TokenValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructor that creates a TokenValidationException with a specific error message and cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TOKEN_INVALID";
    }
    
    /**
     * Constructor that creates a TokenValidationException with a specific error message, cause, and error code.
     *
     * @param message The error message
     * @param cause The cause of the exception
     * @param errorCode The specific error code
     */
    public TokenValidationException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}