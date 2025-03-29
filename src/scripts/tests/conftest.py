import os
import json
import datetime
import uuid
import pytest
import requests_mock
import fakeredis
import psycopg2

# Internal imports
from src.scripts.conjur.config import ConjurConfig, RotationConfig
from src.scripts.utilities.utils import create_db_connection, create_redis_connection
from src.scripts.utilities.config import DatabaseConfig, RedisConfig

# Test configuration paths
TEST_DATA_DIR = os.path.join(os.path.dirname(__file__), 'test_data')
TEST_CONFIG_PATH = os.path.join(TEST_DATA_DIR, 'test_config.json')

# Default test configurations
TEST_DB_CONFIG = """{"host": "localhost", "port": 5432, "dbname": "test_payment", "username": "test_user", "password": "test_password", "connect_timeout": 5, "read_timeout": 10}"""
TEST_REDIS_CONFIG = """{"host": "localhost", "port": 6379, "password": "test_password", "ssl": false, "socket_timeout": 5}"""
TEST_CONJUR_CONFIG = """{"url": "http://localhost:8080", "account": "test-account", "authn_login": "test-service", "cert_path": null, "credential_path_template": "secrets/{account}/variable/payment/credentials/{client_id}"}"""

def pytest_configure(config):
    """
    Pytest hook to configure the test environment before tests run.
    
    Args:
        config (pytest.Config): config
    """
    # Create test_data directory if it doesn't exist
    os.makedirs(TEST_DATA_DIR, exist_ok=True)
    
    # Create test configuration files if they don't exist
    create_test_config_files()
    
    # Set up environment variables for testing
    os.environ["TESTING"] = "true"
    os.environ["CONJUR_URL"] = "http://localhost:8080"
    os.environ["CONJUR_ACCOUNT"] = "test-account"
    os.environ["CONJUR_AUTHN_LOGIN"] = "test-service"

def pytest_unconfigure(config):
    """
    Pytest hook to clean up the test environment after tests run.
    
    Args:
        config (pytest.Config): config
    """
    # Clean up any temporary resources created during testing
    # Reset environment variables
    if "TESTING" in os.environ:
        del os.environ["TESTING"]
    if "CONJUR_URL" in os.environ:
        del os.environ["CONJUR_URL"]
    if "CONJUR_ACCOUNT" in os.environ:
        del os.environ["CONJUR_ACCOUNT"]
    if "CONJUR_AUTHN_LOGIN" in os.environ:
        del os.environ["CONJUR_AUTHN_LOGIN"]

def create_test_config_files():
    """
    Creates test configuration files for testing.
    """
    # Create test_data directory if it doesn't exist
    os.makedirs(TEST_DATA_DIR, exist_ok=True)
    
    # Create test configuration file if it doesn't exist
    if not os.path.exists(TEST_CONFIG_PATH):
        with open(TEST_CONFIG_PATH, 'w') as f:
            config = {
                "database": json.loads(TEST_DB_CONFIG),
                "redis": json.loads(TEST_REDIS_CONFIG),
                "conjur": json.loads(TEST_CONJUR_CONFIG),
                "test_mode": True
            }
            json.dump(config, f, indent=4)

@pytest.fixture
def test_config():
    """
    Provides test configuration dictionary for tests.
    
    Returns:
        dict: Test configuration
    """
    if os.path.exists(TEST_CONFIG_PATH):
        with open(TEST_CONFIG_PATH, 'r') as f:
            return json.load(f)
    else:
        return {
            "database": json.loads(TEST_DB_CONFIG),
            "redis": json.loads(TEST_REDIS_CONFIG),
            "conjur": json.loads(TEST_CONJUR_CONFIG),
            "test_mode": True
        }

@pytest.fixture
def db_connection(test_config):
    """
    Provides a database connection for tests.
    
    Args:
        test_config: Test configuration fixture
    
    Returns:
        Connection object or Mock: Database connection
    """
    try:
        # Create a database configuration object
        db_config = DatabaseConfig.from_dict(test_config["database"])
        
        # Use the imported connection function
        try:
            conn = create_db_connection(db_config)
        except (NameError, AttributeError):
            # Fallback to direct connection if function not available
            conn = psycopg2.connect(
                host=db_config.host,
                port=db_config.port,
                dbname=db_config.dbname,
                user=db_config.username,
                password=db_config.password,
                connect_timeout=db_config.connect_timeout
            )
        
        # Yield the connection to the test
        yield conn
        
        # Close the connection after the test
        conn.close()
    except Exception as e:
        # If we can't connect, yield a mock connection for testing
        import unittest.mock as mock
        print(f"Could not connect to database: {str(e)}")
        print("Using mock database connection")
        mock_conn = mock.MagicMock()
        mock_cur = mock.MagicMock()
        mock_conn.cursor.return_value = mock_cur
        yield mock_conn

@pytest.fixture
def fake_redis(test_config):
    """
    Provides a fake Redis instance for tests.
    
    Args:
        test_config: Test configuration fixture
    
    Returns:
        FakeRedis: Fake Redis instance
    """
    # Create a Redis configuration object
    redis_config = RedisConfig.from_dict(test_config["redis"])
    
    # Create a fake Redis instance
    try:
        # Try to use the imported function first
        redis_instance = create_redis_connection(redis_config)
    except (NameError, AttributeError):
        # Fallback to FakeRedis for testing
        redis_instance = fakeredis.FakeRedis()
    
    # Yield the Redis instance to the test
    yield redis_instance
    
    # Clean up the Redis instance after the test
    redis_instance.flushall()

