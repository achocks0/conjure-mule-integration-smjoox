#!/usr/bin/env python3
"""
Script for restoring credential and token metadata from backups for the
Payment API Security Enhancement project. This script supports disaster recovery,
environment synchronization, and credential rotation processes by restoring
critical metadata from previously created backups.
"""

import os
import sys
import argparse
import json
import datetime
import shutil
import psycopg2
import tarfile
import glob
from config import LOGGER, BACKUP_DIR, get_environment_config
from utils import run_command, send_notification, validate_environment

# Constants
MANIFEST_FILENAME = 'backup_manifest.json'
DEFAULT_TABLES = ['client_credential', 'token_metadata', 'authentication_event', 'credential_rotation']

def parse_arguments(args=None):
    """
    Parses command-line arguments for the restore script
    
    Args:
        args (list): Command-line arguments
        
    Returns:
        argparse.Namespace: Parsed command-line arguments
    """
    parser = argparse.ArgumentParser(description='Restore credential and token metadata from backup')
    
    parser.add_argument('backup_path', help='Path to the backup file or directory')
    parser.add_argument('target_environment', choices=['development', 'test', 'staging', 'production'], 
                        help='Target environment to restore to')
    parser.add_argument('--tables', nargs='+', default=DEFAULT_TABLES,
                        help=f'Tables to restore (default: {DEFAULT_TABLES})')
    parser.add_argument('--dry-run', action='store_true', 
                        help='Simulate restore without making changes')
    parser.add_argument('--force', action='store_true',
                        help='Force restore without confirmation')
    parser.add_argument('--notify', action='store_true',
                        help='Send notifications about the restore operation')
    
    return parser.parse_args(args)

def get_db_connection(db_config):
    """
    Establishes a connection to the database
    
    Args:
        db_config (dict): Database configuration parameters
        
    Returns:
        psycopg2.connection: Database connection object
    """
    try:
        LOGGER.info(f"Connecting to database: {db_config.get('host')}:{db_config.get('port')}/{db_config.get('database')}")
        
        conn = psycopg2.connect(
            host=db_config.get('host'),
            port=db_config.get('port'),
            database=db_config.get('database'),
            user=db_config.get('user'),
            password=db_config.get('password')
        )
        LOGGER.info("Database connection established")
        return conn
    except Exception as e:
        LOGGER.error(f"Error connecting to database: {str(e)}")
        raise

def extract_backup_if_needed(backup_path):
    """
    Extracts backup archive if the backup path is a compressed file
    
    Args:
        backup_path (str): Path to the backup file or directory
        
    Returns:
        str: Path to the extracted backup directory or original path if not an archive
    """
    # Check if backup_path is a file with .tar.gz extension
    if os.path.isfile(backup_path) and backup_path.endswith('.tar.gz'):
        LOGGER.info(f"Extracting backup archive: {backup_path}")
        
        try:
            # Create a temporary directory for extraction
            temp_dir = os.path.join(os.path.dirname(backup_path), 'temp_extract')
            if os.path.exists(temp_dir):
                shutil.rmtree(temp_dir)
            os.makedirs(temp_dir)
            
            # Extract the archive
            with tarfile.open(backup_path, 'r:gz') as tar:
                tar.extractall(path=temp_dir)
            
            LOGGER.info(f"Backup extracted to: {temp_dir}")
            return temp_dir
        except Exception as e:
            LOGGER.error(f"Error extracting backup archive: {str(e)}")
            raise
    
    # If not an archive, return the original path
    return backup_path

def read_backup_manifest(backup_dir):
    """
    Reads the backup manifest file to get metadata about the backup
    
    Args:
        backup_dir (str): Path to the backup directory
        
    Returns:
        dict: Backup manifest data
    """
    manifest_path = os.path.join(backup_dir, MANIFEST_FILENAME)
    
    try:
        if not os.path.exists(manifest_path):
            LOGGER.error(f"Backup manifest file not found: {manifest_path}")
            raise FileNotFoundError(f"Backup manifest file not found: {manifest_path}")
        
        with open(manifest_path, 'r') as f:
            manifest = json.load(f)
        
        LOGGER.info(f"Read backup manifest: {manifest_path}")
        return manifest
    except json.JSONDecodeError as e:
        LOGGER.error(f"Error parsing backup manifest: {str(e)}")
        raise
    except Exception as e:
        LOGGER.error(f"Error reading backup manifest: {str(e)}")
        raise

