package com.payment.eapi.service.impl;

import com.payment.eapi.service.ForwardingService;
import com.payment.eapi.model.Token;
import com.payment.common.monitoring.MetricsService;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * Implementation of the ForwardingService interface that handles forwarding authenticated
 * requests from Payment-Eapi to Payment-Sapi with JWT token authentication. This service
 * translates between header-based authentication and token-based authentication, maintaining
 * backward compatibility with existing vendor integrations while enhancing internal security.
 */
@Service
public class ForwardingServiceImpl implements ForwardingService {

    private static final Logger logger = LoggerFactory.getLogger(ForwardingServiceImpl.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestTemplate restTemplate;
    private final MetricsService metricsService;
    private final String paymentSapiUrl;

    /**
     * Constructor that injects the required dependencies
     *
     * @param restTemplate the RestTemplate for making HTTP requests
     * @param metricsService the MetricsService for recording metrics
     * @param paymentSapiUrl the URL of the Payment-Sapi service
     */
    @Autowired
    public ForwardingServiceImpl(RestTemplate restTemplate, 
                                MetricsService metricsService,
                                @Value("${payment-sapi.url}") String paymentSapiUrl) {
        this.restTemplate = restTemplate;
        this.metricsService = metricsService;
        this.paymentSapiUrl = paymentSapiUrl;
    }

    /**
     * Forwards a request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send (can be null for requests without a body)
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    @Override
    public ResponseEntity<?> forwardRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders) {
        validateToken(token);
        
        HttpHeaders headers = createHeaders(token, additionalHeaders);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);
        
        String url = paymentSapiUrl + path;
        logger.debug("Forwarding request to: {}", url);
        
        long startTime = System.currentTimeMillis();
        
        ResponseEntity<?> response = restTemplate.exchange(
            url,
            HttpMethod.POST, // Default to POST, but should be parameterized for actual implementation
            entity,
            Object.class
        );
        
        long duration = System.currentTimeMillis() - startTime;
        recordApiCallDuration(path, HttpMethod.POST, response.getStatusCodeValue(), duration);
        
        return handleResponse(response);
    }

    /**
     * Forwards a GET request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    @Override
    public ResponseEntity<?> forwardGetRequest(String path, Token token, Map<String, String> additionalHeaders) {
        validateToken(token);
        
        HttpHeaders headers = createHeaders(token, additionalHeaders);
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        String url = paymentSapiUrl + path;
        logger.debug("Forwarding GET request to: {}", url);
        
        long startTime = System.currentTimeMillis();
        
        ResponseEntity<?> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            entity,
            Object.class
        );
        
        long duration = System.currentTimeMillis() - startTime;
        recordApiCallDuration(path, HttpMethod.GET, response.getStatusCodeValue(), duration);
        
        return handleResponse(response);
    }

    /**
     * Forwards a POST request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    @Override
    public ResponseEntity<?> forwardPostRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders) {
        validateToken(token);
        
        HttpHeaders headers = createHeaders(token, additionalHeaders);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);
        
        String url = paymentSapiUrl + path;
        logger.debug("Forwarding POST request to: {}", url);
        
        long startTime = System.currentTimeMillis();
        
        ResponseEntity<?> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            entity,
            Object.class
        );
        
        long duration = System.currentTimeMillis() - startTime;
        recordApiCallDuration(path, HttpMethod.POST, response.getStatusCodeValue(), duration);
        
        return handleResponse(response);
    }

    /**
     * Forwards a PUT request to Payment-Sapi with JWT token authentication
     *
     * @param path the API path to forward the request to
     * @param requestBody the request body to send
     * @param token the JWT token to use for authentication
     * @param additionalHeaders additional headers to include in the request
     * @return response from Payment-Sapi
     */
    @Override
    public ResponseEntity<?> forwardPutRequest(String path, Object requestBody, Token token, Map<String, String> additionalHeaders) {
        validateToken(token);
        
        HttpHeaders headers = createHeaders(token, additionalHeaders);
        HttpEntity<?> entity = new HttpEntity<>(requestBody, headers);
        
        String url = paymentSapiUrl + path;
        logger.debug("Forwarding PUT request to: {}", url);
        
        long startTime = System.currentTimeMillis();
        
        ResponseEntity<?> response = restTemplate.exchange(
            url,
            HttpMethod.PUT,
            entity,
            Object.class
        );
        
        long duration = System.currentTimeMillis() - startTime;
        recordApiCallDuration(path, HttpMethod.PUT, response.getStatusCodeValue(), duration);
        
        return handleResponse(response);
    }

    /**
     * Handles the response from Payment-Sapi
     *
     * @param response the response from Payment-Sapi
     * @return the processed response
     */
    @Override
    public ResponseEntity<?> handleResponse(ResponseEntity<?> response) {
        HttpStatus status = response.getStatusCode();
        logger.debug("Received response with status: {}", status);
        
        if (status.is4xxClientError() || status.is5xxServerError()) {
            logger.error("Error response from Payment-Sapi: {} - {}", status, response.getBody());
        }
        
        // Return the response without modification to maintain the contract
        return response;
    }

    /**
     * Creates HTTP headers with Authorization header containing the JWT token
     *
     * @param token the JWT token to include in the headers
     * @param additionalHeaders additional headers to include
     * @return the created HTTP headers
     */
    private HttpHeaders createHeaders(Token token, Map<String, String> additionalHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AUTHORIZATION_HEADER, BEARER_PREFIX + token.getTokenString());
        
        if (additionalHeaders != null) {
            additionalHeaders.forEach(headers::set);
        }
        
        return headers;
    }

    /**
     * Validates that the token is not null or expired
     *
     * @param token the token to validate
     * @throws IllegalArgumentException if the token is null
     * @throws IllegalStateException if the token is expired
     */
    private void validateToken(Token token) {
        if (token == null) {
            logger.error("Token cannot be null");
            throw new IllegalArgumentException("Token cannot be null");
        }
        
        if (token.isExpired()) {
            logger.error("Token has expired");
            throw new IllegalStateException("Token has expired");
        }
    }

    /**
     * Records API call duration using the metrics service
     * 
     * @param path the API path
     * @param method the HTTP method
     * @param statusCode the response status code
     * @param durationMs the call duration in milliseconds
     */
    private void recordApiCallDuration(String path, HttpMethod method, int statusCode, long durationMs) {
        // Use the standard recordApiRequest method from MetricsService
        metricsService.recordApiRequest(path, method.toString(), statusCode, durationMs);
    }
}