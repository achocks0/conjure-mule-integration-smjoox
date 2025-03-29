package com.payment.eapi.service;

import com.payment.eapi.model.Token;
import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * Interface that defines methods for forwarding authenticated requests from Payment-Eapi to Payment-Sapi
 * with JWT token authentication. This service is responsible for translating between header-based authentication
 * and token-based authentication, maintaining backward compatibility with existing vendor integrations.
 */
public interface ForwardingService {
    
    /**
     * Forwards a request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send (can be null for requests without a body)
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    ResponseEntity<?> forwardRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders);
    
    /**
     * Forwards a GET request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    ResponseEntity<?> forwardGetRequest(String path, Token token, Map<String, String> additionalHeaders);
    
    /**
     * Forwards a POST request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    ResponseEntity<?> forwardPostRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders);
    
    /**
     * Forwards a PUT request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    ResponseEntity<?> forwardPutRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders);
    
    /**
     * Handles the response from Payment-Sapi
     *
     * @param response the response from Payment-Sapi
     * @return the processed response
     */
    ResponseEntity<?> handleResponse(ResponseEntity<?> response);
}