def restore_table_from_json(conn, table_name, backup_file, dry_run=False):
    """
    Restores a database table from a JSON backup file
    
    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to restore
        backup_file (str): Path to the backup file
        dry_run (bool): If True, only simulate the restore without making changes
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        if not os.path.exists(backup_file):
            LOGGER.error(f"Backup file does not exist: {backup_file}")
            return False
        
        with open(backup_file, 'r') as f:
            records = json.load(f)
        
        LOGGER.info(f"Loaded {len(records)} records from {backup_file}")
        
        cursor = conn.cursor()
        
        if not dry_run:
            LOGGER.info(f"Truncating table: {table_name}")
            cursor.execute(f"TRUNCATE TABLE {table_name} RESTART IDENTITY CASCADE")
            
            for record in records:
                columns = record.keys()
                values = [record[col] for col in columns]
                
                column_str = ', '.join(columns)
                placeholder_str = ', '.join(['%s'] * len(columns))
                
                insert_query = f"INSERT INTO {table_name} ({column_str}) VALUES ({placeholder_str})"
                cursor.execute(insert_query, values)
            
            conn.commit()
            LOGGER.info(f"Restored {len(records)} records to table: {table_name}")
        else:
            LOGGER.info(f"Dry run: would restore {len(records)} records to table: {table_name}")
        
        return True
    except Exception as e:
        if not dry_run:
            conn.rollback()
        LOGGER.error(f"Error restoring table {table_name} from JSON: {str(e)}")
        return False

def restore_table_from_sql(conn, table_name, backup_file, dry_run=False):
    """
    Restores a database table from a SQL backup file
    
    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to restore
        backup_file (str): Path to the backup file
        dry_run (bool): If True, only simulate the restore without making changes
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        if not os.path.exists(backup_file):
            LOGGER.error(f"Backup file does not exist: {backup_file}")
            return False
        
        with open(backup_file, 'r') as f:
            sql_statements = f.read()
        
        cursor = conn.cursor()
        
        if not dry_run:
            LOGGER.info(f"Truncating table: {table_name}")
            cursor.execute(f"TRUNCATE TABLE {table_name} RESTART IDENTITY CASCADE")
            
            # Execute SQL statements
            cursor.execute(sql_statements)
            
            conn.commit()
            LOGGER.info(f"Restored table from SQL: {table_name}")
        else:
            LOGGER.info(f"Dry run: would restore table from SQL: {table_name}")
        
        return True
    except Exception as e:
        if not dry_run:
            conn.rollback()
        LOGGER.error(f"Error restoring table {table_name} from SQL: {str(e)}")
        return False

def restore_table_from_csv(conn, table_name, backup_file, dry_run=False):
    """
    Restores a database table from a CSV backup file
    
    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to restore
        backup_file (str): Path to the backup file
        dry_run (bool): If True, only simulate the restore without making changes
        
    Returns:
        bool: True if successful, False otherwise
    """
    try:
        if not os.path.exists(backup_file):
            LOGGER.error(f"Backup file does not exist: {backup_file}")
            return False
        
        with open(backup_file, 'r') as f:
            lines = f.readlines()
        
        if not lines:
            LOGGER.error(f"CSV file is empty: {backup_file}")
            return False
        
        # Parse header row to get column names
        header = lines[0].strip().split(',')
        data_rows = lines[1:]
        
        LOGGER.info(f"Loaded {len(data_rows)} records from {backup_file}")
        
        cursor = conn.cursor()
        
        if not dry_run:
            LOGGER.info(f"Truncating table: {table_name}")
            cursor.execute(f"TRUNCATE TABLE {table_name} RESTART IDENTITY CASCADE")
            
            for row in data_rows:
                values = row.strip().split(',')
                
                # Ensure we have the right number of values
                if len(values) != len(header):
                    LOGGER.warning(f"Skipping row with incorrect number of values: {row}")
                    continue
                
                column_str = ', '.join(header)
                placeholder_str = ', '.join(['%s'] * len(header))
                
                insert_query = f"INSERT INTO {table_name} ({column_str}) VALUES ({placeholder_str})"
                cursor.execute(insert_query, values)
            
            conn.commit()
            LOGGER.info(f"Restored {len(data_rows)} records to table: {table_name}")
        else:
            LOGGER.info(f"Dry run: would restore {len(data_rows)} records to table: {table_name}")
        
        return True
    except Exception as e:
        if not dry_run:
            conn.rollback()
        LOGGER.error(f"Error restoring table {table_name} from CSV: {str(e)}")
        return False

