package com.payment.sapi.exception;

import com.payment.common.exception.CommonExceptionHandler;
import com.payment.common.model.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.stream.Collectors;

/**
 * Global exception handler for the Payment-Sapi module that extends the CommonExceptionHandler
 * to provide specialized handling for module-specific exceptions such as TokenValidationException
 * and PaymentProcessingException. Ensures consistent error responses across the API.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends CommonExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles token validation exceptions returning a 401 Unauthorized response.
     *
     * @param ex The TokenValidationException
     * @param request The web request
     * @return ResponseEntity with appropriate error response
     */
    @ExceptionHandler(TokenValidationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorResponse> handleTokenValidationException(
            TokenValidationException ex, WebRequest request) {
        
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "TOKEN_INVALID")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handles payment processing exceptions returning a 500 Internal Server Error response.
     *
     * @param ex The PaymentProcessingException
     * @param request The web request
     * @return ResponseEntity with appropriate error response
     */
    @ExceptionHandler(PaymentProcessingException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorResponse> handlePaymentProcessingException(
            PaymentProcessingException ex, WebRequest request) {
        
        String requestId = extractRequestId(request);
        logException(ex, requestId, "ERROR");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("PAYMENT_ERROR")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles security exceptions returning a 403 Forbidden response.
     *
     * @param ex The SecurityException
     * @param request The web request
     * @return ResponseEntity with appropriate error response
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex, WebRequest request) {
        
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    /**
     * Handles validation errors for request bodies returning a 400 Bad Request response.
     *
     * @param ex The MethodArgumentNotValidException
     * @param request The web request
     * @return ResponseEntity with appropriate error response
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            org.springframework.web.bind.MethodArgumentNotValidException ex, WebRequest request) {
        
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(errorMessage)
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}