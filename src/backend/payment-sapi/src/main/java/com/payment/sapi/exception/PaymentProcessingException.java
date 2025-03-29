package com.payment.sapi.exception;

import java.lang.RuntimeException;
import lombok.Getter;

/**
 * Custom exception class for payment processing failures in the Payment-Sapi service.
 * This exception is thrown when payment transactions fail due to issues such as 
 * validation errors, duplicate references, backend processing failures, or data inconsistencies.
 */
@Getter
public class PaymentProcessingException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * Constructor that takes an error message.
     *
     * @param message the error message
     */
    public PaymentProcessingException(String message) {
        super(message);
        this.errorCode = "PAYMENT_ERROR";
    }
    
    /**
     * Constructor that takes an error message and a cause.
     *
     * @param message the error message
     * @param cause the cause of the exception
     */
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PAYMENT_ERROR";
    }
    
    /**
     * Constructor that takes an error message and a custom error code.
     *
     * @param message the error message
     * @param errorCode the custom error code
     */
    public PaymentProcessingException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructor that takes an error message, a cause, and a custom error code.
     *
     * @param message the error message
     * @param cause the cause of the exception
     * @param errorCode the custom error code
     */
    public PaymentProcessingException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}