package com.payment.eapi.exception;

import com.payment.common.exception.CommonExceptionHandler;
import com.payment.common.model.ErrorResponse;
import com.payment.eapi.exception.AuthenticationException;
import com.payment.eapi.exception.ConjurException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global exception handler for the Payment-Eapi module that extends the CommonExceptionHandler
 * to provide specialized handling for module-specific exceptions.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends CommonExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles AuthenticationException by creating a standardized error response with 401 Unauthorized status
     *
     * @param ex The authentication exception
     * @param request The web request
     * @return Error response with 401 Unauthorized status
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "AUTH_ERROR")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
                
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }
    
    /**
     * Handles ConjurException by creating a standardized error response with 503 Service Unavailable status
     *
     * @param ex The Conjur exception
     * @param request The web request
     * @return Error response with 503 Service Unavailable status
     */
    @ExceptionHandler(ConjurException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorResponse> handleConjurException(ConjurException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        logException(ex, requestId, "ERROR");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "CONJUR_ERROR")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
                
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    /**
     * Handles security-related exceptions by creating a standardized error response with 403 Forbidden status
     *
     * @param ex The security exception
     * @param request The web request
     * @return Error response with 403 Forbidden status
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex, WebRequest request) {
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
     * Handles missing required request headers by creating a standardized error response with 400 Bad Request status
     *
     * @param ex The missing request header exception
     * @param request The web request
     * @return Error response with 400 Bad Request status
     */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
            org.springframework.web.bind.MissingRequestHeaderException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("MISSING_HEADER")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
                
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}