@pytest.fixture
def requests_mocker():
    """
    Provides a requests mocker for HTTP request mocking.
    
    Returns:
        RequestsMocker: Requests mocker instance
    """
    with requests_mock.Mocker() as m:
        yield m

@pytest.fixture
def conjur_config(test_config):
    """
    Provides a ConjurConfig instance for tests.
    
    Args:
        test_config: Test configuration fixture
    
    Returns:
        ConjurConfig: ConjurConfig instance
    """
    return ConjurConfig.from_dict(test_config["conjur"])

@pytest.fixture
def rotation_config():
    """
    Provides a RotationConfig instance for tests.
    
    Returns:
        RotationConfig: RotationConfig instance
    """
    return RotationConfig(
        transition_period_seconds=300,  # 5 minutes for testing
        monitoring_interval_seconds=60,  # 1 minute for testing
        notification_endpoint="http://localhost:8000/notify",
        monitoring_endpoint="http://localhost:8000/monitor"
    )

@pytest.fixture
def test_client_credentials():
    """
    Provides test client credentials for authentication tests.
    
    Returns:
        dict: Test client credentials
    """
    return {
        "client_id": "test-client",
        "client_secret": "test-secret",
        "active": True,
        "created_at": datetime.datetime.now().timestamp() - 86400,  # 1 day ago
        "updated_at": datetime.datetime.now().timestamp() - 43200,  # 12 hours ago
        "version": "1.0",
        "rotation_state": None
    }

@pytest.fixture
def test_token_data():
    """
    Provides test token data for token validation tests.
    
    Returns:
        dict: Test token data
    """
    token_id = str(uuid.uuid4())
    client_id = "test-client"
    created_at = datetime.datetime.now().timestamp()
    expires_at = created_at + 3600  # 1 hour expiration
    
    return {
        "token_id": token_id,
        "client_id": client_id,
        "created_at": created_at,
        "expires_at": expires_at,
        "status": "ACTIVE",
        "iss": "payment-eapi",
        "sub": client_id,
        "aud": "payment-sapi",
        "exp": expires_at,
        "iat": created_at,
        "jti": token_id,
        "permissions": ["process_payment", "view_status"]
    }

@pytest.fixture
def mock_db_manager():
    """
    Provides a mock database manager for tests.
    
    Returns:
        MagicMock: Mock database manager
    """
    from unittest.mock import MagicMock
    
    mock_manager = MagicMock()
    
    # Configure common methods
    mock_manager.connect.return_value = True
    mock_manager.disconnect.return_value = True
    
    # Mock execute_query to return sample data based on the query
    def mock_execute_query(query, params=(), fetch_all=False):
        # Check if it's a token query
        if "TOKEN_METADATA" in query and "token_id" in str(params):
            if fetch_all:
                return [
                    ("token-1", "test-client", datetime.datetime.now().timestamp(), datetime.datetime.now().timestamp() + 3600, "ACTIVE"),
                    ("token-2", "test-client", datetime.datetime.now().timestamp(), datetime.datetime.now().timestamp() - 3600, "ACTIVE")  # Expired
                ]
            else:
                token_id = params[0] if params else "unknown"
                if token_id == "expired-token":
                    # Return an expired token
                    return ("test-client", datetime.datetime.now().timestamp() - 7200, datetime.datetime.now().timestamp() - 3600, "ACTIVE")
                else:
                    # Return a valid token
                    return ("test-client", datetime.datetime.now().timestamp(), datetime.datetime.now().timestamp() + 3600, "ACTIVE")
        
        # Default response for other queries
        return [] if fetch_all else None
    
    mock_manager.execute_query.side_effect = mock_execute_query
    
    # Mock delete_token method
    mock_manager.delete_token.return_value = True
    
    # Add a connection attribute with a commit method
    mock_manager.connection = MagicMock()
    mock_manager.connection.commit.return_value = None
    mock_manager.connection.rollback.return_value = None
    
    return mock_manager

@pytest.fixture
def mock_redis_manager():
    """
    Provides a mock Redis manager for tests.
    
    Returns:
        MagicMock: Mock Redis manager
    """
    from unittest.mock import MagicMock
    
    mock_manager = MagicMock()
    
    # Configure common methods
    mock_manager.connect.return_value = True
    mock_manager.disconnect.return_value = True
    
    # Mock token storage
    token_storage = {}
    
    # Mock get_token method
    def mock_get_token(token_id):
        return token_storage.get(token_id)
    
    mock_manager.get_token.side_effect = mock_get_token
    
    # Mock store_token method
    def mock_store_token(token_id, token_data, expiration_seconds=3600):
        token_storage[token_id] = token_data
        return True
    
    mock_manager.store_token.side_effect = mock_store_token
    
    # Mock delete_token method
    def mock_delete_token(token_id):
        if token_id in token_storage:
            del token_storage[token_id]
            return True
        return False
    
    mock_manager.delete_token.side_effect = mock_delete_token
    
    return mock_manager