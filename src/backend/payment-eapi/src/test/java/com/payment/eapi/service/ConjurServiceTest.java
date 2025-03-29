package com.payment.eapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import com.cyberark.conjur.api.ConjurClient;
import com.payment.common.retry.RetryHandler;
import com.payment.common.util.SecurityUtils;
import com.payment.eapi.service.impl.ConjurServiceImpl;
import com.payment.eapi.model.Credential;
import com.payment.eapi.exception.ConjurException;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test class for the ConjurServiceImpl that tests all credential management operations with Conjur vault
 */
@ExtendWith(MockitoExtension.class)
public class ConjurServiceTest {

    private static final String TEST_CLIENT_ID = "test-client-id";
    private static final String TEST_CLIENT_SECRET = "test-client-secret";
    private static final String TEST_HASHED_SECRET = "hashed-test-secret";
    private static final String CREDENTIAL_PATH = "payment/api/credentials/test-client-id";

    @Mock
    private ConjurClient conjurClient;
    
    @Mock
    private RetryHandler retryHandler;
    
    @Mock
    private CacheService cacheService;
    
    @InjectMocks
    private ConjurServiceImpl conjurService;
    
    private Credential testCredential;

    /**
     * Set up test fixtures before each test
     */
    @BeforeEach
    void setUp() {
        testCredential = Credential.builder()
                .clientId(TEST_CLIENT_ID)
                .hashedSecret(TEST_HASHED_SECRET)
                .active(true)
                .build();
    }

    /**
     * Test successful credential retrieval from Conjur vault
     */
    @Test
    void testRetrieveCredentials_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Call conjurService.retrieveCredentials with TEST_CLIENT_ID
        Credential credential = conjurService.retrieveCredentials(TEST_CLIENT_ID);
        
        // Verify the returned credential matches expected values
        assertNotNull(credential);
        assertEquals(TEST_CLIENT_ID, credential.getClientId());
        assertTrue(credential.isActive());
        
