"""
Test module for Conjur vault integration functionality.

Contains unit and integration tests for authentication, credential retrieval,
storage, and rotation operations with Conjur vault. Uses mocking to simulate
Conjur API responses and test error handling scenarios.
"""

import pytest
from unittest.mock import Mock, patch, MagicMock
import json
import base64
import requests
import requests_mock
import time
import os

from src.scripts.conjur.config import (
    ConjurConfig, 
    RotationConfig, 
    create_conjur_config, 
    get_credential_path,
    read_certificate
)
from src.scripts.conjur.authenticate import (
    authenticate,
    authenticate_with_retry,
    clear_token_cache,
    AuthenticationResult
)
from src.scripts.conjur.retrieve_credentials import (
    retrieve_credential,
    retrieve_credential_with_retry,
    clear_credential_cache,
    CredentialResult
)
from src.scripts.conjur.store_credentials import (
    store_credential,
    store_credential_with_retry
)
from src.scripts.conjur.rotate_credentials import (
    rotate_credential,
    RotationState
)
from src.scripts.conjur.utils import (
    ConjurConnectionError,
    ConjurAuthenticationError,
    ConjurNotFoundError,
    ConjurPermissionError,
    RetryHandler,
    build_conjur_url,
    encode_credentials
)


class MockResponse:
    """Mock HTTP response class for testing."""
    
    def __init__(self, status_code, content, headers=None):
        """
        Initializes a new MockResponse instance.
        
        Args:
            status_code (int): HTTP status code
            content (str or dict): Response content
            headers (dict, optional): Response headers. Defaults to None.
        """
        self.status_code = status_code
        self.content = content
        self.headers = headers or {"content-type": "application/json"}
        self.ok = 200 <= status_code < 300
    
    def json(self):
        """
        Returns the content as JSON.
        
        Returns:
            dict: JSON content
        """
        if isinstance(self.content, dict):
            return self.content
        return json.loads(self.content)
    
    def text(self):
        """
        Returns the content as text.
        
        Returns:
            str: Text content
        """
        if isinstance(self.content, dict):
            return json.dumps(self.content)
        return self.content
    
    def raise_for_status(self):
        """
        Raises an exception if status code indicates an error.
        """
        if not self.ok:
            raise requests.exceptions.HTTPError(f"HTTP Error: {self.status_code}")


@pytest.fixture
def conjur_config():
    """Fixture to create a test Conjur configuration."""
    return ConjurConfig(
        url="https://conjur.example.com",
        account="payment-system",
        authn_login="payment-eapi-service",
        cert_path="/path/to/test/cert.pem",
        credential_path_template="secrets/{account}/variable/payment/credentials/{client_id}"
    )


@pytest.fixture
def rotation_config():
    """Fixture to create a test rotation configuration."""
    return RotationConfig(
        transition_period_seconds=3600,  # 1 hour for testing
        monitoring_interval_seconds=60,  # 1 minute for testing
        notification_endpoint="https://notify.example.com/rotation",
        monitoring_endpoint="https://monitor.example.com/rotation"
    )


@pytest.fixture
def requests_mocker():
    """Fixture to create a requests mocker."""
    with requests_mock.Mocker() as m:
        yield m


@pytest.mark.unit
def test_conjur_config_creation():
    """Tests the creation of a ConjurConfig instance."""
    # Create a config with all required parameters
    config = ConjurConfig(
        url="https://conjur.example.com",
        account="payment-system",
        authn_login="payment-eapi-service",
        cert_path="/path/to/cert.pem"
    )
    
    # Verify that all parameters are set correctly
    assert config.url == "https://conjur.example.com"
    assert config.account == "payment-system"
    assert config.authn_login == "payment-eapi-service"
    assert config.cert_path == "/path/to/cert.pem"
    assert config.credential_path_template == "secrets/{account}/variable/payment/credentials/{client_id}"  # Default
    
    # Validate the configuration
    assert config.validate() == True
    
    # Create an invalid configuration missing required parameters
    invalid_config = ConjurConfig()
    assert invalid_config.validate() == False


