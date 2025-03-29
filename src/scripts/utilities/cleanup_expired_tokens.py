#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Token Cleanup Utility Script

This script cleans up expired authentication tokens from both the database and Redis cache.
It's designed to be run as a scheduled task to maintain system performance and security
by removing tokens that are no longer valid.
"""

import argparse
import sys
import os
import datetime
import time

# Import configuration and utilities
from config import (
    LOGGER, 
    setup_logging, 
    load_config, 
    get_database_config, 
    get_redis_config, 
    get_token_cleanup_config,
    DatabaseConfig,
    RedisConfig,
    TokenCleanupConfig
)
from utils import (
    DatabaseManager, 
    RedisManager, 
    TokenManager,
    DatabaseError,
    RedisError,
    format_timestamp
)

# Set up logger
logger = LOGGER

def parse_arguments():
    """
    Parses command-line arguments for the script.

    Returns:
        argparse.Namespace: Parsed command-line arguments
    """
    parser = argparse.ArgumentParser(
        description="Clean up expired authentication tokens from database and Redis cache"
    )
    
    parser.add_argument(
        "--config", 
        help="Path to configuration file", 
        default=os.environ.get("CONFIG_FILE")
    )
    
    parser.add_argument(
        "--batch-size", 
        type=int, 
        help="Number of tokens to process in each batch", 
        default=None
    )
    
    parser.add_argument(
        "--max-tokens", 
        type=int, 
        help="Maximum number of tokens to process (0 for no limit)", 
        default=None
    )
    
    parser.add_argument(
        "--dry-run", 
        action="store_true", 
        help="Run without making any changes (dry run)"
    )
    
    parser.add_argument(
        "--verbose", 
        action="store_true", 
        help="Enable verbose logging"
    )
    
    return parser.parse_args()

def cleanup_tokens(token_manager, cleanup_config):
    """
    Cleans up expired tokens from database and Redis cache.
    
    Args:
        token_manager (TokenManager): token manager instance
        cleanup_config (TokenCleanupConfig): token cleanup configuration
        
    Returns:
        dict: Cleanup statistics
    """
    logger.info("Starting expired token cleanup process")
    
    batch_size = cleanup_config.batch_size
    max_tokens = cleanup_config.max_tokens_per_run
    dry_run = cleanup_config.dry_run
    
    if dry_run:
        logger.info("DRY RUN MODE - No changes will be made")
    
    # Start timing the operation
    start_time = time.time()
    
    # Call the cleanup function from the token manager
    stats = token_manager.cleanup_expired_tokens(batch_size, max_tokens)
    
    # Calculate execution time
    execution_time = time.time() - start_time
    
    # Log cleanup statistics
    logger.info(f"Cleanup completed in {execution_time:.2f} seconds")
    logger.info(f"Tokens processed: {stats['total_processed']}")
    logger.info(f"Tokens removed from database: {stats['db_removed']}")
    logger.info(f"Tokens removed from cache: {stats['cache_removed']}")
    logger.info(f"Errors encountered: {stats['errors']}")
    
    return stats

def main():
    """
    Main entry point for the script.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    try:
        # Parse command-line arguments
        args = parse_arguments()
        
        # Set up logging with appropriate verbosity
        log_level = "DEBUG" if args.verbose else "INFO"
        setup_logging(log_level)
        
        logger.info("Token cleanup script started")
        
        # Load configuration
        config_file = args.config
        if config_file:
            logger.info(f"Loading configuration from {config_file}")
            config = load_config(config_file)
        else:
            logger.info("Using default configuration and environment variables")
            config = load_config()
        
        # Get configuration components
        db_config_dict = get_database_config(config)
        redis_config_dict = get_redis_config(config)
        cleanup_config_dict = get_token_cleanup_config(config)
        
        # Create configuration objects
        db_config = DatabaseConfig.from_dict(db_config_dict)
        redis_config = RedisConfig.from_dict(redis_config_dict)
        cleanup_config = TokenCleanupConfig.from_dict(cleanup_config_dict)
        
        # Override with command-line arguments if provided
        if args.batch_size is not None:
            cleanup_config.batch_size = args.batch_size
            logger.info(f"Using batch size from command line: {cleanup_config.batch_size}")
            
        if args.max_tokens is not None:
            cleanup_config.max_tokens_per_run = args.max_tokens
            logger.info(f"Using max tokens from command line: {cleanup_config.max_tokens_per_run}")
            
        if args.dry_run:
            cleanup_config.dry_run = True
            logger.info("Using dry run mode from command line")
        
        # Initialize managers
        db_manager = DatabaseManager(db_config)
        redis_manager = RedisManager(redis_config)
        
        # Get the secret key for token validation from config or env
        secret_key = config.get('security', {}).get('token_signing_key', 
                                                   os.environ.get('TOKEN_SIGNING_KEY', ''))
        
        token_manager = TokenManager(db_manager, redis_manager, secret_key)
        
        # Connect to database and Redis
        if not db_manager.connect():
            logger.error("Failed to connect to database")
            return 1
            
        if not redis_manager.connect():
            logger.error("Failed to connect to Redis")
            db_manager.disconnect()
            return 1
        
        try:
            # Clean up expired tokens
            stats = cleanup_tokens(token_manager, cleanup_config)
            
            # Log summary
            logger.info("Token cleanup completed successfully")
            
            return 0
            
        finally:
            # Disconnect from database and Redis
            db_manager.disconnect()
            redis_manager.disconnect()
            
    except Exception as e:
        logger.error(f"An error occurred: {str(e)}", exc_info=True)
        return 1

if __name__ == '__main__':
    sys.exit(main())