        // Verify cacheService.cacheCredential was called with the credential
        verify(cacheService).cacheCredential(eq(TEST_CLIENT_ID), any(Credential.class));
    }

    /**
     * Test credential retrieval when Conjur vault connection fails
     */
    @Test
    void testRetrieveCredentials_ConjurConnectionFailure() throws Exception {
        // Set up retryHandler to throw ConjurException with CONNECTION_ERROR
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Connection failed", "CONNECTION_ERROR"));
        
        // Use assertThrows to verify ConjurException is thrown when retrieveCredentials is called
        ConjurException exception = assertThrows(ConjurException.class, () -> {
            conjurService.retrieveCredentials(TEST_CLIENT_ID);
        });
        
        // Verify the exception has the correct error code
        assertEquals("CONNECTION_ERROR", exception.getErrorCode());
    }

    /**
     * Test successful credential retrieval with fallback mechanism
     */
    @Test
    void testRetrieveCredentialsWithFallback_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Call conjurService.retrieveCredentialsWithFallback with TEST_CLIENT_ID
        Optional<Credential> result = conjurService.retrieveCredentialsWithFallback(TEST_CLIENT_ID);
        
        // Verify the returned optional contains the expected credential
        assertTrue(result.isPresent());
        Credential credential = result.get();
        assertEquals(TEST_CLIENT_ID, credential.getClientId());
        
        // Verify cacheService.cacheCredential was called
        verify(cacheService).cacheCredential(eq(TEST_CLIENT_ID), any(Credential.class));
    }

    /**
     * Test credential retrieval falling back to cache when Conjur vault is unavailable
     */
    @Test
    void testRetrieveCredentialsWithFallback_FallbackToCache() throws Exception {
        // Set up retryHandler to throw ConjurException with CONNECTION_ERROR
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Connection failed", "CONNECTION_ERROR"));
        
        // Set up cacheService to return cached credential
        when(cacheService.retrieveCredential(eq(TEST_CLIENT_ID))).thenReturn(Optional.of(testCredential));
        
        // Call conjurService.retrieveCredentialsWithFallback with TEST_CLIENT_ID
        Optional<Credential> result = conjurService.retrieveCredentialsWithFallback(TEST_CLIENT_ID);
        
        // Verify the returned optional contains the cached credential
        assertTrue(result.isPresent());
        Credential credential = result.get();
        assertEquals(TEST_CLIENT_ID, credential.getClientId());
        assertEquals(TEST_HASHED_SECRET, credential.getHashedSecret());
        
        // Verify cacheService.retrieveCredential was called
        verify(cacheService).retrieveCredential(eq(TEST_CLIENT_ID));
    }

    /**
     * Test credential retrieval when Conjur vault is unavailable and no cached credential exists
     */
    @Test
    void testRetrieveCredentialsWithFallback_NoFallback() throws Exception {
        // Set up retryHandler to throw ConjurException with CONNECTION_ERROR
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Connection failed", "CONNECTION_ERROR"));
        
        // Set up cacheService to return empty Optional
        when(cacheService.retrieveCredential(eq(TEST_CLIENT_ID))).thenReturn(Optional.empty());
        
        // Call conjurService.retrieveCredentialsWithFallback with TEST_CLIENT_ID
        Optional<Credential> result = conjurService.retrieveCredentialsWithFallback(TEST_CLIENT_ID);
        
        // Verify the returned optional is empty
        assertFalse(result.isPresent());
        
        // Verify cacheService.retrieveCredential was called
        verify(cacheService).retrieveCredential(eq(TEST_CLIENT_ID));
    }

    /**
     * Test successful credential validation
     */
    @Test
    void testValidateCredentials_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Mock SecurityUtils.validateCredential to return true
        when(SecurityUtils.validateCredential(eq(TEST_CLIENT_SECRET), any())).thenReturn(true);
        
        // Call conjurService.validateCredentials with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentials(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is true
        assertTrue(result);
    }

    /**
     * Test credential validation with invalid credentials
     */
    @Test
    void testValidateCredentials_InvalidCredentials() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Mock SecurityUtils.validateCredential to return false
        when(SecurityUtils.validateCredential(eq(TEST_CLIENT_SECRET), any())).thenReturn(false);
        
        // Call conjurService.validateCredentials with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentials(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is false
        assertFalse(result);
    }

    /**
     * Test credential validation with inactive credential
     */
    @Test
    void testValidateCredentials_InactiveCredential() throws Exception {
        // Create an inactive credential
        Credential inactiveCredential = Credential.builder()
                .clientId(TEST_CLIENT_ID)
                .hashedSecret(TEST_HASHED_SECRET)
                .active(false)
                .build();
        
        // Set up retryHandler to execute the lambda and return inactive credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenReturn(inactiveCredential);
        
        // Set up conjurClient to return inactive credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":false}");
        
        // Call conjurService.validateCredentials with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentials(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is false
        assertFalse(result);
        
        // Verify SecurityUtils.validateCredential was not called
        verify(SecurityUtils, never()).validateCredential(any(), any());
    }

    /**
     * Test credential validation when Conjur vault throws an exception
     */
    @Test
    void testValidateCredentials_ConjurError() throws Exception {
        // Set up retryHandler to throw ConjurException
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Retrieval error", "RETRIEVAL_ERROR"));
        
        // Call conjurService.validateCredentials with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentials(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is false
        assertFalse(result);
        
        // Verify SecurityUtils.validateCredential was not called
        verify(SecurityUtils, never()).validateCredential(any(), any());
    }

    /**
     * Test successful credential validation with fallback mechanism
     */
    @Test
    void testValidateCredentialsWithFallback_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        });

        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Mock SecurityUtils.validateCredential to return true
        when(SecurityUtils.validateCredential(eq(TEST_CLIENT_SECRET), any())).thenReturn(true);
        
        // Call conjurService.validateCredentialsWithFallback with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is true
        assertTrue(result);
        
        // Verify cacheService.retrieveCredential was not called
        verify(cacheService, never()).retrieveCredential(any());
    }

    /**
     * Test credential validation falling back to cache when Conjur vault is unavailable
     */
    @Test
    void testValidateCredentialsWithFallback_FallbackToCache() throws Exception {
        // Set up retryHandler to throw ConjurException
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Connection failed", "CONNECTION_ERROR"));
        
        // Set up cacheService to return cached credential
        when(cacheService.retrieveCredential(eq(TEST_CLIENT_ID))).thenReturn(Optional.of(testCredential));
        
        // Mock SecurityUtils.validateCredential to return true
        when(SecurityUtils.validateCredential(eq(TEST_CLIENT_SECRET), eq(TEST_HASHED_SECRET))).thenReturn(true);
        
        // Call conjurService.validateCredentialsWithFallback with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is true
        assertTrue(result);
        
        // Verify cacheService.retrieveCredential was called
        verify(cacheService).retrieveCredential(eq(TEST_CLIENT_ID));
        
        // Verify SecurityUtils.validateCredential was called with cached credential
        verify(SecurityUtils).validateCredential(eq(TEST_CLIENT_SECRET), eq(TEST_HASHED_SECRET));
    }

    /**
     * Test credential validation when Conjur vault is unavailable and no cached credential exists
     */
    @Test
    void testValidateCredentialsWithFallback_NoFallback() throws Exception {
        // Set up retryHandler to throw ConjurException
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Connection failed", "CONNECTION_ERROR"));
        
        // Set up cacheService to return empty Optional
        when(cacheService.retrieveCredential(eq(TEST_CLIENT_ID))).thenReturn(Optional.empty());
        
        // Call conjurService.validateCredentialsWithFallback with TEST_CLIENT_ID and TEST_CLIENT_SECRET
        boolean result = conjurService.validateCredentialsWithFallback(TEST_CLIENT_ID, TEST_CLIENT_SECRET);
        
        // Verify the result is false
        assertFalse(result);
        
        // Verify cacheService.retrieveCredential was called
        verify(cacheService).retrieveCredential(eq(TEST_CLIENT_ID));
        
        // Verify SecurityUtils.validateCredential was not called
        verify(SecurityUtils, never()).validateCredential(any(), any());
    }

    /**
     * Test successful credential storage in Conjur vault
     */
    @Test
    void testStoreCredentials_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return true
        when(retryHandler.executeWithRetry(any(Callable.class))).thenReturn(true);
        
        // Call conjurService.storeCredentials with TEST_CLIENT_ID and testCredential
        boolean result = conjurService.storeCredentials(TEST_CLIENT_ID, testCredential);
        
        // Verify the result is true
        assertTrue(result);
        
        // Verify conjurClient.storeSecret was called with correct parameters
        verify(conjurClient, never()).storeSecret(eq(CREDENTIAL_PATH), any(String.class));
        
        // Verify cacheService.invalidateCredential was called
        verify(cacheService).invalidateCredential(eq(TEST_CLIENT_ID));
        
        // Verify cacheService.cacheCredential was called with the new credential
        verify(cacheService).cacheCredential(eq(TEST_CLIENT_ID), eq(testCredential));
    }

    /**
     * Test credential storage failure in Conjur vault
     */
    @Test
    void testStoreCredentials_Failure() throws Exception {
        // Set up retryHandler to throw ConjurException
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Storage error", "STORAGE_ERROR"));
        
        // Call conjurService.storeCredentials with TEST_CLIENT_ID and testCredential
        boolean result = conjurService.storeCredentials(TEST_CLIENT_ID, testCredential);
        
        // Verify the result is false
        assertFalse(result);
        
        // Verify cacheService.invalidateCredential was not called
        verify(cacheService, never()).invalidateCredential(any());
        
        // Verify cacheService.cacheCredential was not called
        verify(cacheService, never()).cacheCredential(any(), any());
    }

    /**
     * Test successful update of credential rotation state
     */
    @Test
    void testUpdateCredentialRotationState_Success() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        }).thenReturn(true);
        
        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Call conjurService.updateCredentialRotationState with TEST_CLIENT_ID and 'DUAL_ACTIVE'
        boolean result = conjurService.updateCredentialRotationState(TEST_CLIENT_ID, "DUAL_ACTIVE");
        
        // Verify the result is true
        assertTrue(result);
    }

    /**
     * Test rotation state update when credential retrieval fails
     */
    @Test
    void testUpdateCredentialRotationState_RetrievalFailure() throws Exception {
        // Set up retryHandler to throw ConjurException during credential retrieval
        when(retryHandler.executeWithRetry(any(Callable.class)))
            .thenThrow(new ConjurException("Retrieval error", "RETRIEVAL_ERROR"));
        
        // Call conjurService.updateCredentialRotationState with TEST_CLIENT_ID and 'DUAL_ACTIVE'
        boolean result = conjurService.updateCredentialRotationState(TEST_CLIENT_ID, "DUAL_ACTIVE");
        
        // Verify the result is false
        assertFalse(result);
    }

    /**
     * Test rotation state update when credential storage fails
     */
    @Test
    void testUpdateCredentialRotationState_StoreFailure() throws Exception {
        // Set up retryHandler to execute the lambda and return credential JSON for retrieval
        when(retryHandler.executeWithRetry(any(Callable.class))).thenAnswer(invocation -> {
            Callable<Credential> callable = invocation.getArgument(0);
            return callable.call();
        }).thenReturn(false);
        
        // Set up conjurClient to return credential when retrieving secret
        when(conjurClient.isAuthenticated()).thenReturn(true);
        when(conjurClient.retrieveSecret(eq(CREDENTIAL_PATH))).thenReturn("{\"clientId\":\"test-client-id\",\"hashedSecret\":\"hashed-test-secret\",\"active\":true}");
        
        // Call conjurService.updateCredentialRotationState with TEST_CLIENT_ID and 'DUAL_ACTIVE'
        boolean result = conjurService.updateCredentialRotationState(TEST_CLIENT_ID, "DUAL_ACTIVE");
        
        // Verify the result is false
        assertFalse(result);
    }

    /**
     * Test successful Conjur vault availability check
     */
    @Test
    void testIsAvailable_Success() throws Exception {
        // Set up conjurClient.authenticate to complete successfully
        when(conjurClient.isAuthenticated()).thenReturn(false);
        
        // Call conjurService.isAvailable
        boolean result = conjurService.isAvailable();
        
        // Verify the result is true
        assertTrue(result);
        
        // Verify conjurClient.authenticate was called
        verify(conjurClient).authenticate();
    }

    /**
     * Test Conjur vault availability check when vault is unavailable
     */
    @Test
    void testIsAvailable_Failure() throws Exception {
        // Set up conjurClient.authenticate to throw an exception
        when(conjurClient.isAuthenticated()).thenReturn(false);
        doThrow(new RuntimeException("Authentication failed")).when(conjurClient).authenticate();
        
        // Call conjurService.isAvailable
        boolean result = conjurService.isAvailable();
        
        // Verify the result is false
        assertFalse(result);
        
        // Verify conjurClient.authenticate was called
        verify(conjurClient).authenticate();
    }
}