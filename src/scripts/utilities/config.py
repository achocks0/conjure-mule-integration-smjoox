#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Configuration module for utility scripts in the Payment API Security Enhancement project.

This module provides centralized configuration management, default values, environment variable
handling, and logging setup for database connections, Redis cache, token management, and
credential operations.
"""

import os
import sys
import logging
import json
import yaml  # pyyaml 6.0

# Set up global logger
LOGGER = logging.getLogger('utilities')

# Default configuration values
DEFAULT_DB_HOST = os.getenv('DB_HOST', 'localhost')
DEFAULT_DB_PORT = int(os.getenv('DB_PORT', '5432'))
DEFAULT_DB_NAME = os.getenv('DB_NAME', 'payment')
DEFAULT_DB_USERNAME = os.getenv('DB_USERNAME', 'payment_user')
DEFAULT_DB_PASSWORD = os.getenv('DB_PASSWORD', '')
DEFAULT_REDIS_HOST = os.getenv('REDIS_HOST', 'localhost')
DEFAULT_REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))
DEFAULT_REDIS_PASSWORD = os.getenv('REDIS_PASSWORD', '')
DEFAULT_REDIS_SSL = os.getenv('REDIS_SSL', 'false').lower() == 'true'
DEFAULT_TOKEN_CLEANUP_BATCH_SIZE = int(os.getenv('TOKEN_CLEANUP_BATCH_SIZE', '1000'))
DEFAULT_CONNECTION_TIMEOUT = int(os.getenv('CONNECTION_TIMEOUT', '5'))
DEFAULT_READ_TIMEOUT = int(os.getenv('READ_TIMEOUT', '10'))
DEFAULT_LOG_LEVEL = os.getenv('LOG_LEVEL', 'INFO')
DEFAULT_LOG_FORMAT = os.getenv('LOG_FORMAT', '%(asctime)s - %(name)s - %(levelname)s - %(message)s')
CONFIG_ENV_PREFIX = 'PAYMENT_'


def setup_logging(log_level=None, log_format=None):
    """
    Sets up logging configuration for utility scripts.
    
    Args:
        log_level (str, optional): Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_format (str, optional): Log message format
    
    Returns:
        logging.Logger: Configured logger instance
    """
    # Convert string log level to logging constant if needed
    if isinstance(log_level, str):
        level_map = {
            'DEBUG': logging.DEBUG,
            'INFO': logging.INFO,
            'WARNING': logging.WARNING,
            'ERROR': logging.ERROR,
            'CRITICAL': logging.CRITICAL
        }
        log_level = level_map.get(log_level.upper(), logging.INFO)
    elif log_level is None:
        log_level = logging.INFO
    
    # Use default format if none provided
    if log_format is None:
        log_format = DEFAULT_LOG_FORMAT
    
    # Configure root logger
    logging.basicConfig(level=log_level, format=log_format)
    
    # Configure console handler
    console_handler = logging.StreamHandler()
    console_handler.setLevel(log_level)
    
    # Create formatter
    formatter = logging.Formatter(log_format)
    console_handler.setFormatter(formatter)
    
    # Add handler to the logger
    logging.getLogger().addHandler(console_handler)
    
    return LOGGER


def load_config_from_file(file_path):
    """
    Loads configuration from a JSON or YAML file.
    
    Args:
        file_path (str): Path to the configuration file
    
    Returns:
        dict: Configuration dictionary
    """
    if not os.path.exists(file_path):
        LOGGER.error(f"Configuration file not found: {file_path}")
        return {}
    
    file_ext = os.path.splitext(file_path)[1].lower()
    
    try:
        with open(file_path, 'r') as config_file:
            if file_ext in ('.yaml', '.yml'):
                return yaml.safe_load(config_file)
            elif file_ext == '.json':
                return json.load(config_file)
            else:
                LOGGER.error(f"Unsupported configuration file format: {file_ext}")
                return {}
    except Exception as e:
        LOGGER.error(f"Error loading configuration from {file_path}: {str(e)}")
        return {}


def load_config_from_env(prefix):
    """
    Loads configuration from environment variables.
    
    Args:
        prefix (str): Prefix for environment variables to include in config
    
    Returns:
        dict: Configuration dictionary
    """
    config = {}
    
    for key, value in os.environ.items():
        if key.startswith(prefix):
            # Remove prefix and convert to lowercase for config keys
            config_key = key[len(prefix):].lower()
            config[config_key] = value
    
    return config


def load_config(config_file=None, env_prefix=None):
    """
    Loads configuration from file and/or environment variables.
    
    Args:
        config_file (str, optional): Path to configuration file
        env_prefix (str, optional): Prefix for environment variables
    
    Returns:
        dict: Merged configuration dictionary
    """
    config = {}
    
    # Load from file if provided
    if config_file:
        file_config = load_config_from_file(config_file)
        config.update(file_config)
    
    # Load from environment variables if prefix provided
    if env_prefix:
        env_config = load_config_from_env(env_prefix)
        # Update config with environment variables (environment overrides file)
        config.update(env_config)
    
    return config


def get_database_config(config=None):
    """
    Gets database configuration from config dictionary or defaults.
    
    Args:
        config (dict, optional): Configuration dictionary
    
    Returns:
        dict: Database configuration dictionary
    """
    db_config = {
        'host': DEFAULT_DB_HOST,
        'port': DEFAULT_DB_PORT,
        'dbname': DEFAULT_DB_NAME,
        'username': DEFAULT_DB_USERNAME,
        'password': DEFAULT_DB_PASSWORD,
        'connect_timeout': DEFAULT_CONNECTION_TIMEOUT,
        'read_timeout': DEFAULT_READ_TIMEOUT
    }
    
    if config and 'database' in config:
        db_config.update(config['database'])
    
    return db_config


def get_redis_config(config=None):
    """
    Gets Redis configuration from config dictionary or defaults.
    
    Args:
        config (dict, optional): Configuration dictionary
    
    Returns:
        dict: Redis configuration dictionary
    """
    redis_config = {
        'host': DEFAULT_REDIS_HOST,
        'port': DEFAULT_REDIS_PORT,
        'password': DEFAULT_REDIS_PASSWORD,
        'ssl': DEFAULT_REDIS_SSL,
        'socket_timeout': DEFAULT_READ_TIMEOUT
    }
    
    if config and 'redis' in config:
        redis_config.update(config['redis'])
    
    return redis_config


def get_token_cleanup_config(config=None):
    """
    Gets token cleanup configuration from config dictionary or defaults.
    
    Args:
        config (dict, optional): Configuration dictionary
    
    Returns:
        dict: Token cleanup configuration dictionary
    """
    cleanup_config = {
        'batch_size': DEFAULT_TOKEN_CLEANUP_BATCH_SIZE,
        'max_tokens_per_run': 0,  # 0 means no limit
        'dry_run': False
    }
    
    if config and 'token_cleanup' in config:
        cleanup_config.update(config['token_cleanup'])
    
    return cleanup_config


def get_environment():
    """
    Gets the current environment (development, staging, production).
    
    Returns:
        str: Current environment name
    """
    return os.getenv('ENVIRONMENT', 'development').lower()


def validate_config(config, required_keys=None):
    """
    Validates configuration values.
    
    Args:
        config (dict): Configuration dictionary to validate
        required_keys (list, optional): List of required keys in the configuration
    
    Returns:
        bool: True if configuration is valid, False otherwise
    """
    if config is None:
        LOGGER.error("Configuration is None")
        return False
    
    valid = True
    
    if required_keys:
        for key in required_keys:
            if key not in config:
                LOGGER.error(f"Required configuration key missing: {key}")
                valid = False
    
    return valid


class DatabaseConfig:
    """Configuration class for database connections."""
    
    def __init__(self, host=None, port=None, dbname=None, username=None, 
                 password=None, connect_timeout=None, read_timeout=None):
        """
        Initializes a new DatabaseConfig instance.
        
        Args:
            host (str, optional): Database host
            port (int, optional): Database port
            dbname (str, optional): Database name
            username (str, optional): Database username
            password (str, optional): Database password
            connect_timeout (int, optional): Connection timeout in seconds
            read_timeout (int, optional): Read timeout in seconds
        """
        self.host = host or DEFAULT_DB_HOST
        self.port = port or DEFAULT_DB_PORT
        self.dbname = dbname or DEFAULT_DB_NAME
        self.username = username or DEFAULT_DB_USERNAME
        self.password = password or DEFAULT_DB_PASSWORD
        self.connect_timeout = connect_timeout or DEFAULT_CONNECTION_TIMEOUT
        self.read_timeout = read_timeout or DEFAULT_READ_TIMEOUT
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'host': self.host,
            'port': self.port,
            'dbname': self.dbname,
            'username': self.username,
            'password': self.password,
            'connect_timeout': self.connect_timeout,
            'read_timeout': self.read_timeout
        }
    
    @staticmethod
    def from_dict(config_dict):
        """
        Creates a DatabaseConfig instance from a dictionary.
        
        Args:
            config_dict (dict): Configuration dictionary
        
        Returns:
            DatabaseConfig: DatabaseConfig instance
        """
        return DatabaseConfig(
            host=config_dict.get('host'),
            port=config_dict.get('port'),
            dbname=config_dict.get('dbname'),
            username=config_dict.get('username'),
            password=config_dict.get('password'),
            connect_timeout=config_dict.get('connect_timeout'),
            read_timeout=config_dict.get('read_timeout')
        )
    
    def validate(self):
        """
        Validates the database configuration.
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        valid = True
        
        if not self.host:
            LOGGER.error("Database host is empty")
            valid = False
        
        if not (1 <= self.port <= 65535):
            LOGGER.error(f"Invalid database port: {self.port}")
            valid = False
            
        if not self.dbname:
            LOGGER.error("Database name is empty")
            valid = False
            
        if not self.username:
            LOGGER.error("Database username is empty")
            valid = False
            
        if self.connect_timeout <= 0:
            LOGGER.error(f"Invalid connect timeout: {self.connect_timeout}")
            valid = False
            
        if self.read_timeout <= 0:
            LOGGER.error(f"Invalid read timeout: {self.read_timeout}")
            valid = False
            
        return valid