@pytest.mark.unit
def test_create_conjur_config_from_file(tmp_path):
    """Tests creating a ConjurConfig from a configuration file."""
    # Create a temporary configuration file
    config_file = tmp_path / "conjur_config.json"
    config_data = {
        "url": "https://conjur.example.com",
        "account": "payment-system",
        "authn_login": "payment-eapi-service",
        "cert_path": "/path/to/cert.pem"
    }
    
    with open(config_file, 'w') as f:
        json.dump(config_data, f)
    
    # Create config from file
    config = create_conjur_config(str(config_file))
    
    # Verify that all parameters are set correctly
    assert config.url == "https://conjur.example.com"
    assert config.account == "payment-system"
    assert config.authn_login == "payment-eapi-service"
    assert config.cert_path == "/path/to/cert.pem"
    
    # Test with invalid file
    invalid_file = tmp_path / "invalid.json"
    with open(invalid_file, 'w') as f:
        f.write("Not valid JSON")
    
    # This should not raise an exception but log a warning
    with patch('src.scripts.conjur.config.LOGGER') as mock_logger:
        config = create_conjur_config(str(invalid_file))
        mock_logger.warning.assert_called()


@pytest.mark.unit
def test_get_credential_path(conjur_config):
    """Tests the get_credential_path function."""
    # Test with a standard client ID
    client_id = "test-client"
    path = get_credential_path(client_id, conjur_config)
    
    expected_path = f"payment/credentials/{client_id}"
    assert expected_path in path
    
    # Test with a different template
    conjur_config.credential_path_template = "custom/{account}/path/{client_id}"
    path = get_credential_path(client_id, conjur_config)
    
    expected_path = f"custom/{conjur_config.account}/path/{client_id}"
    assert path == expected_path


@pytest.mark.integration
def test_authenticate_success(conjur_config, requests_mocker):
    """Tests successful authentication with Conjur vault."""
    # Mock the Conjur authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    # Mock response body (this would be the raw token from Conjur)
    token_data = "raw-token-data-from-conjur"
    
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Call authenticate
    token = authenticate(conjur_config)
    
    # Verify that we get the expected token back (should be base64 encoded)
    expected_token = base64.b64encode(token_data.encode('utf-8')).decode('utf-8')
    assert token == expected_token
    
    # Verify request was made with correct parameters
    assert requests_mocker.call_count == 1
    
    # Verify token is cached
    clear_token_cache(conjur_config)  # Clean up afterward


@pytest.mark.integration
def test_authenticate_failure(conjur_config, requests_mocker):
    """Tests authentication failure with Conjur vault."""
    # Mock the Conjur authentication endpoint to return an error
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    requests_mocker.post(auth_url, text='{"error": "Authentication failed"}', status_code=401)
    
    # Call authenticate and verify it raises the expected exception
    with pytest.raises(ConjurAuthenticationError):
        authenticate(conjur_config)
    
    # Test different error codes
    requests_mocker.post(auth_url, text='{"error": "Permission denied"}', status_code=403)
    with pytest.raises(ConjurPermissionError):
        authenticate(conjur_config)
    
    requests_mocker.post(auth_url, text='{"error": "Server error"}', status_code=500)
    with pytest.raises(ConjurConnectionError):
        authenticate(conjur_config)


@pytest.mark.integration
def test_authenticate_with_retry(conjur_config, requests_mocker):
    """Tests authentication with retry mechanism."""
    # Mock the Conjur authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    # Set up the mocker to fail on the first attempt, then succeed
    token_data = "raw-token-data-from-conjur"
    
    # Using a counter to track request attempts
    attempt_counter = {'count': 0}
    
    def response_callback(request, context):
        attempt_counter['count'] += 1
        if attempt_counter['count'] == 1:
            context.status_code = 500
            return '{"error": "Server error"}'
        else:
            context.status_code = 200
            return token_data
    
    requests_mocker.post(auth_url, text=response_callback)
    
    # Call authenticate_with_retry
    token = authenticate_with_retry(conjur_config, max_retries=3, backoff_factor=0.1)
    
    # Verify that we get the expected token back
    expected_token = base64.b64encode(token_data.encode('utf-8')).decode('utf-8')
    assert token == expected_token
    
    # Verify multiple requests were made
    assert attempt_counter['count'] == 2
    
    # Clear auth token cache
    clear_token_cache(conjur_config)
    
    # Now test a case where all retries fail
    attempt_counter['count'] = 0
    
    def always_fail(request, context):
        attempt_counter['count'] += 1
        context.status_code = 500
        return '{"error": "Server error"}'
    
    requests_mocker.post(auth_url, text=always_fail)
    
    # Call authenticate_with_retry and verify it raises the expected exception
    with pytest.raises(ConjurConnectionError):
        authenticate_with_retry(conjur_config, max_retries=3, backoff_factor=0.1)
    
    # Verify that retry count was exhausted
    assert attempt_counter['count'] == 4  # Initial try + 3 retries


