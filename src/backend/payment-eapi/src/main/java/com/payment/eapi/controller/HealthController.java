package com.payment.eapi.controller;

import com.payment.eapi.service.CacheService;
import com.payment.eapi.service.ConjurService;
import com.payment.eapi.service.ForwardingService;
import com.payment.eapi.service.TokenService;
import com.payment.common.monitoring.MetricsService;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * REST controller that provides health check endpoints for the Payment-Eapi component.
 * This controller is responsible for monitoring the health and status of critical
 * dependencies such as Conjur vault, Redis cache, and forwarding service, and
 * exposing this information through API endpoints for monitoring systems.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    private final CacheService cacheService;
    private final ConjurService conjurService;
    private final ForwardingService forwardingService;
    private final TokenService tokenService;
    private final MetricsService metricsService;
    
    /**
     * Constructor that injects the required dependencies
     *
     * @param cacheService the service for accessing Redis cache
     * @param conjurService the service for accessing Conjur vault
     * @param forwardingService the service for forwarding requests to Payment-Sapi
     * @param tokenService the service for generating JWT tokens
     * @param metricsService the service for recording metrics
     */
    @Autowired
    public HealthController(CacheService cacheService, 
                            ConjurService conjurService,
                            ForwardingService forwardingService,
                            TokenService tokenService,
                            MetricsService metricsService) {
        this.cacheService = cacheService;
        this.conjurService = conjurService;
        this.forwardingService = forwardingService;
        this.tokenService = tokenService;
        this.metricsService = metricsService;
    }
    
    /**
     * Provides a basic health check endpoint that returns the status of the service
     *
     * @return Response entity containing health status information
     */
    @GetMapping("/health")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Map<String, Object>> checkHealth() {
        logger.info("Health check request received");
        
        // Record API request metric
        metricsService.recordApiRequest("/health", "GET", HttpStatus.OK.value(), 0);
        
        // Create a simple health status response
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("service", "Payment-EAPI");
        healthStatus.put("status", "UP");
        
        return ResponseEntity.ok(healthStatus);
    }
    
    /**
     * Provides a detailed health check endpoint that includes the status of dependencies
     *
     * @return Response entity containing detailed health status information
     */
    @GetMapping("/health/detailed")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Map<String, Object>> checkDetailedHealth() {
        logger.info("Detailed health check request received");
        
        // Record API request metric
        metricsService.recordApiRequest("/health/detailed", "GET", HttpStatus.OK.value(), 0);
        
        // Create health status response with dependency details
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("service", "Payment-EAPI");
        
        // Check Conjur vault availability
        boolean conjurAvailable = false;
        try {
            conjurAvailable = conjurService.isAvailable();
            healthStatus.put("conjurVault", conjurAvailable ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Error checking Conjur vault health: {}", e.getMessage());
            healthStatus.put("conjurVault", "DOWN");
        }
        
        // Check Redis cache availability
        boolean redisAvailable = false;
        try {
            // Try to retrieve a token to check if Redis is available
            redisAvailable = cacheService.retrieveToken("health-check-client").isPresent() || true; // Consider up even if no token found
            healthStatus.put("redisCache", redisAvailable ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Error checking Redis cache health: {}", e.getMessage());
            healthStatus.put("redisCache", "DOWN");
        }
        
        // Check Payment-Sapi availability
        boolean sapiAvailable = false;
        try {
            // Generate a test token for the health check
            var testToken = tokenService.generateTestToken();
            
            // Forward a simple health check request to Payment-Sapi
            var sapiResponse = forwardingService.forwardGetRequest("/internal/health", 
                                                                  testToken, 
                                                                  Collections.emptyMap());
            sapiAvailable = sapiResponse.getStatusCode().is2xxSuccessful();
            healthStatus.put("paymentSapi", sapiAvailable ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Error checking Payment-Sapi health: {}", e.getMessage());
            healthStatus.put("paymentSapi", "DOWN");
        }
        
        // Determine overall status based on dependency statuses
        boolean allDependenciesUp = conjurAvailable && redisAvailable && sapiAvailable;
        healthStatus.put("status", allDependenciesUp ? "UP" : "DEGRADED");
        
        // Record gauge metrics for component health
        metricsService.recordGaugeValue("component.health", conjurAvailable ? 1 : 0, new String[]{"component:conjur-vault"});
        metricsService.recordGaugeValue("component.health", redisAvailable ? 1 : 0, new String[]{"component:redis-cache"});
        metricsService.recordGaugeValue("component.health", sapiAvailable ? 1 : 0, new String[]{"component:payment-sapi"});
        
        HttpStatus status = allDependenciesUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(healthStatus);
    }
    
    /**
     * Provides a lightweight liveness check endpoint for Kubernetes health probes
     *
     * @return Empty response entity with HTTP status 200 OK
     */
    @GetMapping("/health/liveness")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Void> checkLiveness() {
        logger.debug("Liveness check request received");
        
        // Record API request metric
        metricsService.recordApiRequest("/health/liveness", "GET", HttpStatus.OK.value(), 0);
        
        // Simple check to see if the application is alive
        return ResponseEntity.ok().build();
    }
    
    /**
     * Provides a readiness check endpoint for Kubernetes health probes that verifies
     * the service is ready to accept traffic
     *
     * @return Empty response entity with HTTP status 200 OK if ready, or 503 Service Unavailable if not ready
     */
    @GetMapping("/health/readiness")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Void> checkReadiness() {
        logger.info("Readiness check request received");
        
        // Record API request metric
        metricsService.recordApiRequest("/health/readiness", "GET", HttpStatus.OK.value(), 0);
        
        boolean conjurAvailable = false;
        boolean redisAvailable = false;
        boolean sapiAvailable = false;
        
        // Check Conjur vault availability
        try {
            conjurAvailable = conjurService.isAvailable();
        } catch (Exception e) {
            logger.warn("Error checking Conjur vault readiness: {}", e.getMessage());
        }
        
        // Check Redis cache availability
        try {
            // Try to retrieve a token to check if Redis is available
            redisAvailable = cacheService.retrieveToken("health-check-client").isPresent() || true; // Consider up even if no token found
        } catch (Exception e) {
            logger.warn("Error checking Redis cache readiness: {}", e.getMessage());
        }
        
        // Check Payment-Sapi availability
        try {
            // Generate a test token for the health check
            var testToken = tokenService.generateTestToken();
            
            // Forward a simple health check request to Payment-Sapi
            var sapiResponse = forwardingService.forwardGetRequest("/internal/health", 
                                                                  testToken, 
                                                                  Collections.emptyMap());
            sapiAvailable = sapiResponse.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn("Error checking Payment-Sapi readiness: {}", e.getMessage());
        }
        
        // Record gauge metrics for readiness status
        metricsService.recordGaugeValue("readiness.status", conjurAvailable ? 1 : 0, new String[]{"component:conjur-vault"});
        metricsService.recordGaugeValue("readiness.status", redisAvailable ? 1 : 0, new String[]{"component:redis-cache"});
        metricsService.recordGaugeValue("readiness.status", sapiAvailable ? 1 : 0, new String[]{"component:payment-sapi"});
        
        // Service is ready if all dependencies are available
        boolean isReady = conjurAvailable && redisAvailable && sapiAvailable;
        
        if (isReady) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}