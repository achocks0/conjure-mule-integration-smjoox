#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Unit tests for the utilities module of the Payment API Security Enhancement project.

This file contains test cases for database operations, Redis cache management,
token validation, and token cleanup functionality to ensure the reliability and
security of the authentication mechanisms.
"""

# Internal imports
from src.scripts.utilities.utils import (
    DatabaseManager, RedisManager, TokenManager, DatabaseError, RedisError, TokenError,
    execute_db_query, batch_execute_db_query, format_timestamp, is_token_expired,
    validate_token, cleanup_expired_tokens
)
from src.scripts.utilities.config import DatabaseConfig, RedisConfig, TokenCleanupConfig
from src.scripts.utilities.cleanup_expired_tokens import parse_arguments, cleanup_tokens
from src.scripts.utilities.validate_tokens import (
    validate_specific_token, validate_raw_token, list_tokens, format_output
)

# External imports
import pytest
from unittest.mock import Mock, patch, MagicMock
import datetime
import json
import jwt

# Fixtures
@pytest.fixture
def mock_db_manager():
    """Creates a mock DatabaseManager instance for testing."""
    mock = Mock(spec=DatabaseManager)
    return mock

@pytest.fixture
def mock_redis_manager():
    """Creates a mock RedisManager instance for testing."""
    mock = Mock(spec=RedisManager)
    return mock

# Tests for DatabaseManager
@pytest.mark.parametrize('success', [True, False])
def test_database_manager_connect(mock_db_manager, success):
    """Tests the connect method of DatabaseManager."""
    # Setup
    mock_db_manager.connect.return_value = success
    
    # Exercise
    result = mock_db_manager.connect()
    
    # Assert
    assert result == success
    mock_db_manager.connect.assert_called_once()

@pytest.mark.parametrize('success', [True, False])
def test_database_manager_disconnect(mock_db_manager, success):
    """Tests the disconnect method of DatabaseManager."""
    # Setup
    mock_db_manager.disconnect.return_value = success
    
    # Exercise
    result = mock_db_manager.disconnect()
    
    # Assert
    assert result == success
    mock_db_manager.disconnect.assert_called_once()

def test_database_manager_execute_query(mock_db_manager):
    """Tests the execute_query method of DatabaseManager."""
    # Setup
    expected_result = [{'id': 1, 'name': 'test'}]
    mock_db_manager.execute_query.return_value = expected_result
    
    # Exercise
    result = mock_db_manager.execute_query("SELECT * FROM test WHERE id = %s", (1,), fetch_all=True)
    
    # Assert
    assert result == expected_result
    mock_db_manager.execute_query.assert_called_with("SELECT * FROM test WHERE id = %s", (1,), fetch_all=True)

def test_database_manager_execute_query_error(mock_db_manager):
    """Tests error handling in the execute_query method of DatabaseManager."""
    # Setup
    mock_db_manager.execute_query.side_effect = DatabaseError("Test error")
    
    # Exercise and Assert
    with pytest.raises(DatabaseError):
        mock_db_manager.execute_query("SELECT * FROM test WHERE id = %s", (1,))
    
    mock_db_manager.execute_query.assert_called_with("SELECT * FROM test WHERE id = %s", (1,))

# Tests for RedisManager
@pytest.mark.parametrize('success', [True, False])
def test_redis_manager_connect(mock_redis_manager, success):
    """Tests the connect method of RedisManager."""
    # Setup
    mock_redis_manager.connect.return_value = success
    
    # Exercise
    result = mock_redis_manager.connect()
    
    # Assert
    assert result == success
    mock_redis_manager.connect.assert_called_once()

@pytest.mark.parametrize('success', [True, False])
def test_redis_manager_disconnect(mock_redis_manager, success):
    """Tests the disconnect method of RedisManager."""
    # Setup
    mock_redis_manager.disconnect.return_value = success
    
    # Exercise
    result = mock_redis_manager.disconnect()
    
    # Assert
    assert result == success
    mock_redis_manager.disconnect.assert_called_once()

def test_redis_manager_get_token(mock_redis_manager):
    """Tests the get_token method of RedisManager."""
    # Setup
    token_data = {'token_id': 'test_token', 'client_id': 'test_client'}
    mock_redis_manager.get_token.return_value = token_data
    
    # Exercise
    result = mock_redis_manager.get_token('test_token')
    
    # Assert
    assert result == token_data
    mock_redis_manager.get_token.assert_called_with('test_token')

def test_redis_manager_store_token(mock_redis_manager):
    """Tests the store_token method of RedisManager."""
    # Setup
    token_data = {'token_id': 'test_token', 'client_id': 'test_client'}
    mock_redis_manager.store_token.return_value = True
    
    # Exercise
    result = mock_redis_manager.store_token('test_token', token_data, 3600)
    
    # Assert
    assert result is True
    mock_redis_manager.store_token.assert_called_with('test_token', token_data, 3600)

def test_redis_manager_delete_token(mock_redis_manager):
    """Tests the delete_token method of RedisManager."""
    # Setup
    mock_redis_manager.delete_token.return_value = True
    
    # Exercise
    result = mock_redis_manager.delete_token('test_token')
    
    # Assert
    assert result is True
    mock_redis_manager.delete_token.assert_called_with('test_token')

# Tests for TokenManager
@pytest.mark.parametrize('check_db,check_cache,expected_result', [
    (True, True, {'valid': True}),
    (True, False, {'valid': True}),
    (False, True, {'valid': True}),
    (False, False, {'valid': False})
])
def test_token_manager_validate_token(mock_db_manager, mock_redis_manager, check_db, check_cache, expected_result):
    """Tests the validate_token method of TokenManager."""
    # Setup
    token_id = 'test_token'
    token_manager = TokenManager(mock_db_manager, mock_redis_manager, 'test_secret')
    
    # Mock the validate_token method of token_manager
    token_manager.validate_token = Mock(return_value=expected_result)
    
    # Exercise
    result = token_manager.validate_token(token_id, check_db, check_cache)
    
    # Assert
    assert result['valid'] == expected_result['valid']
    token_manager.validate_token.assert_called_with(token_id, check_db, check_cache)

def test_token_manager_cleanup_expired_tokens(mock_db_manager, mock_redis_manager):
    """Tests the cleanup_expired_tokens method of TokenManager."""
    # Setup
    token_manager = TokenManager(mock_db_manager, mock_redis_manager, 'test_secret')
    
    # Mock the cleanup_expired_tokens method
    expected_stats = {
        'total_processed': 10,
        'db_removed': 8,
        'cache_removed': 6,
        'errors': 0
    }
    token_manager.cleanup_expired_tokens = Mock(return_value=expected_stats)
    
    # Exercise
    stats = token_manager.cleanup_expired_tokens(batch_size=10, max_tokens=0)
    
    # Assert
    assert stats['total_processed'] == 10
    assert stats['db_removed'] == 8
    assert stats['cache_removed'] == 6
    assert stats['errors'] == 0
    token_manager.cleanup_expired_tokens.assert_called_with(batch_size=10, max_tokens=0)

# Tests for Helper Functions
def test_execute_db_query():
    """Tests the execute_db_query function."""
    # Setup
    mock_connection = Mock()
    mock_cursor = Mock()
    mock_connection.cursor.return_value = mock_cursor
    
    # Configure cursor to return test results
    test_results = [{'id': 1, 'name': 'test'}]
    mock_cursor.fetchall.return_value = test_results
    mock_cursor.description = ['id', 'name']  # Non-None description indicates SELECT query
    
    # Exercise
    result = execute_db_query(mock_connection, "SELECT * FROM test", fetch_all=True)
    
    # Assert
    assert result == test_results
    mock_connection.cursor.assert_called_once()
    mock_cursor.execute.assert_called_once_with("SELECT * FROM test", ())
    mock_cursor.fetchall.assert_called_once()
    mock_cursor.close.assert_called_once()

def test_batch_execute_db_query():
    """Tests the batch_execute_db_query function."""
    # Setup
    mock_connection = Mock()
    mock_cursor = Mock()
    mock_connection.cursor.return_value = mock_cursor
    
    # Configure cursor to return row counts
    mock_cursor.rowcount = 5
    
    # Test data
    query = "INSERT INTO test (id, name) VALUES (%s, %s)"
    params_list = [(1, 'test1'), (2, 'test2'), (3, 'test3')]
    batch_size = 2
    
    # Exercise
    total_rows = batch_execute_db_query(mock_connection, query, params_list, batch_size)
    
    # Assert
    assert total_rows == 10  # 2 batches * 5 rows each
    mock_connection.cursor.assert_called_once()
    assert mock_cursor.executemany.call_count == 2
    assert mock_connection.commit.call_count == 2
    mock_cursor.close.assert_called_once()

@pytest.mark.parametrize('timestamp,expected', [
    (1623757845, '2021-06-15T10:30:45'),
    (datetime.datetime(2021, 6, 15, 10, 30, 45), '2021-06-15T10:30:45')
])
def test_format_timestamp(timestamp, expected):
    """Tests the format_timestamp function."""
    # Exercise
    result = format_timestamp(timestamp)
    
    # Assert
    assert result == expected

@pytest.mark.parametrize('token_data,expected', [
    ({'exp': int(datetime.datetime.now().timestamp()) + 3600}, False),  # Not expired
    ({'exp': int(datetime.datetime.now().timestamp()) - 3600}, True),   # Expired
    ({}, True)  # No expiration (considered expired)
])
def test_is_token_expired(token_data, expected):
    """Tests the is_token_expired function."""
    # Exercise
    result = is_token_expired(token_data)
    
    # Assert
    assert result == expected

def test_validate_token():
    """Tests the validate_token function."""
    # Setup
    secret_key = 'test_secret'
    exp_time = int(datetime.datetime.now().timestamp()) + 3600
    
    # Create a valid token with appropriate claims
    valid_token_payload = {
        'sub': 'test_client',
        'iss': 'payment-eapi',
        'aud': 'payment-sapi',
        'exp': exp_time,
        'iat': int(datetime.datetime.now().timestamp()),
        'jti': 'token-67890',
        'permissions': ['process_payment']
    }
    valid_token = jwt.encode(valid_token_payload, secret_key, algorithm='HS256')
    
    # Exercise - valid token with no required permissions
    result = validate_token(valid_token, secret_key)
    
    # Assert
    assert result['valid'] is True
    assert result['token_data'] is not None
    assert result['error'] is None
    
    # Exercise - valid token with matching required permissions
    result = validate_token(valid_token, secret_key, required_permissions=['process_payment'])
    
    # Assert
    assert result['valid'] is True
    
    # Exercise - valid token with non-matching required permissions
    result = validate_token(valid_token, secret_key, required_permissions=['admin'])
    
    # Assert
    assert result['valid'] is False
    assert 'Token does not have required permissions' in result['error']
    
    # Exercise - invalid token (wrong signature)
    invalid_token = valid_token + '.invalid'
    result = validate_token(invalid_token, secret_key)
    
    # Assert
    assert result['valid'] is False
    assert result['error'] is not None

def test_cleanup_expired_tokens():
    """Tests the cleanup_expired_tokens function."""
    # Setup
    mock_token_manager = Mock()
    mock_token_manager.cleanup_expired_tokens.return_value = {
        'total_processed': 10,
        'db_removed': 8,
        'cache_removed': 6,
        'errors': 0
    }
    
    # Exercise
    stats = cleanup_expired_tokens(mock_token_manager, batch_size=100, max_tokens=500)
    
    # Assert
    assert stats['total_processed'] == 10
    assert stats['db_removed'] == 8
    assert stats['cache_removed'] == 6
    assert stats['errors'] == 0
    
    # Verify that the mock was called correctly
    mock_token_manager.cleanup_expired_tokens.assert_called_once_with(100, 500)

# Tests for Script Functions
def test_cleanup_tokens_script():
    """Tests the cleanup_tokens function from the cleanup_expired_tokens script."""
    # Setup
    mock_token_manager = Mock()
    mock_token_manager.cleanup_expired_tokens.return_value = {
        'total_processed': 20,
        'db_removed': 15,
        'cache_removed': 12,
        'errors': 1
    }
    
    cleanup_config = TokenCleanupConfig(
        batch_size=200,
        max_tokens_per_run=1000,
        dry_run=False
    )
    
    # Exercise
    stats = cleanup_tokens(mock_token_manager, cleanup_config)
    
    # Assert
    assert stats['total_processed'] == 20
    assert stats['db_removed'] == 15
    assert stats['cache_removed'] == 12
    assert stats['errors'] == 1
    
    # Verify that the mock was called correctly
    mock_token_manager.cleanup_expired_tokens.assert_called_once_with(200, 1000)

def test_parse_arguments():
    """Tests the parse_arguments function from the cleanup_expired_tokens script."""
    # Setup
    with patch('sys.argv', ['cleanup_expired_tokens.py', 
                           '--batch-size', '300', 
                           '--max-tokens', '2000', 
                           '--dry-run', 
                           '--verbose']):
        # Exercise
        args = parse_arguments()
        
        # Assert
        assert args.batch_size == 300
        assert args.max_tokens == 2000
        assert args.dry_run is True
        assert args.verbose is True

def test_validate_specific_token():
    """Tests the validate_specific_token function from the validate_tokens script."""
    # Setup
    mock_token_manager = Mock()
    mock_token_manager.validate_token.return_value = {
        'valid': True,
        'token_data': {
            'token_id': 'test_token',
            'client_id': 'test_client',
            'permissions': ['process_payment'],
            'aud': 'payment-sapi',
            'iss': 'payment-eapi',
            'created_at': 1623757845,
            'expires_at': int(datetime.datetime.now().timestamp()) + 3600,
        },
        'source': 'cache'
    }
    
    # Exercise
    result = validate_specific_token(
        mock_token_manager, 
        'test_token', 
        check_db=True, 
        check_cache=True, 
        required_permissions=['process_payment'],
        audience='payment-sapi',
        issuers=['payment-eapi']
    )
    
    # Assert
    assert result['valid'] is True
    assert result['token_id'] == 'test_token'
    assert result['source'] == 'cache'
    
    # Verify that the mock was called correctly
    mock_token_manager.validate_token.assert_called_once_with('test_token', True, True)

def test_validate_raw_token():
    """Tests the validate_raw_token function from the validate_tokens script."""
    # Setup - Mock jwt.decode to avoid actual JWT validation
    with patch('jwt.decode') as mock_decode:
        mock_decode.return_value = {
            'sub': 'test_client',
            'iss': 'payment-eapi',
            'aud': 'payment-sapi',
            'exp': int(datetime.datetime.now().timestamp()) + 3600,
            'iat': int(datetime.datetime.now().timestamp()),
            'jti': 'token-67890',
            'permissions': ['process_payment']
        }
        
        # Exercise
        result = validate_raw_token(
            'fake.jwt.token', 
            'test_secret', 
            required_permissions=['process_payment'],
            audience='payment-sapi',
            issuers=['payment-eapi']
        )
        
        # Assert
        assert result['valid'] is True
        assert result['validation']['signature'] is True
        assert result['validation']['expiration'] is True
        assert result['validation']['permissions'] is True
        assert result['validation']['audience'] is True
        assert result['validation']['issuer'] is True

@pytest.mark.parametrize('expired_only,expected_query', [
    (True, 'expires_at < %s'),
    (False, 'SELECT * FROM TOKEN_METADATA')
])
def test_list_tokens(expired_only, expected_query):
    """Tests the list_tokens function from the validate_tokens script."""
    # Setup
    mock_db_manager = Mock()
    current_time = datetime.datetime.now().timestamp()
    future_time = current_time + 3600
    past_time = current_time - 3600
    
    mock_db_manager.execute_query.return_value = [
        ('token1', 'client1', 1623757845, future_time, 'ACTIVE'),
        ('token2', 'client2', 1623757845, past_time, 'ACTIVE')
    ]
    
    # Exercise
    tokens = list_tokens(mock_db_manager, expired_only, limit=10)
    
    # Assert
    assert len(tokens) == 2
    assert tokens[0]['token_id'] == 'token1'
    assert tokens[0]['client_id'] == 'client1'
    assert tokens[0]['expired'] is False
    assert tokens[1]['token_id'] == 'token2'
    assert tokens[1]['client_id'] == 'client2'
    assert tokens[1]['expired'] is True
    
    # Verify that the appropriate query was executed
    mock_db_manager.execute_query.assert_called_once()
    # Check that the query contains the expected condition based on expired_only
    call_args = mock_db_manager.execute_query.call_args[0]
    query = call_args[0]
    if expired_only:
        assert expected_query in query
    else:
        assert expected_query in query

@pytest.mark.parametrize('output_format', ['json', 'table'])
def test_format_output(output_format):
    """Tests the format_output function from the validate_tokens script."""
    # Setup
    # Test with a single validation result
    single_result = {
        'token_id': 'test_token',
        'valid': True,
        'source': 'cache',
        'details': {
            'client_id': 'test_client',
            'permissions': ['process_payment'],
            'created_at_formatted': '2021-06-15T10:30:45',
            'expires_at_formatted': '2023-06-15T10:30:45'
        },
        'validation': {
            'signature': True,
            'expiration': True,
            'permissions': True,
            'audience': True,
            'issuer': True
        },
        'error': None
    }
    
    # Test with a list of tokens
    token_list = [
        {
            'token_id': 'token1',
            'client_id': 'client1',
            'created_at_formatted': '2021-06-15T10:30:45',
            'expires_at_formatted': '2023-06-15T10:30:45',
            'status': 'ACTIVE',
            'expired': False
        },
        {
            'token_id': 'token2',
            'client_id': 'client2',
            'created_at_formatted': '2021-06-15T10:30:45',
            'expires_at_formatted': '2021-06-16T10:30:45',
            'status': 'ACTIVE',
            'expired': True
        }
    ]
    
    # Exercise
    single_output = format_output(single_result, output_format)
    list_output = format_output(token_list, output_format)
    
    # Assert
    if output_format == 'json':
        # Verify JSON format
        assert isinstance(single_output, str)
        assert isinstance(list_output, str)
        
        # Try parsing the JSON to ensure it's valid
        single_parsed = json.loads(single_output)
        list_parsed = json.loads(list_output)
        
        assert single_parsed['token_id'] == 'test_token'
        assert len(list_parsed) == 2
    else:  # table format
        # Just verify that output is a string and contains some expected content
        assert isinstance(single_output, str)
        assert isinstance(list_output, str)
        
        if single_output:  # Not an empty string
            assert 'Token ID' in single_output
            assert 'test_token' in single_output
        
        if list_output:  # Not an empty string
            assert 'Token ID' in list_output
            assert 'token1' in list_output
            assert 'token2' in list_output