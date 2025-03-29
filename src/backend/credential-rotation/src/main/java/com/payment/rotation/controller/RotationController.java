package com.payment.rotation.controller;

import com.payment.rotation.model.RotationRequest;
import com.payment.rotation.model.RotationResponse;
import com.payment.rotation.model.RotationState;
import com.payment.rotation.service.RotationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * REST controller that exposes endpoints for managing credential rotation processes.
 * This controller provides APIs to initiate, monitor, advance, complete, and cancel
 * credential rotations without service disruption.
 */
@RestController
@RequestMapping("/api/v1/rotations")
public class RotationController {

    private static final Logger logger = LoggerFactory.getLogger(RotationController.class);
    
    private final RotationService rotationService;

    /**
     * Constructor for RotationController with dependency injection.
     *
     * @param rotationService The service for managing credential rotation processes
     */
    @Autowired
    public RotationController(RotationService rotationService) {
        this.rotationService = rotationService;
    }

    /**
     * Initiates a new credential rotation process.
     *
     * @param request The rotation request containing parameters for the rotation process
     * @return HTTP response with rotation details
     */
    @PostMapping("/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RotationResponse> initiateRotation(@Valid @RequestBody RotationRequest request) {
        logger.info("Initiating credential rotation for client ID: {}", request.getClientId());
        RotationResponse response = rotationService.initiateRotation(request);
        logger.info("Credential rotation initiated successfully. Rotation ID: {}", response.getRotationId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves the status of a specific rotation process.
     *
     * @param rotationId The unique identifier of the rotation process
     * @return HTTP response with rotation status
     */
    @GetMapping("/{rotationId}")
    public ResponseEntity<RotationResponse> getRotationStatus(@PathVariable String rotationId) {
        logger.info("Retrieving rotation status for rotation ID: {}", rotationId);
        Optional<RotationResponse> rotationResponse = rotationService.getRotationStatus(rotationId);
        
        if (rotationResponse.isPresent()) {
            logger.debug("Found rotation with ID: {} in state: {}", rotationId, rotationResponse.get().getCurrentState());
            return ResponseEntity.ok(rotationResponse.get());
        } else {
            logger.warn("Rotation with ID: {} not found", rotationId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Retrieves all rotation processes for a specific client.
     *
     * @param clientId The client ID to retrieve rotations for
     * @return HTTP response with list of rotation responses
     */
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<RotationResponse>> getRotationsByClientId(@PathVariable String clientId) {
        logger.info("Retrieving all rotations for client ID: {}", clientId);
        List<RotationResponse> rotations = rotationService.getRotationsByClientId(clientId);
        logger.debug("Found {} rotations for client ID: {}", rotations.size(), clientId);
        return ResponseEntity.ok(rotations);
    }

    /**
     * Retrieves all currently active rotation processes.
     *
     * @return HTTP response with list of active rotation responses
     */
    @GetMapping("/active")
    public ResponseEntity<List<RotationResponse>> getActiveRotations() {
        logger.info("Retrieving all active rotation processes");
        List<RotationResponse> activeRotations = rotationService.getActiveRotations();
        logger.debug("Found {} active rotation processes", activeRotations.size());
        return ResponseEntity.ok(activeRotations);
    }

    /**
     * Advances a rotation process to the specified state.
     *
     * @param rotationId The unique identifier of the rotation process
     * @param targetState The target state to advance the rotation to
     * @return HTTP response with updated rotation details
     */
    @PutMapping("/{rotationId}/advance")
    public ResponseEntity<RotationResponse> advanceRotation(
            @PathVariable String rotationId,
            @RequestParam RotationState targetState) {
        logger.info("Advancing rotation ID: {} to target state: {}", rotationId, targetState);
        RotationResponse response = rotationService.advanceRotation(rotationId, targetState);
        logger.info("Successfully advanced rotation ID: {} to state: {}", rotationId, response.getCurrentState());
        return ResponseEntity.ok(response);
    }

    /**
     * Completes a rotation process by finalizing the transition to the new credential.
     *
     * @param rotationId The unique identifier of the rotation process
     * @return HTTP response with completed rotation details
     */
    @PutMapping("/{rotationId}/complete")
    public ResponseEntity<RotationResponse> completeRotation(@PathVariable String rotationId) {
        logger.info("Completing rotation process for rotation ID: {}", rotationId);
        RotationResponse response = rotationService.completeRotation(rotationId);
        logger.info("Successfully completed rotation process for rotation ID: {}", rotationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an in-progress rotation process.
     *
     * @param rotationId The unique identifier of the rotation process
     * @param reason The reason for cancellation
     * @return HTTP response with cancelled rotation details
     */
    @DeleteMapping("/{rotationId}")
    public ResponseEntity<RotationResponse> cancelRotation(
            @PathVariable String rotationId,
            @RequestParam(required = false) String reason) {
        logger.info("Cancelling rotation process for rotation ID: {} with reason: {}", rotationId, reason);
        RotationResponse response = rotationService.cancelRotation(rotationId, reason);
        logger.info("Successfully cancelled rotation process for rotation ID: {}", rotationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Handles exceptions thrown during rotation operations.
     *
     * @param ex The exception that was thrown
     * @return HTTP response with error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RotationResponse> handleException(Exception ex) {
        logger.error("Error during rotation operation: {}", ex.getMessage(), ex);
        RotationResponse errorResponse = RotationResponse.builder()
                .success(false)
                .message("An unexpected error occurred: " + ex.getMessage())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles illegal argument exceptions thrown during rotation operations.
     *
     * @param ex The illegal argument exception that was thrown
     * @return HTTP response with error details
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RotationResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.error("Invalid argument during rotation operation: {}", ex.getMessage(), ex);
        RotationResponse errorResponse = RotationResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}