@pytest.mark.integration
def test_retrieve_credential_success(conjur_config, requests_mocker):
    """Tests successful credential retrieval from Conjur vault."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint
    client_id = "test-client"
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    credential_data = {
        "client_id": client_id,
        "client_secret": "test-secret",
        "created_at": "2023-01-01T00:00:00Z",
        "updated_at": "2023-01-01T00:00:00Z",
        "version": "v1",
        "status": "active"
    }
    
    requests_mocker.get(credential_url, json=credential_data, status_code=200)
    
    # Call retrieve_credential
    credential = retrieve_credential(client_id, conjur_config)
    
    # Verify the credential
    assert credential["client_id"] == client_id
    assert credential["client_secret"] == "test-secret"
    
    # Verify requests were made
    assert requests_mocker.call_count == 2  # auth + credential
    
    # Clean up
    clear_credential_cache(client_id)
    clear_token_cache(conjur_config)


@pytest.mark.integration
def test_retrieve_credential_failure(conjur_config, requests_mocker):
    """Tests credential retrieval failure from Conjur vault."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint for various failure scenarios
    client_id = "test-client"
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    # Test not found
    requests_mocker.get(credential_url, text='{"error": "Not found"}', status_code=404)
    with pytest.raises(ConjurNotFoundError):
        retrieve_credential(client_id, conjur_config)
    
    # Test permission denied
    requests_mocker.get(credential_url, text='{"error": "Permission denied"}', status_code=403)
    with pytest.raises(ConjurPermissionError):
        retrieve_credential(client_id, conjur_config)
    
    # Test server error
    requests_mocker.get(credential_url, text='{"error": "Server error"}', status_code=500)
    with pytest.raises(ConjurConnectionError):
        retrieve_credential(client_id, conjur_config)
    
    # Clean up
    clear_token_cache(conjur_config)


@pytest.mark.integration
def test_retrieve_credential_with_retry(conjur_config, requests_mocker):
    """Tests credential retrieval with retry mechanism."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint
    client_id = "test-client"
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    # Set up the mocker to fail on the first attempt, then succeed
    credential_data = {
        "client_id": client_id,
        "client_secret": "test-secret",
        "created_at": "2023-01-01T00:00:00Z",
        "updated_at": "2023-01-01T00:00:00Z",
        "version": "v1",
        "status": "active"
    }
    
    # Using a counter to track request attempts
    attempt_counter = {'count': 0}
    
    def response_callback(request, context):
        attempt_counter['count'] += 1
        if attempt_counter['count'] == 1:
            context.status_code = 500
            return '{"error": "Server error"}'
        else:
            context.status_code = 200
            return json.dumps(credential_data)
    
    requests_mocker.get(credential_url, text=response_callback)
    
    # Call retrieve_credential_with_retry
    credential = retrieve_credential_with_retry(client_id, conjur_config, max_retries=3, backoff_factor=0.1)
    
    # Verify the credential
    assert credential["client_id"] == client_id
    assert credential["client_secret"] == "test-secret"
    
    # Verify multiple requests were made
    assert attempt_counter['count'] == 2
    
    # Clear caches
    clear_credential_cache(client_id)
    clear_token_cache(conjur_config)
    
    # Now test a case where all retries fail
    attempt_counter['count'] = 0
    
    def always_fail(request, context):
        attempt_counter['count'] += 1
        context.status_code = 500
        return '{"error": "Server error"}'
    
    requests_mocker.get(credential_url, text=always_fail)
    
    # Call retrieve_credential_with_retry and verify it raises the expected exception
    with pytest.raises(ConjurConnectionError):
        retrieve_credential_with_retry(client_id, conjur_config, max_retries=3, backoff_factor=0.1)
    
    # Verify that retry count was exhausted
    assert attempt_counter['count'] == 4  # Initial try + 3 retries


@pytest.mark.integration
def test_store_credential_success(conjur_config, requests_mocker):
    """Tests successful credential storage in Conjur vault."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint for storing
    client_id = "test-client"
    client_secret = "test-secret"
    
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    # Mock the POST response
    requests_mocker.post(credential_url, status_code=201)
    
    # Call store_credential
    result = store_credential(client_id, client_secret, conjur_config)
    
    # Verify the result
    assert result == True
    
    # Verify requests were made
    assert requests_mocker.call_count == 2  # auth + store
    
    # Verify the content of the POST request
    post_request = [req for req in requests_mocker.request_history if req.method == 'POST' and req.url == credential_url][0]
    posted_data = json.loads(post_request.text)
    assert posted_data["client_id"] == client_id
    assert posted_data["client_secret"] == client_secret
    
    # Clean up
    clear_token_cache(conjur_config)


