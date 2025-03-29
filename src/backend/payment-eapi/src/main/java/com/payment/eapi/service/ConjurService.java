package com.payment.eapi.service;

import com.payment.eapi.exception.ConjurException;
import com.payment.eapi.model.Credential;

import java.util.Optional;

/**
 * Interface defining the operations for secure credential management with Conjur vault.
 * This service provides methods to retrieve, validate, and store credentials in Conjur vault,
 * with fallback mechanisms for handling vault unavailability.
 */
public interface ConjurService {

    /**
     * Retrieves credential information from Conjur vault for the specified client ID.
     *
     * @param clientId the client ID for which to retrieve credentials
     * @return the credential information retrieved from Conjur vault
     * @throws ConjurException if an error occurs during retrieval from Conjur vault
     */
    Credential retrieveCredentials(String clientId) throws ConjurException;

    /**
     * Retrieves credential information with fallback to cached credentials if Conjur vault is unavailable.
     *
     * @param clientId the client ID for which to retrieve credentials
     * @return an Optional containing the credential information if available, empty Optional otherwise
     */
    Optional<Credential> retrieveCredentialsWithFallback(String clientId);

    /**
     * Validates the provided client credentials against those stored in Conjur vault.
     *
     * @param clientId the client ID to validate
     * @param clientSecret the client secret to validate
     * @return true if the credentials are valid, false otherwise
     */
    boolean validateCredentials(String clientId, String clientSecret);

    /**
     * Validates credentials with fallback to cached credentials if Conjur vault is unavailable.
     *
     * @param clientId the client ID to validate
     * @param clientSecret the client secret to validate
     * @return true if the credentials are valid, false otherwise
     */
    boolean validateCredentialsWithFallback(String clientId, String clientSecret);

    /**
     * Stores new credentials in Conjur vault during credential rotation.
     *
     * @param clientId the client ID for which to store credentials
     * @param credential the credential information to store
     * @return true if storage succeeds, false otherwise
     */
    boolean storeCredentials(String clientId, Credential credential);

    /**
     * Updates the rotation state of credentials in Conjur vault.
     *
     * @param clientId the client ID for which to update rotation state
     * @param rotationState the new rotation state
     * @return true if the update succeeds, false otherwise
     */
    boolean updateCredentialRotationState(String clientId, String rotationState);

    /**
     * Checks if Conjur vault is available and accessible.
     *
     * @return true if Conjur vault is available, false otherwise
     */
    boolean isAvailable();
}