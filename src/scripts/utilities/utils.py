#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Utility module providing common functionality for the Payment API Security Enhancement project scripts.

This module contains database and Redis connection management, token operations, error handling,
and helper functions for various maintenance and validation tasks.
"""

# Internal imports
from .config import LOGGER, DatabaseConfig, RedisConfig, TokenCleanupConfig

# External imports
import psycopg2  # version 2.9.3
import redis  # version 4.3.4
import jwt  # PyJWT version 2.4.0
import datetime
import time
import logging
import json
import base64

# Set up module logger
logger = logging.getLogger(__name__)

# Custom exceptions
class DatabaseError(Exception):
    """Exception raised for database-related errors."""
    
    def __init__(self, message, original_exception=None):
        """
        Initializes a new DatabaseError instance.
        
        Args:
            message (str): Error message
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message)
        self.message = message
        self.original_exception = original_exception

class RedisError(Exception):
    """Exception raised for Redis-related errors."""
    
    def __init__(self, message, original_exception=None):
        """
        Initializes a new RedisError instance.
        
        Args:
            message (str): Error message
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message)
        self.message = message
        self.original_exception = original_exception

class TokenError(Exception):
    """Exception raised for token-related errors."""
    
    def __init__(self, message, original_exception=None):
        """
        Initializes a new TokenError instance.
        
        Args:
            message (str): Error message
            original_exception (Exception, optional): Original exception that caused this error
        """
        super().__init__(message)
        self.message = message
        self.original_exception = original_exception

# Core utility functions
def execute_db_query(connection, query, params=(), fetch_all=False):
    """
    Executes a database query with parameters and returns the results.
    
    Args:
        connection (psycopg2.connection): Database connection
        query (str): SQL query to execute
        params (tuple, optional): Parameters for the query
        fetch_all (bool, optional): Whether to fetch all results or just one
    
    Returns:
        list or dict or None: Query results or None if no results
    """
    cursor = None
    try:
        cursor = connection.cursor()
        cursor.execute(query, params)
        
        if cursor.description:
            if fetch_all:
                return cursor.fetchall()
            else:
                return cursor.fetchone()
        else:
            # For queries that don't return results (INSERT, UPDATE, DELETE)
            return None
    except psycopg2.Error as e:
        logger.error(f"Database query error: {str(e)}")
        if cursor:
            cursor.close()
        raise DatabaseError(f"Error executing query: {str(e)}", e)
    finally:
        if cursor:
            cursor.close()

def batch_execute_db_query(connection, query, params_list, batch_size=1000):
    """
    Executes a database query in batches for large datasets.
    
    Args:
        connection (psycopg2.connection): Database connection
        query (str): SQL query to execute
        params_list (list): List of parameter tuples for the query
        batch_size (int, optional): Number of parameters to process in each batch
    
    Returns:
        int: Number of rows affected
    """
    total_rows = 0
    cursor = None
    
    try:
        cursor = connection.cursor()
        
        # Process in batches
        for i in range(0, len(params_list), batch_size):
            batch = params_list[i:i+batch_size]
            cursor.executemany(query, batch)
            rows_affected = cursor.rowcount
            total_rows += rows_affected
            connection.commit()
            
            logger.debug(f"Processed batch {i//batch_size + 1}, rows affected: {rows_affected}")
        
        logger.info(f"Batch execution completed. Total rows affected: {total_rows}")
        return total_rows
    
    except psycopg2.Error as e:
        connection.rollback()
        logger.error(f"Batch execution error: {str(e)}")
        raise DatabaseError(f"Error executing batch query: {str(e)}", e)
    
    finally:
        if cursor:
            cursor.close()

def format_timestamp(timestamp):
    """
    Formats a timestamp for display or logging.
    
    Args:
        timestamp (int or float or datetime.datetime): Timestamp to format
    
    Returns:
        str: Formatted timestamp string
    """
    try:
        if isinstance(timestamp, (int, float)):
            # Convert unix timestamp to datetime
            dt = datetime.datetime.fromtimestamp(timestamp)
        elif isinstance(timestamp, datetime.datetime):
            dt = timestamp
        else:
            return str(timestamp)
        
        # Format as ISO 8601
        return dt.isoformat()
    
    except Exception as e:
        logger.warning(f"Error formatting timestamp: {str(e)}")
        return str(timestamp)

def is_token_expired(token_data):
    """
    Checks if a token is expired based on its expiration timestamp.
    
    Args:
        token_data (dict): Token data containing expiration timestamp
    
    Returns:
        bool: True if token is expired, False otherwise
    """
    try:
        exp = token_data.get('exp')
        if not exp:
            logger.warning("Token does not have an expiration timestamp")
            return True
        
        current_time = datetime.datetime.now().timestamp()
        return exp <= current_time
    
    except Exception as e:
        logger.error(f"Error checking token expiration: {str(e)}")
        # If we can't determine expiration, assume expired for safety
        return True

def validate_token(token, secret_key, required_permissions=None):
    """
    Validates a JWT token's signature and claims.
    
    Args:
        token (str): JWT token to validate
        secret_key (str): Secret key used to sign the token
        required_permissions (list, optional): List of permissions the token must have
    
    Returns:
        dict: Validation result with status and token data
    """
    result = {
        'valid': False,
        'token_data': None,
        'error': None
    }
    
    try:
        # Decode and verify token
        token_data = jwt.decode(token, secret_key, algorithms=['HS256'])
        
        # Check expiration
        if 'exp' in token_data and token_data['exp'] < datetime.datetime.now().timestamp():
            result['error'] = 'Token expired'
            return result
        
        # Check permissions if required
        if required_permissions:
            token_permissions = token_data.get('permissions', [])
            if not all(perm in token_permissions for perm in required_permissions):
                result['error'] = 'Token does not have required permissions'
                return result
        
        # Token is valid
        result['valid'] = True
        result['token_data'] = token_data
        
    except jwt.ExpiredSignatureError:
        result['error'] = 'Token expired'
    except jwt.InvalidTokenError as e:
        result['error'] = f'Invalid token: {str(e)}'
    except Exception as e:
        result['error'] = f'Token validation error: {str(e)}'
    
    return result

def cleanup_expired_tokens(token_manager, batch_size=1000, max_tokens=0):
    """
    Cleans up expired tokens from database and Redis cache.
    
    Args:
        token_manager (TokenManager): Token manager instance
        batch_size (int, optional): Number of tokens to process in each batch
        max_tokens (int, optional): Maximum number of tokens to process (0 for no limit)
    
    Returns:
        dict: Cleanup statistics
    """
    stats = {
        'total_processed': 0,
        'db_removed': 0,
        'cache_removed': 0,
        'errors': 0
    }
    
    try:
        # Get current timestamp
        current_time = datetime.datetime.now().timestamp()
        logger.info(f"Starting expired token cleanup at {format_timestamp(current_time)}")
        
        # Call token manager's cleanup method
        cleanup_result = token_manager.cleanup_expired_tokens(batch_size, max_tokens)
        
        # Update stats
        stats.update(cleanup_result)
        
        logger.info(f"Expired token cleanup completed. Stats: {stats}")
        
    except Exception as e:
        logger.error(f"Error during token cleanup: {str(e)}")
        stats['errors'] += 1
    
    return stats

# Resource management classes
class DatabaseManager:
    """Manages database connections and operations."""
    
    def __init__(self, config):
        """
        Initializes a new DatabaseManager instance.
        
        Args:
            config (DatabaseConfig): Database configuration
        """
        self.config = config
        self.connection = None
        self.connected = False
    
    def connect(self):
        """
        Establishes a connection to the database.
        
        Returns:
            bool: True if connection successful, False otherwise
        """
        if self.connected:
            return True
        
        try:
            # Create connection string
            conn_params = {
                'host': self.config.host,
                'port': self.config.port,
                'dbname': self.config.dbname,
                'user': self.config.username,
                'password': self.config.password,
                'connect_timeout': self.config.connect_timeout
            }
            
            self.connection = psycopg2.connect(**conn_params)
            self.connected = True
            logger.info(f"Connected to database {self.config.dbname} on {self.config.host}:{self.config.port}")
            return True
            
        except psycopg2.Error as e:
            logger.error(f"Failed to connect to database: {str(e)}")
            self.connected = False
            return False
    
    def disconnect(self):
        """
        Closes the database connection.
        
        Returns:
            bool: True if disconnection successful, False otherwise
        """
        if not self.connected:
            return True
        
        try:
            self.connection.close()
            self.connected = False
            logger.info("Disconnected from database")
            return True
            
        except psycopg2.Error as e:
            logger.error(f"Error disconnecting from database: {str(e)}")
            return False
    
    def execute_query(self, query, params=(), fetch_all=False):
        """
        Executes a SQL query with parameters.
        
        Args:
            query (str): SQL query to execute
            params (tuple, optional): Parameters for the query
            fetch_all (bool, optional): Whether to fetch all results or just one
        
        Returns:
            list or dict or None: Query results or None if no results
        
        Raises:
            DatabaseError: If there's an error executing the query
        """
        if not self.connected and not self.connect():
            raise DatabaseError("Not connected to database")
        
        try:
            return execute_db_query(self.connection, query, params, fetch_all)
        except Exception as e:
            raise DatabaseError(f"Query execution error: {str(e)}", e)
    
    def batch_execute(self, query, params_list, batch_size=1000):
        """
        Executes a SQL query in batches.
        
        Args:
            query (str): SQL query to execute
            params_list (list): List of parameter tuples for the query
            batch_size (int, optional): Number of parameters to process in each batch
        
        Returns:
            int: Number of rows affected
        
        Raises:
            DatabaseError: If there's an error executing the batch query
        """
        if not self.connected and not self.connect():
            raise DatabaseError("Not connected to database")
        
        try:
            return batch_execute_db_query(self.connection, query, params_list, batch_size)
        except Exception as e:
            raise DatabaseError(f"Batch execution error: {str(e)}", e)
    
    def get_expired_tokens(self, limit=1000):
        """
        Retrieves expired tokens from the database.
        
        Args:
            limit (int, optional): Maximum number of expired tokens to retrieve
        
        Returns:
            list: List of expired token records
        
        Raises:
            DatabaseError: If there's an error retrieving expired tokens
        """
        if not self.connected and not self.connect():
            raise DatabaseError("Not connected to database")
        
        try:
            # Get current timestamp
            current_time = datetime.datetime.now().timestamp()
            
            # SQL query to get expired tokens
            query = """
                SELECT token_id, client_id, expires_at
                FROM TOKEN_METADATA
                WHERE status = 'ACTIVE' AND expires_at < %s
                ORDER BY expires_at
                LIMIT %s
            """
            
            # Execute query
            result = self.execute_query(query, (current_time, limit), fetch_all=True)
            
            if not result:
                return []
            
            return result
        
        except Exception as e:
            raise DatabaseError(f"Error retrieving expired tokens: {str(e)}", e)
    
    def delete_token(self, token_id):
        """
        Deletes a token from the database.
        
        Args:
            token_id (str): ID of the token to delete
        
        Returns:
            bool: True if token deleted, False otherwise
        
        Raises:
            DatabaseError: If there's an error deleting the token
        """
        if not self.connected and not self.connect():
            raise DatabaseError("Not connected to database")
        
        try:
            # SQL query to update token status to 'EXPIRED'
            query = """
                UPDATE TOKEN_METADATA
                SET status = 'EXPIRED'
                WHERE token_id = %s
            """
            
            # Execute query
            self.execute_query(query, (token_id,))
            self.connection.commit()
            
            return True
        
        except Exception as e:
            self.connection.rollback()
            raise DatabaseError(f"Error deleting token: {str(e)}", e)

class RedisManager:
    """Manages Redis connections and operations."""
    
    def __init__(self, config):
        """
        Initializes a new RedisManager instance.
        
        Args:
            config (RedisConfig): Redis configuration
        """
        self.config = config
        self.client = None
        self.connected = False
    
    def connect(self):
        """
        Establishes a connection to Redis.
        
        Returns:
            bool: True if connection successful, False otherwise
        """
        if self.connected:
            return True
        
        try:
            # Create Redis client
            self.client = redis.Redis(
                host=self.config.host,
                port=self.config.port,
                password=self.config.password if self.config.password else None,
                ssl=self.config.ssl,
                socket_timeout=self.config.socket_timeout
            )
            
            # Test connection
            self.client.ping()
            self.connected = True
            logger.info(f"Connected to Redis on {self.config.host}:{self.config.port}")
            return True
            
        except redis.RedisError as e:
            logger.error(f"Failed to connect to Redis: {str(e)}")
            self.connected = False
            return False
    
    def disconnect(self):
        """
        Closes the Redis connection.
        
        Returns:
            bool: True if disconnection successful, False otherwise
        """
        if not self.connected:
            return True
        
        try:
            self.client.close()
            self.connected = False
            logger.info("Disconnected from Redis")
            return True
            
        except redis.RedisError as e:
            logger.error(f"Error disconnecting from Redis: {str(e)}")
            return False
    
    def get_token(self, token_id):
        """
        Retrieves a token from Redis cache.
        
        Args:
            token_id (str): ID of the token to retrieve
        
        Returns:
            dict or None: Token data or None if not found
        
        Raises:
            RedisError: If there's an error retrieving the token
        """
        if not self.connected and not self.connect():
            raise RedisError("Not connected to Redis")
        
        try:
            # Create key for token
            token_key = f"token:{token_id}"
            
            # Get token data
            token_data = self.client.get(token_key)
            
            if not token_data:
                return None
            
            # Deserialize token data
            return json.loads(token_data)
        
        except redis.RedisError as e:
            raise RedisError(f"Error retrieving token: {str(e)}", e)
        except json.JSONDecodeError as e:
            raise RedisError(f"Error deserializing token data: {str(e)}", e)
    
    def store_token(self, token_id, token_data, expiration_seconds=3600):
        """
        Stores a token in Redis cache with expiration.
        
        Args:
            token_id (str): ID of the token
            token_data (dict): Token data to store
            expiration_seconds (int, optional): Token expiration time in seconds
        
        Returns:
            bool: True if token stored, False otherwise
        
        Raises:
            RedisError: If there's an error storing the token
        """
        if not self.connected and not self.connect():
            raise RedisError("Not connected to Redis")
        
        try:
            # Create key for token
            token_key = f"token:{token_id}"
            
            # Serialize token data
            token_json = json.dumps(token_data)
            
            # Store token in Redis with expiration
            self.client.setex(token_key, expiration_seconds, token_json)
            
            return True
        
        except redis.RedisError as e:
            raise RedisError(f"Error storing token: {str(e)}", e)
        except (TypeError, json.JSONDecodeError) as e:
            raise RedisError(f"Error serializing token data: {str(e)}", e)
    
    def delete_token(self, token_id):
        """
        Deletes a token from Redis cache.
        
        Args:
            token_id (str): ID of the token to delete
        
        Returns:
            bool: True if token deleted, False otherwise
        
        Raises:
            RedisError: If there's an error deleting the token
        """
        if not self.connected and not self.connect():
            raise RedisError("Not connected to Redis")
        
        try:
            # Create key for token
            token_key = f"token:{token_id}"
            
            # Delete token
            deleted = self.client.delete(token_key)
            
            return deleted > 0
        
        except redis.RedisError as e:
            raise RedisError(f"Error deleting token: {str(e)}", e)

class TokenManager:
    """Manages token operations across database and Redis cache."""
    
    def __init__(self, db_manager, redis_manager, secret_key):
        """
        Initializes a new TokenManager instance.
        
        Args:
            db_manager (DatabaseManager): Database manager instance
            redis_manager (RedisManager): Redis manager instance
            secret_key (str): Secret key for token validation
        """
        self.db_manager = db_manager
        self.redis_manager = redis_manager
        self.secret_key = secret_key
    
    def validate_token(self, token_id, check_db=True, check_cache=True):
        """
        Validates a token against database and Redis cache.
        
        Args:
            token_id (str): ID of the token to validate
            check_db (bool, optional): Whether to check the database
            check_cache (bool, optional): Whether to check the Redis cache
        
        Returns:
            dict: Validation result with status and token data
        """
        result = {
            'valid': False,
            'token_data': None,
            'source': None,
            'error': None
        }
        
        try:
            # Check Redis cache first if enabled
            if check_cache:
                token_data = self.redis_manager.get_token(token_id)
                
                if token_data:
                    # Check if token is expired
                    if not is_token_expired(token_data):
                        result['valid'] = True
                        result['token_data'] = token_data
                        result['source'] = 'cache'
                        return result
                    else:
                        result['error'] = 'Token expired'
            
            # Check database if enabled and token not found in cache
            if check_db:
                # Query to get token data
                query = """
                    SELECT client_id, created_at, expires_at, status
                    FROM TOKEN_METADATA
                    WHERE token_id = %s
                """
                
                token_record = self.db_manager.execute_query(query, (token_id,))
                
                if token_record:
                    # Check if token is active
                    if token_record[3] == 'ACTIVE':
                        # Check if token is expired
                        if token_record[2] > datetime.datetime.now().timestamp():
                            # Create token data
                            token_data = {
                                'token_id': token_id,
                                'client_id': token_record[0],
                                'created_at': token_record[1],
                                'expires_at': token_record[2],
                                'status': token_record[3]
                            }
                            
                            result['valid'] = True
                            result['token_data'] = token_data
                            result['source'] = 'database'
                            
                            # Store in cache for future use if not already there
                            if check_cache:
                                expiration = int(token_data['expires_at'] - datetime.datetime.now().timestamp())
                                if expiration > 0:
                                    self.redis_manager.store_token(token_id, token_data, expiration)
                            
                            return result
                        else:
                            result['error'] = 'Token expired'
                    else:
                        result['error'] = f"Token status is {token_record[3]}"
                else:
                    result['error'] = 'Token not found'
            
            # If we get here, token was not found or not valid
            if not result['error']:
                result['error'] = 'Token not found'
                
            return result
            
        except Exception as e:
            logger.error(f"Error validating token: {str(e)}")
            result['error'] = f"Validation error: {str(e)}"
            return result
    
    def cleanup_expired_tokens(self, batch_size=1000, max_tokens=0):
        """
        Cleans up expired tokens from both database and Redis.
        
        Args:
            batch_size (int, optional): Number of tokens to process in each batch
            max_tokens (int, optional): Maximum number of tokens to process (0 for no limit)
        
        Returns:
            dict: Cleanup statistics
        """
        stats = {
            'total_processed': 0,
            'db_removed': 0,
            'cache_removed': 0,
            'errors': 0
        }
        
        try:
            # Get expired tokens from database
            expired_tokens = self.db_manager.get_expired_tokens(max_tokens if max_tokens > 0 else 10000)
            
            if not expired_tokens:
                logger.info("No expired tokens found")
                return stats
            
            logger.info(f"Found {len(expired_tokens)} expired tokens")
            
            # Process tokens in batches
            for i in range(0, len(expired_tokens), batch_size):
                batch = expired_tokens[i:i+batch_size]
                
                for token_record in batch:
                    token_id = token_record[0]
                    
                    try:
                        # Delete from database
                        db_deleted = self.db_manager.delete_token(token_id)
                        
                        # Delete from Redis cache
                        cache_deleted = self.redis_manager.delete_token(token_id)
                        
                        stats['total_processed'] += 1
                        if db_deleted:
                            stats['db_removed'] += 1
                        if cache_deleted:
                            stats['cache_removed'] += 1
                        
                    except Exception as e:
                        logger.error(f"Error cleaning up token {token_id}: {str(e)}")
                        stats['errors'] += 1
                
                # Commit database changes after each batch
                self.db_manager.connection.commit()
                
                logger.info(f"Processed batch {i//batch_size + 1}, "
                           f"total processed: {stats['total_processed']}, "
                           f"db removed: {stats['db_removed']}, "
                           f"cache removed: {stats['cache_removed']}")
                
                # Check if we've reached the maximum tokens to process
                if max_tokens > 0 and stats['total_processed'] >= max_tokens:
                    logger.info(f"Reached maximum tokens to process: {max_tokens}")
                    break
            
            return stats
            
        except Exception as e:
            logger.error(f"Error during token cleanup: {str(e)}")
            self.db_manager.connection.rollback()
            stats['errors'] += 1
            return stats