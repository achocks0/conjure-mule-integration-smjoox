#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Utility script for generating test credentials for the Payment API Security Enhancement project.

This script creates test client IDs and client secrets with appropriate complexity
for testing authentication, token generation, and credential rotation processes.
It supports storing generated credentials in Conjur vault and/or local files for test environments.
"""

# Internal imports
from .config import LOGGER, setup_logging, load_config
from .utils import DatabaseManager, DatabaseError
from ..conjur.store_credentials import generate_credential, store_credential_with_retry

# External imports
import argparse
import sys
import os
import json
import random
import string
import uuid

# Global constants
DEFAULT_SECRET_LENGTH = 32
DEFAULT_NUM_CREDENTIALS = 5
DEFAULT_OUTPUT_FILE = 'test_credentials.json'
DEFAULT_PREFIX = 'test-client-'

# Set up module logger
logger = LOGGER

def generate_test_credentials(num_credentials=DEFAULT_NUM_CREDENTIALS, 
                             secret_length=DEFAULT_SECRET_LENGTH,
                             prefix=DEFAULT_PREFIX):
    """
    Generates a specified number of test credentials.
    
    Args:
        num_credentials (int): Number of credentials to generate
        secret_length (int): Length of generated client secrets
        prefix (str): Prefix for client IDs
        
    Returns:
        list: List of generated credential dictionaries
    """
    credentials = []
    
    # Set default values if not provided
    num_credentials = num_credentials or DEFAULT_NUM_CREDENTIALS
    secret_length = secret_length or DEFAULT_SECRET_LENGTH
    prefix = prefix or DEFAULT_PREFIX
    
    for i in range(num_credentials):
        # Generate a unique client_id with prefix and sequential number
        client_id = f"{prefix}{i+1}"
        
        # Call generate_credential from store_credentials module to create secure client_secret
        credential = generate_credential(client_id, secret_length)
        
        # Add generated credential to the list
        credentials.append(credential)
        
        # Log generation (without exposing sensitive data)
        logger.info(f"Generated credential for client_id: {client_id}")
    
    return credentials

def save_credentials_to_file(credentials, output_file=DEFAULT_OUTPUT_FILE):
    """
    Saves generated credentials to a JSON file.
    
    Args:
        credentials (list): List of credential dictionaries
        output_file (str): Path to output file
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Validate input parameters
        if not credentials or not isinstance(credentials, list):
            logger.error("Invalid credentials provided")
            return False
            
        # Create output directory if it doesn't exist
        output_dir = os.path.dirname(output_file)
        if output_dir and not os.path.exists(output_dir):
            os.makedirs(output_dir)
        
        # Write credentials to file
        with open(output_file, 'w') as f:
            json.dump(credentials, f, indent=2)
        
        logger.info(f"Saved {len(credentials)} credentials to {output_file}")
        return True
        
    except FileNotFoundError as e:
        logger.error(f"Directory not found: {str(e)}")
        return False
    except PermissionError as e:
        logger.error(f"Permission denied when writing file: {str(e)}")
        return False
    except Exception as e:
        logger.error(f"Error saving credentials to file: {str(e)}")
        return False

def store_credentials_in_database(credentials, db_manager):
    """
    Stores generated credentials in the database.
    
    Args:
        credentials (list): List of credential dictionaries
        db_manager (DatabaseManager): Database manager instance
        
    Returns:
        dict: Results with success and failure counts
    """
    results = {
        'success': 0,
        'failure': 0
    }
    
    try:
        # Ensure database connection is established
        if not db_manager.connected and not db_manager.connect():
            logger.error("Failed to connect to database")
            return results
        
        # For each credential in the list
        for credential in credentials:
            client_id = credential['client_id']
            client_secret = credential['client_secret']
            
            try:
                # Prepare SQL query to insert credential
                query = """
                    INSERT INTO CLIENT_CREDENTIAL (client_id, metadata, created_at, updated_at, status, version)
                    VALUES (%s, %s, NOW(), NOW(), 'ACTIVE', %s)
                    ON CONFLICT (client_id) 
                    DO UPDATE SET metadata = %s, updated_at = NOW(), status = 'ACTIVE', version = %s
                """
                
                # Create metadata as JSON
                metadata = json.dumps({
                    'test': True,
                    'generated_at': str(uuid.uuid4())
                })
                
                version = str(uuid.uuid4())
                
                # Execute query with credential data
                db_manager.execute_query(query, (
                    client_id, 
                    metadata, 
                    version,
                    metadata,  # For the UPDATE case
                    version    # For the UPDATE case
                ))
                
                # Commit changes
                db_manager.connection.commit()
                
                # Increment success counter
                results['success'] += 1
                logger.info(f"Stored credential in database for client_id: {client_id}")
                
            except Exception as e:
                # Increment failure counter
                results['failure'] += 1
                logger.error(f"Error storing credential in database for client_id {client_id}: {str(e)}")
                db_manager.connection.rollback()
        
        # Log results
        logger.info(f"Database storage complete: {results['success']} succeeded, {results['failure']} failed")
        return results
    
    except DatabaseError as e:
        logger.error(f"Database error: {str(e)}")
        return results

