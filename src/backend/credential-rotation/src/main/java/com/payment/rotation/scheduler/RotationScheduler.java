package com.payment.rotation.scheduler;

import com.payment.rotation.service.RotationService;
import com.payment.rotation.model.RotationResponse;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Scheduler component responsible for automatically checking and advancing 
 * credential rotation processes. This class periodically invokes the rotation 
 * service to check the progress of ongoing rotations and advance them to the 
 * next state when appropriate.
 */
@Component
public class RotationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RotationScheduler.class);
    
    private final RotationService rotationService;
    
    /**
     * Constructor for RotationScheduler with dependency injection.
     * 
     * @param rotationService The service responsible for checking and advancing rotations
     */
    @Autowired
    public RotationScheduler(RotationService rotationService) {
        this.rotationService = rotationService;
    }
    
    /**
     * Scheduled method that checks the progress of ongoing rotations and advances them if necessary.
     * This method runs at a fixed interval configured via application properties.
     */
    @Scheduled(fixedDelayString = "${rotation.scheduler.interval:300000}")
    public void checkAndAdvanceRotations() {
        logger.info("Starting scheduled rotation check and advancement process");
        
        try {
            List<RotationResponse> processedRotations = rotationService.checkRotationProgress();
            
            logger.info("Processed {} rotation(s)", processedRotations.size());
            
            for (RotationResponse rotation : processedRotations) {
                logger.info("Rotation processed - ID: {}, Client ID: {}, Current State: {}", 
                    rotation.getRotationId(), 
                    rotation.getClientId(), 
                    rotation.getCurrentState());
            }
            
            logger.info("Completed scheduled rotation check and advancement process");
        } catch (Exception e) {
            logger.error("Error during rotation check and advancement process", e);
        }
    }
}