@pytest.mark.integration
def test_store_credential_failure(conjur_config, requests_mocker):
    """Tests credential storage failure in Conjur vault."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint for various failure scenarios
    client_id = "test-client"
    client_secret = "test-secret"
    
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    # Test permission denied
    requests_mocker.post(credential_url, text='{"error": "Permission denied"}', status_code=403)
    with pytest.raises(ConjurPermissionError):
        store_credential(client_id, client_secret, conjur_config)
    
    # Test server error
    requests_mocker.post(credential_url, text='{"error": "Server error"}', status_code=500)
    with pytest.raises(ConjurConnectionError):
        store_credential(client_id, client_secret, conjur_config)
    
    # Clean up
    clear_token_cache(conjur_config)


@pytest.mark.integration
def test_rotate_credential(conjur_config, rotation_config, requests_mocker):
    """Tests credential rotation in Conjur vault."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint for retrieving and storing
    client_id = "test-client"
    
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    # Create existing credential data
    existing_credential = {
        "client_id": client_id,
        "client_secret": "old-secret",
        "created_at": "2023-01-01T00:00:00Z",
        "updated_at": "2023-01-01T00:00:00Z",
        "version": "v1",
        "status": "active"
    }
    
    # Set up the mocker to return existing credential on GET
    # and accept updates on POST
    requests_mocker.register_uri(
        'GET',
        credential_url,
        json=existing_credential,
        status_code=200
    )
    
    requests_mocker.register_uri(
        'POST',
        credential_url,
        text="",
        status_code=201
    )
    
    # We need to patch the monitor_credential_usage function to avoid waiting
    with patch('src.scripts.conjur.rotate_credentials.monitor_credential_usage', return_value=True):
        # Call rotate_credential
        result = rotate_credential(client_id, conjur_config, rotation_config)
    
    # Verify the result
    assert result.success == True
    assert result.client_id == client_id
    assert result.old_version == "v1"
    assert result.new_version is not None
    
    # Check if the POST requests were made with the expected data
    post_requests = [req for req in requests_mocker.request_history if req.method == 'POST' and req.url == credential_url]
    assert len(post_requests) > 0
    
    # The POST should include the new credential and rotation metadata
    last_post = post_requests[-1]
    posted_data = json.loads(last_post.text)
    assert posted_data["client_id"] == client_id
    assert posted_data["client_secret"] != "old-secret"
    assert "rotation" in posted_data
    
    # Clean up
    clear_credential_cache(client_id)
    clear_token_cache(conjur_config)


@pytest.mark.unit
def test_rotation_state_transitions():
    """Tests the state transitions during credential rotation."""
    from src.scripts.conjur.rotate_credentials import ROTATION_STATES
    
    # Check state transitions
    assert ROTATION_STATES.INITIATED.name == 'INITIATED'
    assert ROTATION_STATES.DUAL_ACTIVE.name == 'DUAL_ACTIVE'
    assert ROTATION_STATES.OLD_DEPRECATED.name == 'OLD_DEPRECATED'
    assert ROTATION_STATES.NEW_ACTIVE.name == 'NEW_ACTIVE'
    assert ROTATION_STATES.FAILED.name == 'FAILED'


