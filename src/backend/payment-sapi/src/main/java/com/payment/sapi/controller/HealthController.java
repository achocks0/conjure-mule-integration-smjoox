package com.payment.sapi.controller;

import com.payment.sapi.service.CacheService;
import com.payment.sapi.service.PaymentService;
import com.payment.sapi.service.TokenValidationService;
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
 * REST controller that provides health check endpoints for the Payment-Sapi component.
 * This controller is responsible for monitoring the health and status of critical
 * dependencies such as Redis cache, database, and token validation service, and
 * exposing this information through API endpoints for monitoring systems.
 */
@RestController
@RequestMapping("/internal/v1")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);
    
    private final CacheService cacheService;
    private final PaymentService paymentService;
    private final TokenValidationService tokenValidationService;
    private final MetricsService metricsService;
    
    /**
     * Constructor that injects the required dependencies
     */
    @Autowired
    public HealthController(CacheService cacheService, 
                           PaymentService paymentService,
                           TokenValidationService tokenValidationService,
                           MetricsService metricsService) {
        this.cacheService = cacheService;
        this.paymentService = paymentService;
        this.tokenValidationService = tokenValidationService;
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
        logger.info("Health check requested");
        metricsService.recordApiRequest("/internal/v1/health", "GET", 200, 0);
        
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("service", "Payment-SAPI");
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
        logger.info("Detailed health check requested");
        metricsService.recordApiRequest("/internal/v1/health/detailed", "GET", 200, 0);
        
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("service", "Payment-SAPI");
        
        // Check Redis cache
        boolean redisOk = false;
        try {
            // Try to retrieve a token to check if Redis is available
            redisOk = cacheService.retrieveToken("test-token") != null || true; // Even if no token, connection worked
            healthStatus.put("redis", redisOk ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Redis health check failed: {}", e.getMessage());
            healthStatus.put("redis", "DOWN");
            healthStatus.put("redis_error", e.getMessage());
        }
        
        // Check database via payment service
        boolean dbOk = false;
        try {
            // Try to get payments to check if database is available
            paymentService.getPaymentsByClient("test-client");
            dbOk = true;
            healthStatus.put("database", dbOk ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Database health check failed: {}", e.getMessage());
            healthStatus.put("database", "DOWN");
            healthStatus.put("database_error", e.getMessage());
        }
        
        // Check token validation service
        boolean tokenValidationOk = false;
        try {
            // Try to validate token signature to check if service is working
            tokenValidationService.validateTokenSignature("test-token");
            tokenValidationOk = true;
            healthStatus.put("token_validation", tokenValidationOk ? "UP" : "DOWN");
        } catch (Exception e) {
            logger.warn("Token validation health check failed: {}", e.getMessage());
            healthStatus.put("token_validation", "DOWN");
            healthStatus.put("token_validation_error", e.getMessage());
        }
        
        // Determine overall status
        boolean overallOk = redisOk && dbOk && tokenValidationOk;
        healthStatus.put("status", overallOk ? "UP" : "DEGRADED");
        
        // Record metrics for health status
        metricsService.recordGaugeValue("health.redis", redisOk ? 1.0 : 0.0, new String[]{"component:redis"});
        metricsService.recordGaugeValue("health.database", dbOk ? 1.0 : 0.0, new String[]{"component:database"});
        metricsService.recordGaugeValue("health.token_validation", tokenValidationOk ? 1.0 : 0.0, new String[]{"component:token_validation"});
        metricsService.recordGaugeValue("health.overall", overallOk ? 1.0 : 0.0, new String[]{"component:overall"});
        
        HttpStatus status = overallOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
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
        logger.debug("Liveness check requested");
        metricsService.recordApiRequest("/internal/v1/health/liveness", "GET", 200, 0);
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
        logger.info("Readiness check requested");
        metricsService.recordApiRequest("/internal/v1/health/readiness", "GET", 200, 0);
        
        boolean redisOk = false;
        boolean dbOk = false;
        boolean tokenValidationOk = false;
        
        // Check Redis cache
        try {
            redisOk = cacheService.retrieveToken("test-token") != null || true;
        } catch (Exception e) {
            logger.warn("Redis readiness check failed: {}", e.getMessage());
        }
        
        // Check database via payment service
        try {
            paymentService.getPaymentsByClient("test-client");
            dbOk = true;
        } catch (Exception e) {
            logger.warn("Database readiness check failed: {}", e.getMessage());
        }
        
        // Check token validation service
        try {
            tokenValidationService.validateTokenSignature("test-token");
            tokenValidationOk = true;
        } catch (Exception e) {
            logger.warn("Token validation readiness check failed: {}", e.getMessage());
        }
        
        // Determine overall readiness
        boolean ready = redisOk && dbOk && tokenValidationOk;
        
        // Record metrics for readiness status
        metricsService.recordGaugeValue("readiness.status", ready ? 1.0 : 0.0, new String[]{"component:overall"});
        
        if (ready) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}