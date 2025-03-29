#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Token validation utility for the Payment API Security Enhancement project.

This script provides functionality to validate JWT tokens used in the payment API,
check token expiration, verify signatures, and validate claims such as permissions,
audience, and issuer.
"""

# Internal imports
from config import LOGGER, setup_logging, load_config, get_database_config, get_redis_config, DatabaseConfig, RedisConfig
from utils import DatabaseManager, RedisManager, TokenManager, validate_token, format_timestamp, DatabaseError, RedisError, TokenError

# External imports
import argparse  # standard library
import sys  # standard library
import os  # standard library
import json  # standard library
import datetime  # standard library
from tabulate import tabulate  # version 0.8.9

# Default settings
DEFAULT_CONFIG_FILE = os.getenv('CONFIG_FILE', 'config.json')
DEFAULT_SECRET_KEY = os.getenv('SECRET_KEY', '')

def setup_argument_parser():
    """
    Sets up command-line argument parser for the token validation script.
    
    Returns:
        argparse.ArgumentParser: Configured argument parser
    """
    parser = argparse.ArgumentParser(
        description='Validate JWT tokens for the Payment API Security Enhancement project'
    )
    
    parser.add_argument(
        '--config',
        type=str,
        default=DEFAULT_CONFIG_FILE,
        help=f'Configuration file path (default: {DEFAULT_CONFIG_FILE})'
    )
    
    parser.add_argument(
        '--token-id',
        type=str,
        help='ID of a specific token to validate'
    )
    
    parser.add_argument(
        '--token',
        type=str,
        help='Raw JWT token string to validate'
    )
    
    parser.add_argument(
        '--check-db',
        action='store_true',
        help='Check tokens in database'
    )
    
    parser.add_argument(
        '--check-cache',
        action='store_true',
        help='Check tokens in Redis cache'
    )
    
    parser.add_argument(
        '--list-all',
        action='store_true',
        help='List all tokens in the database'
    )
    
    parser.add_argument(
        '--list-expired',
        action='store_true',
        help='List expired tokens in the database'
    )
    
    parser.add_argument(
        '--secret-key',
        type=str,
        default=DEFAULT_SECRET_KEY,
        help='Secret key for token validation'
    )
    
    parser.add_argument(
        '--permissions',
        type=str,
        help='Comma-separated list of required permissions to validate'
    )
    
    parser.add_argument(
        '--audience',
        type=str,
        help='Expected audience for token validation'
    )
    
    parser.add_argument(
        '--issuer',
        type=str,
        help='Comma-separated list of allowed issuers for token validation'
    )
    
    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Enable verbose output'
    )
    
    parser.add_argument(
        '--format',
        type=str,
        choices=['json', 'table'],
        default='table',
        help='Output format (json or table)'
    )
    
    return parser

def validate_specific_token(token_manager, token_id, check_db=True, check_cache=True, 
                           required_permissions=None, audience=None, issuers=None):
    """
    Validates a specific token by ID from database or Redis cache.
    
    Args:
        token_manager (TokenManager): Token manager instance
        token_id (str): ID of the token to validate
        check_db (bool): Whether to check the database
        check_cache (bool): Whether to check the Redis cache
        required_permissions (list): List of required permissions to validate
        audience (str): Expected audience to validate
        issuers (list): List of allowed issuers to validate
    
    Returns:
        dict: Validation result with token details and validation status
    """
    result = {
        'token_id': token_id,
        'valid': False,
        'source': None,
        'details': {},
        'validation': {
            'signature': False,
            'expiration': False,
            'permissions': None,
            'audience': None,
            'issuer': None
        },
        'error': None
    }
    
    try:
        LOGGER.info(f"Validating token with ID: {token_id}")
        
        # Validate token using token manager
        validation = token_manager.validate_token(token_id, check_db, check_cache)
        
        # Update result with basic validation
        result['valid'] = validation['valid']
        result['source'] = validation.get('source')
        
        if validation['valid'] and validation['token_data']:
            token_data = validation['token_data']
            result['details'] = token_data
            result['validation']['signature'] = True
            result['validation']['expiration'] = True
            
            # Format timestamps for readability
            if 'expires_at' in token_data:
                result['details']['expires_at_formatted'] = format_timestamp(token_data['expires_at'])
            
            if 'created_at' in token_data:
                result['details']['created_at_formatted'] = format_timestamp(token_data['created_at'])
            
            # Additional validations if token data has claims
            # Check permissions
            if required_permissions:
                token_permissions = token_data.get('permissions', [])
                has_permissions = all(perm in token_permissions for perm in required_permissions)
                result['validation']['permissions'] = has_permissions
                if not has_permissions:
                    result['valid'] = False
                    result['error'] = f"Token does not have required permissions: {required_permissions}"
            
            # Check audience
            if audience:
                token_audience = token_data.get('aud')
                audience_valid = token_audience == audience
                result['validation']['audience'] = audience_valid
                if not audience_valid:
                    result['valid'] = False
                    result['error'] = f"Token audience '{token_audience}' does not match expected '{audience}'"
            
            # Check issuer
            if issuers:
                token_issuer = token_data.get('iss')
                issuer_valid = token_issuer in issuers
                result['validation']['issuer'] = issuer_valid
                if not issuer_valid:
                    result['valid'] = False
                    result['error'] = f"Token issuer '{token_issuer}' is not in allowed issuers: {issuers}"
        else:
            result['error'] = validation.get('error', 'Unknown validation error')
        
    except Exception as e:
        LOGGER.error(f"Error validating token {token_id}: {str(e)}")
        result['error'] = str(e)
    
    return result

def validate_raw_token(token, secret_key, required_permissions=None, audience=None, issuers=None):
    """
    Validates a raw JWT token string.
    
    Args:
        token (str): Raw JWT token string to validate
        secret_key (str): Secret key for token validation
        required_permissions (list): List of required permissions to validate
        audience (str): Expected audience to validate
        issuers (list): List of allowed issuers to validate
    
    Returns:
        dict: Validation result with token details and validation status
    """
    result = {
        'token': token[:10] + '...',  # Show only beginning of token for security
        'valid': False,
        'details': {},
        'validation': {
            'signature': False,
            'expiration': False,
            'permissions': None,
            'audience': None,
            'issuer': None
        },
        'error': None
    }
    
    try:
        LOGGER.info("Validating raw JWT token")
        
        # Validate token using utility function
        validation = validate_token(token, secret_key)
        
        # Update result with basic validation
        result['valid'] = validation['valid']
        
        if validation['valid'] and validation['token_data']:
            token_data = validation['token_data']
            result['details'] = token_data
            result['validation']['signature'] = True
            result['validation']['expiration'] = True
            
            # Get token ID if present
            if 'jti' in token_data:
                result['token_id'] = token_data['jti']
            
            # Format timestamps for readability
            if 'exp' in token_data:
                result['details']['expires_at_formatted'] = format_timestamp(token_data['exp'])
            
            if 'iat' in token_data:
                result['details']['created_at_formatted'] = format_timestamp(token_data['iat'])
            
            # Additional validations
            # Check permissions
            if required_permissions:
                token_permissions = token_data.get('permissions', [])
                has_permissions = all(perm in token_permissions for perm in required_permissions)
                result['validation']['permissions'] = has_permissions
                if not has_permissions:
                    result['valid'] = False
                    result['error'] = f"Token does not have required permissions: {required_permissions}"
            
            # Check audience
            if audience:
                token_audience = token_data.get('aud')
                audience_valid = token_audience == audience
                result['validation']['audience'] = audience_valid
                if not audience_valid:
                    result['valid'] = False
                    result['error'] = f"Token audience '{token_audience}' does not match expected '{audience}'"
            
            # Check issuer
            if issuers:
                token_issuer = token_data.get('iss')
                issuer_valid = token_issuer in issuers
                result['validation']['issuer'] = issuer_valid
                if not issuer_valid:
                    result['valid'] = False
                    result['error'] = f"Token issuer '{token_issuer}' is not in allowed issuers: {issuers}"
        else:
            result['error'] = validation.get('error', 'Unknown validation error')
        
    except Exception as e:
        LOGGER.error(f"Error validating raw token: {str(e)}")
        result['error'] = str(e)
    
    return result

def list_tokens(db_manager, expired_only=False, limit=100):
    """
    Lists tokens from database with optional filtering for expired tokens.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        expired_only (bool): Whether to list only expired tokens
        limit (int): Maximum number of tokens to list
    
    Returns:
        list: List of token records
    """
    try:
        LOGGER.info(f"Listing {'expired' if expired_only else 'all'} tokens from database")
        
        current_time = datetime.datetime.now().timestamp()
        
        # Create SQL query based on parameters
        if expired_only:
            query = """
                SELECT token_id, client_id, created_at, expires_at, status
                FROM TOKEN_METADATA
                WHERE status = 'ACTIVE' AND expires_at < %s
                ORDER BY expires_at
                LIMIT %s
            """
            params = (current_time, limit)
        else:
            query = """
                SELECT token_id, client_id, created_at, expires_at, status
                FROM TOKEN_METADATA
                ORDER BY expires_at DESC
                LIMIT %s
            """
            params = (limit,)
        
        # Execute query
        tokens = db_manager.execute_query(query, params, fetch_all=True)
        
        if not tokens:
            return []
        
        # Format token records
        formatted_tokens = []
        for token in tokens:
            token_record = {
                'token_id': token[0],
                'client_id': token[1],
                'created_at': token[2],
                'created_at_formatted': format_timestamp(token[2]),
                'expires_at': token[3],
                'expires_at_formatted': format_timestamp(token[3]),
                'status': token[4],
                'expired': token[3] < current_time
            }
            formatted_tokens.append(token_record)
        
        return formatted_tokens
        
    except DatabaseError as e:
        LOGGER.error(f"Database error when listing tokens: {str(e)}")
        raise
    except Exception as e:
        LOGGER.error(f"Error listing tokens: {str(e)}")
        raise

def format_output(results, output_format):
    """
    Formats validation results for output based on specified format.
    
    Args:
        results (dict or list): Validation results to format
        output_format (str): Output format ('json' or 'table')
    
    Returns:
        str: Formatted output string
    """
    try:
        if output_format == 'json':
            return json.dumps(results, indent=2)
        
        elif output_format == 'table':
            if isinstance(results, dict):
                # Single token result
                if 'token_id' in results or 'token' in results:
                    # Format validation result
                    headers = ['Field', 'Value']
                    rows = [
                        ['Token ID', results.get('token_id', results.get('token', 'N/A'))],
                        ['Valid', 'YES' if results.get('valid') else 'NO'],
                        ['Source', results.get('source', 'N/A')],
                        ['Error', results.get('error', 'None')],
                        ['Signature Valid', 'YES' if results.get('validation', {}).get('signature') else 'NO'],
                        ['Expiration Valid', 'YES' if results.get('validation', {}).get('expiration') else 'NO']
                    ]
                    
                    # Add permissions validation if present
                    perm_validation = results.get('validation', {}).get('permissions')
                    if perm_validation is not None:
                        rows.append(['Permissions Valid', 'YES' if perm_validation else 'NO'])
                    
                    # Add audience validation if present
                    audience_validation = results.get('validation', {}).get('audience')
                    if audience_validation is not None:
                        rows.append(['Audience Valid', 'YES' if audience_validation else 'NO'])
                    
                    # Add issuer validation if present
                    issuer_validation = results.get('validation', {}).get('issuer')
                    if issuer_validation is not None:
                        rows.append(['Issuer Valid', 'YES' if issuer_validation else 'NO'])
                    
                    # Add token details if available
                    if 'details' in results and results['details']:
                        rows.append(['', ''])
                        rows.append(['TOKEN DETAILS', ''])
                        details = results['details']
                        
                        if 'client_id' in details:
                            rows.append(['Client ID', details['client_id']])
                        
                        if 'sub' in details:
                            rows.append(['Subject', details['sub']])
                        
                        if 'iss' in details:
                            rows.append(['Issuer', details['iss']])
                        
                        if 'aud' in details:
                            rows.append(['Audience', details['aud']])
                        
                        if 'created_at_formatted' in details:
                            rows.append(['Created At', details['created_at_formatted']])
                        
                        if 'expires_at_formatted' in details:
                            rows.append(['Expires At', details['expires_at_formatted']])
                        
                        if 'permissions' in details:
                            rows.append(['Permissions', ', '.join(details['permissions'])])
                    
                    return tabulate(rows, headers=headers, tablefmt='grid')
                
                # Some other kind of result
                return json.dumps(results, indent=2)
            
            elif isinstance(results, list):
                # List of tokens
                if not results:
                    return "No tokens found."
                
                headers = ['Token ID', 'Client ID', 'Created At', 'Expires At', 'Status', 'Expired']
                rows = []
                
                for token in results:
                    row = [
                        token.get('token_id', 'N/A'),
                        token.get('client_id', 'N/A'),
                        token.get('created_at_formatted', 'N/A'),
                        token.get('expires_at_formatted', 'N/A'),
                        token.get('status', 'N/A'),
                        'YES' if token.get('expired') else 'NO'
                    ]
                    rows.append(row)
                
                return tabulate(rows, headers=headers, tablefmt='grid')
            
            # Fallback for unknown result type
            return str(results)
        
        else:
            # Unsupported format
            return f"Unsupported output format: {output_format}"
        
    except Exception as e:
        LOGGER.error(f"Error formatting output: {str(e)}")
        return f"Error formatting output: {str(e)}"

def main():
    """
    Main function that orchestrates token validation operations.
    
    Returns:
        int: Exit code (0 for success, non-zero for errors)
    """
    # Set up argument parser and parse arguments
    parser = setup_argument_parser()
    args = parser.parse_args()
    
    # Set up logging
    log_level = 'DEBUG' if args.verbose else 'INFO'
    setup_logging(log_level)
    
    # Load configuration
    config = load_config(args.config, 'PAYMENT_')
    
    # Set up database and Redis managers
    db_config = None
    redis_config = None
    db_manager = None
    redis_manager = None
    token_manager = None
    
    try:
        # Get configurations
        db_config_dict = get_database_config(config)
        redis_config_dict = get_redis_config(config)
        
        db_config = DatabaseConfig.from_dict(db_config_dict)
        redis_config = RedisConfig.from_dict(redis_config_dict)
        
        # Create managers
        db_manager = DatabaseManager(db_config)
        redis_manager = RedisManager(redis_config)
        
        # Connect to database and Redis if needed
        if args.check_db or args.list_all or args.list_expired or (args.token_id and not args.check_cache):
            if not db_manager.connect():
                LOGGER.error("Failed to connect to database")
                return 1
        
        if args.check_cache or (args.token_id and not args.check_db):
            if not redis_manager.connect():
                LOGGER.warning("Failed to connect to Redis cache")
                # Continue without Redis if only checking DB
                if not args.check_db and not args.list_all and not args.list_expired and args.token_id:
                    LOGGER.error("Cannot validate token without database or Redis connection")
                    return 1
        
        # Create token manager
        token_manager = TokenManager(db_manager, redis_manager, args.secret_key)
        
        # Process command-line arguments
        result = None
        
        # Parse permissions if provided
        required_permissions = None
        if args.permissions:
            required_permissions = [p.strip() for p in args.permissions.split(',')]
        
        # Parse issuers if provided
        issuers = None
        if args.issuer:
            issuers = [i.strip() for i in args.issuer.split(',')]
        
        # Validate specific token by ID
        if args.token_id:
            check_db = args.check_db or not args.check_cache
            check_cache = args.check_cache or not args.check_db
            
            result = validate_specific_token(
                token_manager, 
                args.token_id, 
                check_db, 
                check_cache,
                required_permissions,
                args.audience,
                issuers
            )
        
        # Validate raw JWT token
        elif args.token:
            result = validate_raw_token(
                args.token, 
                args.secret_key,
                required_permissions,
                args.audience,
                issuers
            )
        
        # List tokens
        elif args.list_all or args.list_expired:
            result = list_tokens(db_manager, args.list_expired, limit=100)
        
        # No operation specified
        else:
            LOGGER.error("No operation specified. Use --token-id, --token, --list-all, or --list-expired")
            parser.print_help()
            return 1
        
        # Format and print result
        formatted_output = format_output(result, args.format)
        print(formatted_output)
        
        # Return exit code based on validation result
        if isinstance(result, dict) and 'valid' in result:
            return 0 if result['valid'] else 2
        return 0
        
    except Exception as e:
        LOGGER.error(f"Error in token validation: {str(e)}")
        return 1
    
    finally:
        # Disconnect from database and Redis
        if db_manager and db_manager.connected:
            db_manager.disconnect()
        
        if redis_manager and redis_manager.connected:
            redis_manager.disconnect()

if __name__ == '__main__':
    sys.exit(main())