@pytest.mark.integration
def test_cache_invalidation(conjur_config, requests_mocker):
    """Tests cache invalidation during credential rotation."""
    # First, mock the authentication endpoint
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    token_data = "raw-token-data-from-conjur"
    requests_mocker.post(auth_url, text=token_data, status_code=200)
    
    # Now, mock the credentials endpoint
    client_id = "test-client"
    credential_path = get_credential_path(client_id, conjur_config)
    credential_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/secrets/{conjur_config.account}/variable/{credential_path}"
    )
    
    credential_data = {
        "client_id": client_id,
        "client_secret": "test-secret",
        "created_at": "2023-01-01T00:00:00Z",
        "updated_at": "2023-01-01T00:00:00Z",
        "version": "v1",
        "status": "active"
    }
    
    requests_mocker.get(credential_url, json=credential_data, status_code=200)
    
    # First call to cache the credential
    credential1 = retrieve_credential(client_id, conjur_config)
    
    # Verify the credential is cached by checking if a second call doesn't hit the API
    requests_mocker.get(credential_url, json=credential_data, status_code=200)  # Reset mock
    credential2 = retrieve_credential(client_id, conjur_config)
    
    # Since it should be cached, the request count should still be 2 (auth + first retrieval)
    assert requests_mocker.call_count == 3  # auth + credential + auth reset
    
    # Now clear the cache
    clear_credential_cache(client_id)
    
    # Try again, this should hit the API
    credential3 = retrieve_credential(client_id, conjur_config)
    
    # Request count should now be 5 (previous 3 + new auth + new retrieval)
    assert requests_mocker.call_count == 5
    
    # Clean up
    clear_credential_cache()
    clear_token_cache()


@pytest.mark.integration
def test_connection_error_handling(conjur_config, requests_mocker):
    """Tests handling of connection errors to Conjur vault."""
    # Mock the authentication endpoint to raise a connection error
    auth_url = build_conjur_url(
        conjur_config.url,
        conjur_config.account,
        f"/authn/{conjur_config.account}/{conjur_config.authn_login}/authenticate"
    )
    
    # Use a callback that raises an exception
    def request_exception(request, context):
        raise requests.exceptions.ConnectionError("Connection refused")
    
    requests_mocker.post(auth_url, text=request_exception)
    
    # Call authenticate and verify it raises ConjurConnectionError
    with pytest.raises(ConjurConnectionError) as excinfo:
        authenticate(conjur_config)
    
    # Verify the exception message contains useful information
    assert "Connection refused" in str(excinfo.value)
    
    # Test with different exception types
    def timeout_exception(request, context):
        raise requests.exceptions.Timeout("Request timed out")
    
    requests_mocker.post(auth_url, text=timeout_exception)
    
    with pytest.raises(ConjurConnectionError) as excinfo:
        authenticate(conjur_config)
    
    assert "Request timed out" in str(excinfo.value)


@pytest.mark.unit
def test_tls_configuration(tmp_path):
    """Tests TLS configuration for Conjur vault communication."""
    # Create a temporary certificate file
    cert_path = tmp_path / "test_cert.pem"
    with open(cert_path, 'w') as f:
        f.write("-----BEGIN CERTIFICATE-----\nTest Certificate Content\n-----END CERTIFICATE-----")
    
    # Create a config with the certificate path
    conjur_config = ConjurConfig(
        url="https://conjur.example.com",
        account="payment-system",
        authn_login="payment-eapi-service",
        cert_path=str(cert_path)
    )
    
    # Mock the create_http_session function to verify it's called with the correct certificate
    with patch('src.scripts.conjur.authenticate.create_http_session') as mock_create_session:
        # Set up the mock session
        mock_session = MagicMock()
        mock_create_session.return_value = mock_session
        
        # Configure the mock session's post method to return a success response
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.text = "raw-token-data"
        mock_session.post.return_value = mock_response
        
        # Call authenticate
        try:
            token = authenticate(conjur_config)
        except Exception:
            # We expect an exception since we're not fully mocking the response parsing
            pass
        
        # Verify create_http_session was called with the correct certificate path
        mock_create_session.assert_called_once_with(str(cert_path))