def confirm_restore(manifest, target_environment, tables, force=False):
    """
    Asks for user confirmation before proceeding with restore
    
    Args:
        manifest (dict): Backup manifest data
        target_environment (str): Target environment for restore
        tables (list): List of tables to restore
        force (bool): If True, skip confirmation
        
    Returns:
        bool: True if user confirms or force is True, False otherwise
    """
    if force:
        return True
    
    print("\n" + "=" * 80)
    print(f"WARNING: This will overwrite data in the {target_environment} environment!")
    print("=" * 80)
    print("\nBackup Information:")
    print(f"  Source Environment: {manifest.get('environment', 'unknown')}")
    print(f"  Backup Date: {manifest.get('timestamp', 'unknown')}")
    print(f"  Tables in Backup: {', '.join(manifest.get('tables', []))}")
    print("\nRestore Operation:")
    print(f"  Target Environment: {target_environment}")
    print(f"  Tables to Restore: {', '.join(tables)}")
    print("\n" + "=" * 80)
    
    confirmation = input("\nDo you want to proceed with the restore? (y/n): ")
    return confirmation.lower() == 'y'

def restore_metadata(backup_path, target_environment, tables=None, dry_run=False, force=False, notify=False):
    """
    Main function to restore database metadata from backup
    
    Args:
        backup_path (str): Path to the backup file or directory
        target_environment (str): Target environment to restore to
        tables (list): List of tables to restore
        dry_run (bool): If True, only simulate the restore without making changes
        force (bool): If True, skip confirmation prompts
        notify (bool): If True, send notifications about the restore operation
        
    Returns:
        bool: True if restore is successful, False otherwise
    """
    # Use default tables if not specified
    if tables is None:
        tables = DEFAULT_TABLES
    
    # Validate target environment
    if not validate_environment(target_environment):
        return False
    
    try:
        # Extract backup if it's a compressed archive
        extracted_path = extract_backup_if_needed(backup_path)
        
        # Read backup manifest
        manifest = read_backup_manifest(extracted_path)
        
        # Get backup format
        backup_format = manifest.get('format', 'json')
        LOGGER.info(f"Backup format: {backup_format}")
        
        # Confirm restore if not dry run
        if not dry_run:
            if not confirm_restore(manifest, target_environment, tables, force):
                LOGGER.info("Restore operation cancelled by user")
                return False
        
        # Get environment configuration
        env_config = get_environment_config(target_environment)
        
        # Get database connection
        db_config = env_config.get('database', {})
        conn = get_db_connection(db_config)
        
        # Track success of restore operations
        success = True
        
        # Restore each table
        for table in tables:
            backup_file = None
            
            # Determine backup file path based on format
            if backup_format == 'json':
                backup_file = os.path.join(extracted_path, f"{table}.json")
                restore_func = restore_table_from_json
            elif backup_format == 'sql':
                backup_file = os.path.join(extracted_path, f"{table}.sql")
                restore_func = restore_table_from_sql
            elif backup_format == 'csv':
                backup_file = os.path.join(extracted_path, f"{table}.csv")
                restore_func = restore_table_from_csv
            else:
                LOGGER.error(f"Unsupported backup format: {backup_format}")
                success = False
                continue
            
            # Restore table
            table_success = restore_func(conn, table, backup_file, dry_run)
            if not table_success:
                success = False
        
        # Close database connection
        conn.close()
        
        # Clean up extracted backup if it's a temporary directory
        if extracted_path != backup_path and os.path.exists(extracted_path):
            LOGGER.info(f"Cleaning up temporary directory: {extracted_path}")
            shutil.rmtree(extracted_path)
        
        # Send notification if enabled
        if notify:
            operation = "Dry run of restore" if dry_run else "Restore"
            message = f"{operation} {'completed successfully' if success else 'failed'} for {target_environment}"
            notification_data = {
                "environment": target_environment,
                "tables": ", ".join(tables),
                "source_backup": backup_path,
                "timestamp": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }
            send_notification(message, "info" if success else "error", env_config.get('notification_channels'), notification_data)
        
        # Log completion status
        if dry_run:
            LOGGER.info(f"Dry run of restore {'completed successfully' if success else 'failed'} for {target_environment}")
        else:
            LOGGER.info(f"Restore {'completed successfully' if success else 'failed'} for {target_environment}")
        
        return success
    
    except Exception as e:
        LOGGER.error(f"Error during restore operation: {str(e)}")
        return False

def main():
    """
    Main entry point for the script
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    try:
        # Parse command-line arguments
        args = parse_arguments()
        
        # Execute restore operation
        success = restore_metadata(
            args.backup_path,
            args.target_environment,
            args.tables,
            args.dry_run,
            args.force,
            args.notify
        )
        
        return 0 if success else 1
    
    except Exception as e:
        LOGGER.error(f"Error: {str(e)}")
        return 1

if __name__ == "__main__":
    sys.exit(main())