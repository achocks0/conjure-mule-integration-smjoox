package com.payment.rotation.service;

import java.util.Map;
import java.util.Optional;

/**
 * Interface defining operations for interacting with Conjur vault to securely manage credentials
 * during rotation processes. This service provides methods to store, retrieve, and manage
 * credential versions while supporting transition periods where both old and new credentials are valid.
 */
public interface ConjurService {

    /**
     * Retrieves a credential from Conjur vault by client ID.
     *
     * @param clientId The client ID for which to retrieve the credential
     * @return A map containing credential data including client secret and metadata
     * @throws RuntimeException if retrieval fails
     */
    Map<String, String> retrieveCredential(String clientId);

    /**
     * Stores a credential in Conjur vault.
     *
     * @param clientId        The client ID for which to store the credential
     * @param credentialData  The credential data to store, including client secret and metadata
     * @return True if the credential was stored successfully, false otherwise
     */
    boolean storeCredential(String clientId, Map<String, String> credentialData);

    /**
     * Generates a new credential for rotation purposes.
     *
     * @param clientId The client ID for which to generate a new credential
     * @return A map containing the newly generated credential data
     */
    Map<String, String> generateNewCredential(String clientId);

    /**
     * Stores a new version of a credential during rotation.
     *
     * @param clientId         The client ID for which to store the new credential version
     * @param newCredentialData The new credential data to store
     * @param version          The version identifier for the new credential
     * @return True if the new credential version was stored successfully, false otherwise
     */
    boolean storeNewCredentialVersion(String clientId, Map<String, String> newCredentialData, String version);

    /**
     * Retrieves a specific version of a credential from Conjur vault.
     *
     * @param clientId The client ID for which to retrieve the credential
     * @param version  The version of the credential to retrieve
     * @return An Optional containing the credential data if found, empty Optional otherwise
     */
    Optional<Map<String, String>> retrieveCredentialVersion(String clientId, String version);

    /**
     * Configures Conjur vault for a credential transition period where both old and new credentials are valid.
     *
     * @param clientId               The client ID for which to configure the transition
     * @param oldVersion             The version of the old credential
     * @param newVersion             The version of the new credential
     * @param transitionPeriodMinutes The duration of the transition period in minutes
     * @return True if transition configuration was successful, false otherwise
     */
    boolean configureCredentialTransition(String clientId, String oldVersion, String newVersion, int transitionPeriodMinutes);

    /**
     * Disables a specific version of a credential in Conjur vault.
     *
     * @param clientId The client ID for which to disable the credential version
     * @param version  The version of the credential to disable
     * @return True if the credential version was disabled successfully, false otherwise
     */
    boolean disableCredentialVersion(String clientId, String version);

    /**
     * Removes a specific version of a credential from Conjur vault.
     *
     * @param clientId The client ID for which to remove the credential version
     * @param version  The version of the credential to remove
     * @return True if the credential version was removed successfully, false otherwise
     */
    boolean removeCredentialVersion(String clientId, String version);

    /**
     * Retrieves all active credential versions for a client ID.
     *
     * @param clientId The client ID for which to retrieve active credential versions
     * @return A map of version to credential data for all active versions
     */
    Map<String, Map<String, String>> getActiveCredentialVersions(String clientId);

    /**
     * Retrieves the current transition status for a credential.
     *
     * @param clientId The client ID for which to retrieve the transition status
     * @return An Optional containing transition status information if in transition, empty Optional otherwise
     */
    Optional<Map<String, Object>> getCredentialTransitionStatus(String clientId);

    /**
     * Checks if the service is connected to Conjur vault.
     *
     * @return True if connected to Conjur vault, false otherwise
     */
    boolean isConnected();
}