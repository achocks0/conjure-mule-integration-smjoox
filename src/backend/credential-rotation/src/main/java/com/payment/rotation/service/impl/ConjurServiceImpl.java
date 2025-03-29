package com.payment.rotation.service.impl;

import com.payment.rotation.service.ConjurService;
import com.payment.rotation.model.RotationState;
import com.payment.common.util.SecurityUtils;

import com.cyberark.conjur.api.ConjurClient;
import com.cyberark.conjur.api.exceptions.ConjurConnectionException;
import com.cyberark.conjur.api.exceptions.ConjurAuthenticationException;
import com.cyberark.conjur.api.exceptions.ConjurSecretException;
import com.payment.common.retry.RetryHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of the ConjurService interface that provides secure credential
 * management operations with Conjur vault, focusing on credential rotation capabilities.
 * This class handles the secure storage, retrieval, and management of credentials
 * during rotation processes, supporting transition periods where both old and new
 * credentials are valid.
 */
@Service
public class ConjurServiceImpl implements ConjurService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConjurServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SECRET_LENGTH = 32;
    private static final String CLIENT_ID_PATH_PREFIX = "payment/api/credentials/";
    private static final String TRANSITION_PATH_PREFIX = "payment/api/transitions/";

    private final ConjurClient conjurClient;
    private final RetryHandler retryHandler;

    /**
     * Constructs a new ConjurServiceImpl with the specified Conjur client and retry handler.
     *
     * @param conjurClient The client for interacting with Conjur vault
     * @param retryHandler The handler for retrying operations on transient failures
     */
    public ConjurServiceImpl(ConjurClient conjurClient, RetryHandler retryHandler) {
        this.conjurClient = conjurClient;
        this.retryHandler = retryHandler;
        LOGGER.info("ConjurServiceImpl initialized");
    }

    @Override
    public Map<String, String> retrieveCredential(String clientId) {
        validateClientId(clientId);
        String secretPath = CLIENT_ID_PATH_PREFIX + clientId;
        LOGGER.debug("Retrieving credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));

        try {
            return retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                String secretValue = conjurClient.retrieveSecret(secretPath);
                Map<String, String> credentialData = OBJECT_MAPPER.readValue(secretValue, Map.class);
                LOGGER.debug("Successfully retrieved credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
                return credentialData;
            });
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error retrieving credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Failed to connect to Conjur vault", e);
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error retrieving credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Failed to authenticate with Conjur vault", e);
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret retrieval error for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Failed to retrieve secret from Conjur vault", e);
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing credential data for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Failed to parse credential data", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error retrieving credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Unexpected error retrieving credential", e);
        }
    }

    @Override
    public boolean storeCredential(String clientId, Map<String, String> credentialData) {
        validateClientId(clientId);
        if (credentialData == null || credentialData.isEmpty()) {
            throw new IllegalArgumentException("Credential data cannot be null or empty");
        }

        String secretPath = CLIENT_ID_PATH_PREFIX + clientId;
        LOGGER.debug("Storing credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));

        try {
            // Remove plainSecret before storing if it exists
            Map<String, String> storageData = new HashMap<>(credentialData);
            storageData.remove("plainSecret");
            
            String secretValue = OBJECT_MAPPER.writeValueAsString(storageData);
            
            return retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                conjurClient.storeSecret(secretPath, secretValue);
                LOGGER.debug("Successfully stored credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
                return true;
            });
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error storing credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error storing credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret storage error for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing credential data for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error storing credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        }
    }

    @Override
    public Map<String, String> generateNewCredential(String clientId) {
        validateClientId(clientId);
        LOGGER.debug("Generating new credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));

        try {
            // Generate a secure random string for the new client secret
            String newClientSecret = SecurityUtils.generateSecureRandomString(SECRET_LENGTH);
            
            // Create a new credential map
            Map<String, String> credentialData = new HashMap<>();
            credentialData.put("clientId", clientId);
            credentialData.put("hashedSecret", SecurityUtils.hashCredential(newClientSecret));
            credentialData.put("plainSecret", newClientSecret); // This will be returned to caller but not stored
            credentialData.put("version", UUID.randomUUID().toString());
            credentialData.put("createdAt", String.valueOf(System.currentTimeMillis()));
            credentialData.put("status", "ACTIVE");
            
            LOGGER.debug("Successfully generated new credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
            
            return credentialData;
        } catch (Exception e) {
            LOGGER.error("Error generating new credential for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            throw new RuntimeException("Failed to generate new credential", e);
        }
    }

    @Override
    public boolean storeNewCredentialVersion(String clientId, Map<String, String> newCredentialData, String version) {
        validateClientId(clientId);
        if (newCredentialData == null || newCredentialData.isEmpty()) {
            throw new IllegalArgumentException("New credential data cannot be null or empty");
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        String secretPath = CLIENT_ID_PATH_PREFIX + clientId + "/" + version;
        LOGGER.debug("Storing new credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));

        try {
            // Remove plainSecret before storing if it exists
            Map<String, String> storageData = new HashMap<>(newCredentialData);
            storageData.remove("plainSecret");
            
            String secretValue = OBJECT_MAPPER.writeValueAsString(storageData);
            
            return retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                conjurClient.storeSecret(secretPath, secretValue);
                LOGGER.debug("Successfully stored new credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
                return true;
            });
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error storing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error storing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret storage error for credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing credential data for version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error storing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        }
    }

    @Override
    public Optional<Map<String, String>> retrieveCredentialVersion(String clientId, String version) {
        validateClientId(clientId);
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        String secretPath = CLIENT_ID_PATH_PREFIX + clientId + "/" + version;
        LOGGER.debug("Retrieving credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));

        try {
            return Optional.ofNullable(retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                String secretValue = conjurClient.retrieveSecret(secretPath);
                Map<String, String> credentialData = OBJECT_MAPPER.readValue(secretValue, Map.class);
                LOGGER.debug("Successfully retrieved credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
                return credentialData;
            }));
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error retrieving credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error retrieving credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret retrieval error for credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing credential data for version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Unexpected error retrieving credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean configureCredentialTransition(String clientId, String oldVersion, String newVersion, int transitionPeriodMinutes) {
        validateClientId(clientId);
        if (oldVersion == null || oldVersion.isEmpty()) {
            throw new IllegalArgumentException("Old version cannot be null or empty");
        }
        if (newVersion == null || newVersion.isEmpty()) {
            throw new IllegalArgumentException("New version cannot be null or empty");
        }
        if (transitionPeriodMinutes <= 0) {
            throw new IllegalArgumentException("Transition period must be positive");
        }

        String transitionPath = TRANSITION_PATH_PREFIX + clientId;
        LOGGER.debug("Configuring credential transition for clientId: {} from version {} to {}", 
                    SecurityUtils.maskSensitiveData(clientId), oldVersion, newVersion);

        try {
            Map<String, Object> transitionConfig = new HashMap<>();
            transitionConfig.put("oldVersion", oldVersion);
            transitionConfig.put("newVersion", newVersion);
            transitionConfig.put("startTime", System.currentTimeMillis());
            transitionConfig.put("endTime", System.currentTimeMillis() + (transitionPeriodMinutes * 60 * 1000));
            transitionConfig.put("state", RotationState.DUAL_ACTIVE.getValue());
            
            String configValue = OBJECT_MAPPER.writeValueAsString(transitionConfig);
            
            return retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                conjurClient.storeSecret(transitionPath, configValue);
                LOGGER.info("Successfully configured credential transition for clientId: {} from version {} to {} for {} minutes", 
                           SecurityUtils.maskSensitiveData(clientId), oldVersion, newVersion, transitionPeriodMinutes);
                return true;
            });
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error configuring credential transition for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error configuring credential transition for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret storage error configuring credential transition for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error serializing transition configuration for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error configuring credential transition for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        }
    }

    @Override
    public boolean disableCredentialVersion(String clientId, String version) {
        validateClientId(clientId);
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        LOGGER.debug("Disabling credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));

        try {
            Optional<Map<String, String>> credentialOpt = retrieveCredentialVersion(clientId, version);
            if (!credentialOpt.isPresent()) {
                LOGGER.error("Credential version {} not found for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
                return false;
            }

            Map<String, String> credentialData = credentialOpt.get();
            credentialData.put("status", "DISABLED");
            
            boolean result = storeNewCredentialVersion(clientId, credentialData, version);
            if (result) {
                LOGGER.info("Successfully disabled credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
            } else {
                LOGGER.error("Failed to disable credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
            }
            
            return result;
        } catch (Exception e) {
            LOGGER.error("Error disabling credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        }
    }

    @Override
    public boolean removeCredentialVersion(String clientId, String version) {
        validateClientId(clientId);
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be null or empty");
        }

        String secretPath = CLIENT_ID_PATH_PREFIX + clientId + "/" + version;
        LOGGER.debug("Removing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));

        try {
            return retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                conjurClient.removeSecret(secretPath);
                LOGGER.info("Successfully removed credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId));
                return true;
            });
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error removing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error removing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (ConjurSecretException e) {
            LOGGER.error("Secret removal error for credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error removing credential version {} for clientId: {}", version, SecurityUtils.maskSensitiveData(clientId), e);
            return false;
        }
    }

    @Override
    public Map<String, Map<String, String>> getActiveCredentialVersions(String clientId) {
        validateClientId(clientId);
        LOGGER.debug("Getting active credential versions for clientId: {}", SecurityUtils.maskSensitiveData(clientId));

        try {
            Optional<Map<String, Object>> transitionStatusOpt = getCredentialTransitionStatus(clientId);
            Map<String, Map<String, String>> activeVersions = new HashMap<>();
            
            if (transitionStatusOpt.isPresent()) {
                // We are in a transition state, so we need to retrieve both old and new versions
                Map<String, Object> transitionStatus = transitionStatusOpt.get();
                String oldVersion = (String) transitionStatus.get("oldVersion");
                String newVersion = (String) transitionStatus.get("newVersion");
                
                Optional<Map<String, String>> oldCredentialOpt = retrieveCredentialVersion(clientId, oldVersion);
                Optional<Map<String, String>> newCredentialOpt = retrieveCredentialVersion(clientId, newVersion);
                
                if (oldCredentialOpt.isPresent() && "ACTIVE".equals(oldCredentialOpt.get().get("status"))) {
                    activeVersions.put(oldVersion, oldCredentialOpt.get());
                }
                
                if (newCredentialOpt.isPresent() && "ACTIVE".equals(newCredentialOpt.get().get("status"))) {
                    activeVersions.put(newVersion, newCredentialOpt.get());
                }
            } else {
                // No transition active, get the current credential
                Map<String, String> currentCredential = retrieveCredential(clientId);
                String version = currentCredential.get("version");
                
                if ("ACTIVE".equals(currentCredential.get("status"))) {
                    activeVersions.put(version, currentCredential);
                }
            }
            
            LOGGER.debug("Found {} active credential versions for clientId: {}", activeVersions.size(), SecurityUtils.maskSensitiveData(clientId));
            return activeVersions;
        } catch (Exception e) {
            LOGGER.error("Error getting active credential versions for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return new HashMap<>();
        }
    }

    @Override
    public Optional<Map<String, Object>> getCredentialTransitionStatus(String clientId) {
        validateClientId(clientId);
        String transitionPath = TRANSITION_PATH_PREFIX + clientId;
        LOGGER.debug("Getting credential transition status for clientId: {}", SecurityUtils.maskSensitiveData(clientId));

        try {
            return Optional.ofNullable(retryHandler.executeWithRetry(() -> {
                if (!authenticate()) {
                    throw new ConjurAuthenticationException("Failed to authenticate with Conjur");
                }

                try {
                    String configValue = conjurClient.retrieveSecret(transitionPath);
                    Map<String, Object> transitionConfig = OBJECT_MAPPER.readValue(configValue, Map.class);
                    
                    // Check if the transition is still active
                    long endTime = Long.parseLong(transitionConfig.get("endTime").toString());
                    if (System.currentTimeMillis() > endTime) {
                        LOGGER.debug("Transition period has ended for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
                        return null;
                    }
                    
                    LOGGER.debug("Retrieved credential transition status for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
                    return transitionConfig;
                } catch (ConjurSecretException e) {
                    // Secret not found, meaning no transition is configured
                    LOGGER.debug("No transition configuration found for clientId: {}", SecurityUtils.maskSensitiveData(clientId));
                    return null;
                }
            }));
        } catch (ConjurConnectionException e) {
            LOGGER.error("Connection error retrieving transition status for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Authentication error retrieving transition status for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            LOGGER.error("Error parsing transition configuration for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.error("Unexpected error retrieving transition status for clientId: {}", SecurityUtils.maskSensitiveData(clientId), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isConnected() {
        try {
            boolean result = authenticate();
            LOGGER.debug("Conjur connection status: {}", result ? "Connected" : "Not connected");
            return result;
        } catch (Exception e) {
            LOGGER.error("Error checking Conjur connection status", e);
            return false;
        }
    }

    /**
     * Authenticates with Conjur vault.
     *
     * @return true if authentication was successful, false otherwise
     */
    private boolean authenticate() {
        try {
            if (conjurClient.isAuthenticated()) {
                return true;
            }
            
            conjurClient.authenticate();
            LOGGER.debug("Successfully authenticated with Conjur");
            return true;
        } catch (ConjurAuthenticationException e) {
            LOGGER.error("Failed to authenticate with Conjur", e);
            return false;
        } catch (Exception e) {
            LOGGER.error("Unexpected error during Conjur authentication", e);
            return false;
        }
    }

    /**
     * Validates that a client ID is not null or empty.
     *
     * @param clientId The client ID to validate
     * @throws IllegalArgumentException if the client ID is null or empty
     */
    private void validateClientId(String clientId) {
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be null or empty");
        }
    }
}