def store_credentials_in_conjur(credentials, conjur_config):
    """
    Stores generated credentials in Conjur vault.
    
    Args:
        credentials (list): List of credential dictionaries
        conjur_config (dict): Conjur configuration
        
    Returns:
        dict: Results with success and failure counts
    """
    results = {
        'success': 0,
        'failure': 0
    }
    
    # For each credential in the list
    for credential in credentials:
        # Extract client_id and client_secret from credential
        client_id = credential['client_id']
        client_secret = credential['client_secret']
        
        try:
            # Call store_credential_with_retry to store in Conjur vault
            success = store_credential_with_retry(client_id, client_secret, conjur_config)
            
            # Increment success or failure counter based on result
            if success:
                results['success'] += 1
                logger.info(f"Stored credential in Conjur vault for client_id: {client_id}")
            else:
                results['failure'] += 1
                logger.error(f"Failed to store credential in Conjur vault for client_id: {client_id}")
        
        except Exception as e:
            # Increment failure counter on exception
            results['failure'] += 1
            logger.error(f"Error storing credential in Conjur vault for client_id {client_id}: {str(e)}")
    
    # Log results
    logger.info(f"Conjur vault storage complete: {results['success']} succeeded, {results['failure']} failed")
    return results

def parse_arguments():
    """
    Parses command line arguments.
    
    Returns:
        argparse.Namespace: Parsed command line arguments
    """
    parser = argparse.ArgumentParser(
        description="Generate test credentials for the Payment API Security Enhancement project"
    )
    
    parser.add_argument(
        "--num-credentials", "-n",
        type=int,
        default=DEFAULT_NUM_CREDENTIALS,
        help=f"Number of credentials to generate (default: {DEFAULT_NUM_CREDENTIALS})"
    )
    
    parser.add_argument(
        "--secret-length", "-l",
        type=int,
        default=DEFAULT_SECRET_LENGTH,
        help=f"Length of generated client secrets (default: {DEFAULT_SECRET_LENGTH})"
    )
    
    parser.add_argument(
        "--output-file", "-o",
        default=DEFAULT_OUTPUT_FILE,
        help=f"Output file path for generated credentials (default: {DEFAULT_OUTPUT_FILE})"
    )
    
    parser.add_argument(
        "--prefix", "-p",
        default=DEFAULT_PREFIX,
        help=f"Prefix for generated client IDs (default: {DEFAULT_PREFIX})"
    )
    
    parser.add_argument(
        "--config", "-c",
        help="Path to configuration file"
    )
    
    parser.add_argument(
        "--store-in-db",
        action="store_true",
        help="Store generated credentials in database"
    )
    
    parser.add_argument(
        "--store-in-conjur",
        action="store_true",
        help="Store generated credentials in Conjur vault"
    )
    
    parser.add_argument(
        "--store-in-file",
        action="store_true",
        help="Store generated credentials in file (enabled by default unless --store-in-db or --store-in-conjur is specified)"
    )
    
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )
    
    return parser.parse_args()

