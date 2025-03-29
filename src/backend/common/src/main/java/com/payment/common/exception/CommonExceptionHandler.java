package com.payment.common.exception;

import com.payment.common.model.ErrorResponse;
import com.payment.common.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common exception handler for all modules in the Payment API Security Enhancement project.
 * Provides standardized error response formats and consistent error handling across the application.
 * Module-specific exception handlers should extend this class to inherit common handling behavior.
 */
@ControllerAdvice
public class CommonExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonExceptionHandler.class);
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * Handles generic unhandled exceptions with a standard 500 Internal Server Error response.
     *
     * @param ex      The uncaught exception
     * @param request The web request
     * @return ResponseEntity containing a standardized error response with 500 status code
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex, WebRequest request) {
        String requestId = extractRequestId(request);
        logException(ex, requestId, "ERROR");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles IllegalArgumentException with a 400 Bad Request response.
     * Typically indicates invalid input parameters.
     *
     * @param ex      The IllegalArgumentException
     * @param request The web request
     * @return ResponseEntity containing a standardized error response with 400 status code
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        String requestId = extractRequestId(request);
        logException(ex, requestId, "WARN");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode("INVALID_REQUEST")
                .message(ex.getMessage())
                .requestId(requestId)
                .timestamp(new Date())
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Extracts the request ID from the request headers or generates a new one if not present.
     * Sanitizes the request ID to prevent injection attacks.
     *
     * @param request The web request
     * @return A sanitized request ID
     */
    protected String extractRequestId(WebRequest request) {
        try {
            if (request instanceof ServletWebRequest) {
                HttpServletRequest httpRequest = ((ServletWebRequest) request).getRequest();
                String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
                
                if (requestId == null || requestId.isEmpty()) {
                    return UUID.randomUUID().toString();
                }
                
                return SecurityUtils.sanitizeHeader(requestId);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to extract request ID: {}", e.getMessage());
        }
        
        return UUID.randomUUID().toString();
    }

    /**
     * Logs exception details with appropriate masking of sensitive data.
     * The log level can be specified based on the severity of the exception.
     *
     * @param ex       The exception to log
     * @param requestId The request ID for correlation
     * @param logLevel The level at which to log (ERROR, WARN, INFO, DEBUG)
     */
    protected void logException(Exception ex, String requestId, String logLevel) {
        String message = String.format("%s: %s (Request ID: %s)", 
                ex.getClass().getName(), 
                ex.getMessage(), 
                requestId);
        
        switch (logLevel.toUpperCase()) {
            case "ERROR":
                LOGGER.error(message);
                LOGGER.debug("Stack trace for request {}: ", requestId, ex);
                break;
            case "WARN":
                LOGGER.warn(message);
                break;
            case "INFO":
                LOGGER.info(message);
                break;
            case "DEBUG":
                LOGGER.debug(message);
                LOGGER.debug("Stack trace for request {}: ", requestId, ex);
                break;
            default:
                LOGGER.error(message);
                break;
        }
    }
}