class RedisConfig:
    """Configuration class for Redis connections."""
    
    def __init__(self, host=None, port=None, password=None, ssl=None, socket_timeout=None):
        """
        Initializes a new RedisConfig instance.
        
        Args:
            host (str, optional): Redis host
            port (int, optional): Redis port
            password (str, optional): Redis password
            ssl (bool, optional): Whether to use SSL for Redis connection
            socket_timeout (int, optional): Socket timeout in seconds
        """
        self.host = host or DEFAULT_REDIS_HOST
        self.port = port or DEFAULT_REDIS_PORT
        self.password = password or DEFAULT_REDIS_PASSWORD
        self.ssl = ssl if ssl is not None else DEFAULT_REDIS_SSL
        self.socket_timeout = socket_timeout or DEFAULT_READ_TIMEOUT
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'host': self.host,
            'port': self.port,
            'password': self.password,
            'ssl': self.ssl,
            'socket_timeout': self.socket_timeout
        }
    
    @staticmethod
    def from_dict(config_dict):
        """
        Creates a RedisConfig instance from a dictionary.
        
        Args:
            config_dict (dict): Configuration dictionary
        
        Returns:
            RedisConfig: RedisConfig instance
        """
        return RedisConfig(
            host=config_dict.get('host'),
            port=config_dict.get('port'),
            password=config_dict.get('password'),
            ssl=config_dict.get('ssl'),
            socket_timeout=config_dict.get('socket_timeout')
        )
    
    def validate(self):
        """
        Validates the Redis configuration.
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        valid = True
        
        if not self.host:
            LOGGER.error("Redis host is empty")
            valid = False
        
        if not (1 <= self.port <= 65535):
            LOGGER.error(f"Invalid Redis port: {self.port}")
            valid = False
            
        if self.socket_timeout <= 0:
            LOGGER.error(f"Invalid socket timeout: {self.socket_timeout}")
            valid = False
            
        return valid


class TokenCleanupConfig:
    """Configuration class for token cleanup operations."""
    
    def __init__(self, batch_size=None, max_tokens_per_run=None, dry_run=None):
        """
        Initializes a new TokenCleanupConfig instance.
        
        Args:
            batch_size (int, optional): Number of tokens to process in each batch
            max_tokens_per_run (int, optional): Maximum number of tokens to process in a single run (0 for no limit)
            dry_run (bool, optional): Whether to run in dry-run mode (no actual changes)
        """
        self.batch_size = batch_size or DEFAULT_TOKEN_CLEANUP_BATCH_SIZE
        self.max_tokens_per_run = max_tokens_per_run if max_tokens_per_run is not None else 0
        self.dry_run = dry_run if dry_run is not None else False
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'batch_size': self.batch_size,
            'max_tokens_per_run': self.max_tokens_per_run,
            'dry_run': self.dry_run
        }
    
    @staticmethod
    def from_dict(config_dict):
        """
        Creates a TokenCleanupConfig instance from a dictionary.
        
        Args:
            config_dict (dict): Configuration dictionary
        
        Returns:
            TokenCleanupConfig: TokenCleanupConfig instance
        """
        return TokenCleanupConfig(
            batch_size=config_dict.get('batch_size'),
            max_tokens_per_run=config_dict.get('max_tokens_per_run'),
            dry_run=config_dict.get('dry_run')
        )
    
    def validate(self):
        """
        Validates the token cleanup configuration.
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        valid = True
        
        if self.batch_size <= 0:
            LOGGER.error(f"Invalid batch size: {self.batch_size}")
            valid = False
            
        if self.max_tokens_per_run < 0:
            LOGGER.error(f"Invalid max tokens per run: {self.max_tokens_per_run}")
            valid = False
            
        return valid


# Initialize logging with default settings
setup_logging(DEFAULT_LOG_LEVEL, DEFAULT_LOG_FORMAT)