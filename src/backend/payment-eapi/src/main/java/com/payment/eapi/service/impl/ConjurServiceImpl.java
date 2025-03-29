package com.payment.eapi.service.impl;

import com.cyberark.conjur.api.ConjurClient; // latest
import com.payment.common.retry.RetryHandler; // 1.0.0
import com.payment.common.util.SecurityUtils;
import com.payment.eapi.exception.ConjurException;
import com.payment.eapi.model.Credential;
import com.payment.eapi.service.CacheService;
import com.payment.eapi.service.ConjurService;
import org.slf4j.Logger; // 1.7.32
import org.slf4j.LoggerFactory; // 1.7.32
import org.springframework.stereotype.Service; // 5.3.13

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Implementation of the ConjurService interface that provides secure credential management operations
 * with Conjur vault. This class handles credential retrieval, validation, storage, and rotation state
 * management with fallback mechanisms for vault unavailability.
 */
@Service
public class ConjurServiceImpl implements ConjurService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurServiceImpl.class);
    private static final String CREDENTIAL_PATH_PREFIX = "payment/api/credentials/";
    private static final String CONNECTION_ERROR = "CONJUR_CONNECTION_ERROR";
    private static final String RETRIEVAL_ERROR = "CONJUR_RETRIEVAL_ERROR";
    private static final String STORAGE_ERROR = "CONJUR_STORAGE_ERROR";

    private final ConjurClient conjurClient;
    private final RetryHandler retryHandler;
    private final CacheService cacheService;

    /**
     * Constructor that initializes the ConjurServiceImpl with required dependencies
     *
     * @param conjurClient  the client for interacting with Conjur vault
     * @param retryHandler  the handler for retrying operations on transient failures
     * @param cacheService  the service for caching credentials
     */
    public ConjurServiceImpl(ConjurClient conjurClient, RetryHandler retryHandler, CacheService cacheService) {
        this.conjurClient = conjurClient;
        this.retryHandler = retryHandler;
        this.cacheService = cacheService;
        LOGGER.info("ConjurServiceImpl initialized successfully");
    }

    /**
     * Retrieves credential information from Conjur vault for the specified client ID.
     *
     * @param clientId the client ID for which to retrieve credentials
     * @return the credential information retrieved from Conjur vault
     * @throws ConjurException if an error occurs during retrieval from Conjur vault
     */
    @Override
    public Credential retrieveCredentials(String clientId) throws ConjurException {
        LOGGER.debug("Retrieving credentials for client: {}", SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty()) {
            throw new ConjurException("Client ID cannot be null or empty", RETRIEVAL_ERROR);
        }
        
        String credentialPath = getCredentialPath(clientId);
        
        try {
            return retryHandler.executeWithRetry(new Callable<Credential>() {
                @Override
                public Credential call() throws Exception {
                    try {
                        // Ensure we're authenticated with Conjur
                        if (!conjurClient.isAuthenticated()) {
                            conjurClient.authenticate();
                        }
                        
                        // Retrieve the credential from Conjur vault
                        String credentialJson = conjurClient.retrieveSecret(credentialPath);
                        
                        if (credentialJson == null || credentialJson.isEmpty()) {
                            throw new ConjurException("Empty credential returned from Conjur vault", RETRIEVAL_ERROR);
                        }
                        
                        // Parse the JSON into a Credential object
                        // In a real implementation, this would use Jackson ObjectMapper
                        Credential credential = parseCredentialJson(credentialJson);
                        
                        // Cache the retrieved credential
                        cacheService.cacheCredential(clientId, credential);
                        
                        LOGGER.debug("Successfully retrieved credentials for client: {}", SecurityUtils.maskSensitiveData(clientId));
                        return credential;
                    } catch (Exception e) {
                        LOGGER.error("Failed to retrieve credentials from Conjur vault for client: {}, error: {}", 
                                SecurityUtils.maskSensitiveData(clientId), e.getMessage());
                        throw new ConjurException("Failed to retrieve credentials from Conjur vault", e, RETRIEVAL_ERROR);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error retrieving credentials for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            if (e instanceof ConjurException) {
                throw (ConjurException) e;
            }
            throw new ConjurException("Error retrieving credentials from Conjur vault", e, RETRIEVAL_ERROR);
        }
    }

    /**
     * Retrieves credential information with fallback to cached credentials if Conjur vault is unavailable.
     *
     * @param clientId the client ID for which to retrieve credentials
     * @return an Optional containing the credential information if available, empty Optional otherwise
     */
    @Override
    public Optional<Credential> retrieveCredentialsWithFallback(String clientId) {
        LOGGER.debug("Retrieving credentials with fallback for client: {}", SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.error("Client ID cannot be null or empty");
            return Optional.empty();
        }
        
        try {
            // Try to retrieve from Conjur vault first
            Credential credential = retrieveCredentials(clientId);
            return Optional.of(credential);
        } catch (ConjurException e) {
            if (CONNECTION_ERROR.equals(e.getErrorCode()) || RETRIEVAL_ERROR.equals(e.getErrorCode())) {
                LOGGER.warn("Conjur vault unavailable or retrieval error, attempting fallback to cache for client: {}", 
                        SecurityUtils.maskSensitiveData(clientId));
                
                // Fallback to cached credentials
                Optional<Credential> cachedCredential = cacheService.retrieveCredential(clientId);
                
                if (cachedCredential.isPresent()) {
                    LOGGER.info("Successfully retrieved cached credentials for client: {}", 
                            SecurityUtils.maskSensitiveData(clientId));
                    return cachedCredential;
                } else {
                    LOGGER.error("No cached credentials available for client: {}", 
                            SecurityUtils.maskSensitiveData(clientId));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Unexpected error retrieving credentials with fallback for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Validates the provided client credentials against those stored in Conjur vault.
     *
     * @param clientId the client ID to validate
     * @param clientSecret the client secret to validate
     * @return true if the credentials are valid, false otherwise
     */
    @Override
    public boolean validateCredentials(String clientId, String clientSecret) {
        LOGGER.debug("Validating credentials for client: {}", SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            LOGGER.warn("Client ID or client secret cannot be null or empty");
            return false;
        }
        
        try {
            // Retrieve the credential from Conjur vault
            Credential credential = retrieveCredentials(clientId);
            
            // Check if the credential is active
            if (!credential.isValid()) {
                LOGGER.warn("Credentials for client {} are not valid (inactive or expired)", 
                        SecurityUtils.maskSensitiveData(clientId));
                return false;
            }
            
            // Validate the client secret against the stored hashed secret
            boolean isValid = SecurityUtils.validateCredential(clientSecret, credential.getHashedSecret());
            
            if (isValid) {
                LOGGER.debug("Credentials validated successfully for client: {}", 
                        SecurityUtils.maskSensitiveData(clientId));
            } else {
                LOGGER.warn("Invalid client secret provided for client: {}", 
                        SecurityUtils.maskSensitiveData(clientId));
            }
            
            return isValid;
        } catch (Exception e) {
            LOGGER.error("Error validating credentials for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            return false;
        }
    }

    /**
     * Validates credentials with fallback to cached credentials if Conjur vault is unavailable.
     *
     * @param clientId the client ID to validate
     * @param clientSecret the client secret to validate
     * @return true if the credentials are valid, false otherwise
     */
    @Override
    public boolean validateCredentialsWithFallback(String clientId, String clientSecret) {
        LOGGER.debug("Validating credentials with fallback for client: {}", SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty() || clientSecret == null || clientSecret.isEmpty()) {
            LOGGER.warn("Client ID or client secret cannot be null or empty");
            return false;
        }
        
        try {
            // Try to validate using Conjur vault first
            return validateCredentials(clientId, clientSecret);
        } catch (ConjurException e) {
            LOGGER.warn("Conjur vault unavailable or error occurred, attempting fallback to cache for validation, client: {}", 
                    SecurityUtils.maskSensitiveData(clientId));
            
            // Fallback to cached credentials
            Optional<Credential> cachedCredential = cacheService.retrieveCredential(clientId);
            
            if (cachedCredential.isPresent()) {
                Credential credential = cachedCredential.get();
                
                // Check if the credential is active
                if (!credential.isValid()) {
                    LOGGER.warn("Cached credentials for client {} are not valid (inactive or expired)", 
                            SecurityUtils.maskSensitiveData(clientId));
                    return false;
                }
                
                // Validate the client secret against the stored hashed secret
                boolean isValid = SecurityUtils.validateCredential(clientSecret, credential.getHashedSecret());
                
                LOGGER.debug("Fallback validation result for client {}: {}", 
                        SecurityUtils.maskSensitiveData(clientId), isValid);
                return isValid;
            } else {
                LOGGER.error("No cached credentials available for fallback validation, client: {}", 
                        SecurityUtils.maskSensitiveData(clientId));
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("Unexpected error validating credentials with fallback for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            return false;
        }
    }

    /**
     * Stores new credentials in Conjur vault during credential rotation.
     *
     * @param clientId the client ID for which to store credentials
     * @param credential the credential information to store
     * @return true if storage succeeds, false otherwise
     */
    @Override
    public boolean storeCredentials(String clientId, Credential credential) {
        LOGGER.debug("Storing credentials for client: {}", SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty() || credential == null) {
            LOGGER.error("Client ID cannot be null or empty and credential cannot be null");
            return false;
        }
        
        String credentialPath = getCredentialPath(clientId);
        
        try {
            return retryHandler.executeWithRetry(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    try {
                        // Ensure we're authenticated with Conjur
                        if (!conjurClient.isAuthenticated()) {
                            conjurClient.authenticate();
                        }
                        
                        // Serialize the credential object to JSON
                        // In a real implementation, this would use Jackson ObjectMapper
                        String credentialJson = serializeCredential(credential);
                        
                        // Store the credential JSON in Conjur vault
                        conjurClient.storeSecret(credentialPath, credentialJson);
                        
                        // Invalidate any cached credential for this client ID to ensure fresh data is used
                        cacheService.invalidateCredential(clientId);
                        
                        // Cache the new credential
                        cacheService.cacheCredential(clientId, credential);
                        
                        LOGGER.debug("Successfully stored credentials for client: {}", 
                                SecurityUtils.maskSensitiveData(clientId));
                        return true;
                    } catch (Exception e) {
                        LOGGER.error("Failed to store credentials in Conjur vault for client: {}, error: {}", 
                                SecurityUtils.maskSensitiveData(clientId), e.getMessage());
                        throw new ConjurException("Failed to store credentials in Conjur vault", e, STORAGE_ERROR);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.error("Error storing credentials for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            return false;
        }
    }

    /**
     * Updates the rotation state of credentials in Conjur vault.
     *
     * @param clientId the client ID for which to update rotation state
     * @param rotationState the new rotation state
     * @return true if the update succeeds, false otherwise
     */
    @Override
    public boolean updateCredentialRotationState(String clientId, String rotationState) {
        LOGGER.debug("Updating rotation state to '{}' for client: {}", 
                rotationState, SecurityUtils.maskSensitiveData(clientId));
        
        if (clientId == null || clientId.isEmpty()) {
            LOGGER.error("Client ID cannot be null or empty");
            return false;
        }
        
        try {
            // Retrieve the current credential
            Credential credential = retrieveCredentials(clientId);
            
            // Update the rotation state
            credential.setRotationState(rotationState);
            credential.setUpdatedAt(new java.util.Date());
            
            // Store the updated credential back in Conjur vault
            boolean result = storeCredentials(clientId, credential);
            
            if (result) {
                LOGGER.debug("Successfully updated rotation state to '{}' for client: {}", 
                        rotationState, SecurityUtils.maskSensitiveData(clientId));
            } else {
                LOGGER.error("Failed to update rotation state for client: {}", 
                        SecurityUtils.maskSensitiveData(clientId));
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.error("Error updating rotation state for client: {}, error: {}", 
                    SecurityUtils.maskSensitiveData(clientId), e.getMessage());
            return false;
        }
    }

    /**
     * Checks if Conjur vault is available and accessible.
     *
     * @return true if Conjur vault is available, false otherwise
     */
    @Override
    public boolean isAvailable() {
        LOGGER.debug("Checking if Conjur vault is available");
        
        try {
            // Try to authenticate with Conjur
            if (!conjurClient.isAuthenticated()) {
                conjurClient.authenticate();
            }
            
            LOGGER.debug("Conjur vault is available");
            return true;
        } catch (Exception e) {
            LOGGER.warn("Conjur vault is unavailable: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Constructs the path for storing and retrieving credentials in Conjur vault.
     *
     * @param clientId the client ID to construct the path for
     * @return the full path to the credential in Conjur vault
     */
    private String getCredentialPath(String clientId) {
        return CREDENTIAL_PATH_PREFIX + clientId;
    }
    
    /**
     * Parses JSON into a Credential object.
     * In a real implementation, this would use Jackson ObjectMapper.
     *
     * @param credentialJson the JSON string to parse
     * @return the parsed Credential object
     */
    private Credential parseCredentialJson(String credentialJson) {
        // This is a simplified implementation for illustration purposes.
        // In a real implementation, this would use Jackson ObjectMapper.
        // Example: return new ObjectMapper().readValue(credentialJson, Credential.class);
        
        // For now, we'll just create a mock Credential object
        Credential credential = new Credential();
        // Parse JSON and populate credential fields
        // This would be replaced with actual JSON parsing in a real implementation
        return credential;
    }
    
    /**
     * Serializes a Credential object to JSON.
     * In a real implementation, this would use Jackson ObjectMapper.
     *
     * @param credential the Credential object to serialize
     * @return the JSON string representation
     */
    private String serializeCredential(Credential credential) {
        // This is a simplified implementation for illustration purposes.
        // In a real implementation, this would use Jackson ObjectMapper.
        // Example: return new ObjectMapper().writeValueAsString(credential);
        
        // For now, we'll just return a mock JSON string
        return "{\"clientId\":\"" + credential.getClientId() + "\"}";
    }
}