class CredentialGenerator:
    """Class for generating and managing test credentials."""
    
    def __init__(self, secret_length=DEFAULT_SECRET_LENGTH, prefix=DEFAULT_PREFIX):
        """
        Initializes a new CredentialGenerator instance.
        
        Args:
            secret_length (int): Length of generated client secrets
            prefix (str): Prefix for client IDs
        """
        self.secret_length = secret_length or DEFAULT_SECRET_LENGTH
        self.prefix = prefix or DEFAULT_PREFIX
        self.credentials = []
    
    def generate(self, num_credentials=DEFAULT_NUM_CREDENTIALS):
        """
        Generates a specified number of test credentials.
        
        Args:
            num_credentials (int): Number of credentials to generate
            
        Returns:
            list: List of generated credential dictionaries
        """
        self.credentials = generate_test_credentials(
            num_credentials=num_credentials,
            secret_length=self.secret_length,
            prefix=self.prefix
        )
        
        return self.credentials
    
    def save_to_file(self, output_file=DEFAULT_OUTPUT_FILE):
        """
        Saves generated credentials to a file.
        
        Args:
            output_file (str): Path to output file
            
        Returns:
            bool: True if successful, False otherwise
        """
        if not self.credentials:
            logger.error("No credentials generated yet")
            return False
        
        return save_credentials_to_file(self.credentials, output_file)
    
    def store_in_database(self, db_manager):
        """
        Stores generated credentials in database.
        
        Args:
            db_manager (DatabaseManager): Database manager instance
            
        Returns:
            dict: Results with success and failure counts
        """
        if not self.credentials:
            logger.error("No credentials generated yet")
            return {'success': 0, 'failure': 0}
        
        return store_credentials_in_database(self.credentials, db_manager)
    
    def store_in_conjur(self, conjur_config):
        """
        Stores generated credentials in Conjur vault.
        
        Args:
            conjur_config (dict): Conjur configuration
            
        Returns:
            dict: Results with success and failure counts
        """
        if not self.credentials:
            logger.error("No credentials generated yet")
            return {'success': 0, 'failure': 0}
        
        return store_credentials_in_conjur(self.credentials, conjur_config)

def main():
    """
    Main function for CLI usage.
    
    Returns:
        int: Exit code
    """
    # Parse command line arguments
    args = parse_arguments()
    
    # Setup logging with appropriate level based on verbose flag
    log_level = 'DEBUG' if args.verbose else 'INFO'
    setup_logging(log_level)
    
    # Load configuration from file if provided
    config = load_config(args.config)
    
    # Generate test credentials with specified parameters
    credentials = generate_test_credentials(
        num_credentials=args.num_credentials,
        secret_length=args.secret_length,
        prefix=args.prefix
    )
    
    # Determine whether to store in file
    store_in_file = args.store_in_file or (not args.store_in_db and not args.store_in_conjur)
    
    # Initialize results tracking
    results = {
        'file': {'success': False, 'path': args.output_file} if store_in_file else None,
        'database': None,
        'conjur': None
    }
    
    # If store_in_file flag is set, save credentials to output file
    if store_in_file:
        results['file']['success'] = save_credentials_to_file(credentials, args.output_file)
    
    # If store_in_db flag is set, initialize database manager and store credentials
    if args.store_in_db:
        try:
            db_manager = DatabaseManager(config.get('database', {}))
            results['database'] = store_credentials_in_database(credentials, db_manager)
            db_manager.disconnect()
        except Exception as e:
            logger.error(f"Error storing credentials in database: {str(e)}")
            results['database'] = {'success': 0, 'failure': len(credentials)}
    
    # If store_in_conjur flag is set, store credentials in Conjur vault
    if args.store_in_conjur:
        try:
            from ..conjur.config import create_conjur_config
            conjur_config = create_conjur_config(args.config)
            results['conjur'] = store_credentials_in_conjur(credentials, conjur_config)
        except Exception as e:
            logger.error(f"Error storing credentials in Conjur vault: {str(e)}")
            results['conjur'] = {'success': 0, 'failure': len(credentials)}
    
    # Print summary of operations
    logger.info("Credential generation and storage complete")
    logger.info(f"Generated {len(credentials)} test credentials")
    
    if results['file']:
        status = "Success" if results['file']['success'] else "Failed"
        logger.info(f"File storage: {status} - {results['file']['path']}")
    
    if results['database']:
        success = results['database']['success']
        failure = results['database']['failure']
        logger.info(f"Database storage: Success={success}, Failure={failure}")
    
    if results['conjur']:
        success = results['conjur']['success']
        failure = results['conjur']['failure']
        logger.info(f"Conjur vault storage: Success={success}, Failure={failure}")
    
    # Return success exit code if all operations successful, error code otherwise
    success = (
        (results['file'] and results['file']['success']) or
        (results['database'] and results['database']['success'] > 0) or
        (results['conjur'] and results['conjur']['success'] > 0)
    )
    
    return 0 if success else 1

if __name__ == "__main__":
    sys.exit(main())