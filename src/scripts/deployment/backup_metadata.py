#!/usr/bin/env python3
"""
Backup Metadata Script for Payment API Security Enhancement Project

This script backs up credential and token metadata from the PostgreSQL database.
It supports disaster recovery, environment synchronization, and credential rotation processes
by creating backups of critical metadata.
"""

import os
import sys
import argparse
import json
import datetime
import shutil
import psycopg2
import tarfile
from config import LOGGER, BACKUP_DIR, get_environment_config
from utils import run_command, send_notification, validate_environment

# Default tables to backup
DEFAULT_TABLES = ['credentials', 'tokens', 'authentication_events', 'credential_rotation_history']

# Default output format
DEFAULT_FORMAT = 'json'

# Manifest filename
MANIFEST_FILENAME = 'backup_manifest.json'

# Default backup retention period in days
BACKUP_RETENTION_DAYS = int(os.environ.get('BACKUP_RETENTION_DAYS', '30'))


def parse_arguments():
    """
    Parses command-line arguments for the backup script

    Returns:
        argparse.Namespace: Parsed command-line arguments
    """
    parser = argparse.ArgumentParser(
        description='Backup credential and token metadata from the database'
    )
    
    parser.add_argument(
        'environment',
        choices=['development', 'test', 'staging', 'production'],
        help='Target environment (development, test, staging, production)'
    )
    
    parser.add_argument(
        '--output-dir',
        dest='output_dir',
        default=BACKUP_DIR,
        help=f'Output directory for backups (default: {BACKUP_DIR})'
    )
    
    parser.add_argument(
        '--tables',
        nargs='+',
        default=DEFAULT_TABLES,
        help=f'Tables to backup (default: {" ".join(DEFAULT_TABLES)})'
    )
    
    parser.add_argument(
        '--format',
        choices=['json', 'sql', 'csv'],
        default=DEFAULT_FORMAT,
        help=f'Output format (default: {DEFAULT_FORMAT})'
    )
    
    parser.add_argument(
        '--compress',
        action='store_true',
        default=True,
        help='Compress backup files (default: True)'
    )
    
    parser.add_argument(
        '--no-compress',
        dest='compress',
        action='store_false',
        help='Do not compress backup files'
    )
    
    parser.add_argument(
        '--notify',
        action='store_true',
        help='Send notification about backup completion'
    )
    
    return parser.parse_args()


def get_db_connection(db_config):
    """
    Establishes a connection to the database

    Args:
        db_config (dict): Database configuration parameters

    Returns:
        psycopg2.connection: Database connection object
    """
    try:
        # Extract connection parameters
        host = db_config.get('host', 'localhost')
        port = db_config.get('port', 5432)
        dbname = db_config.get('dbname', 'payment')
        user = db_config.get('user', 'postgres')
        password = db_config.get('password', '')
        
        # Establish connection
        conn = psycopg2.connect(
            host=host,
            port=port,
            dbname=dbname,
            user=user,
            password=password
        )
        
        LOGGER.info(f"Connected to database {dbname} on {host}:{port}")
        return conn
    except psycopg2.Error as e:
        LOGGER.error(f"Database connection error: {str(e)}")
        raise


def create_backup_directory(output_dir, environment):
    """
    Creates a backup directory with timestamp

    Args:
        output_dir (str): Base output directory
        environment (str): Target environment

    Returns:
        str: Path to the created backup directory
    """
    try:
        # Generate timestamp for directory name
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        
        # Create directory name with environment and timestamp
        dir_name = f"{environment}_backup_{timestamp}"
        
        # Full path to the backup directory
        backup_dir = os.path.join(output_dir, dir_name)
        
        # Create directory if it doesn't exist
        os.makedirs(backup_dir, exist_ok=True)
        
        LOGGER.info(f"Created backup directory: {backup_dir}")
        return backup_dir
    except OSError as e:
        LOGGER.error(f"Error creating backup directory: {str(e)}")
        raise


def backup_table_to_json(conn, table_name, output_file):
    """
    Backs up a database table to a JSON file

    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to backup
        output_file (str): Path to the output file

    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Create cursor
        cursor = conn.cursor()
        
        # Execute query to fetch all rows
        cursor.execute(f"SELECT * FROM {table_name}")
        
        # Get column names from cursor description
        columns = [desc[0] for desc in cursor.description]
        
        # Fetch all rows
        rows = cursor.fetchall()
        
        # Create list of dictionaries (records)
        records = []
        for row in rows:
            record = {}
            for i, column in enumerate(columns):
                record[column] = row[i]
            records.append(record)
        
        # Write records to JSON file
        with open(output_file, 'w') as f:
            json.dump(records, f, indent=2, default=str)
        
        LOGGER.info(f"Backed up {len(records)} records from {table_name} to {output_file}")
        return True
    except (psycopg2.Error, IOError) as e:
        LOGGER.error(f"Error backing up table {table_name} to JSON: {str(e)}")
        return False


def backup_table_to_sql(conn, table_name, output_file):
    """
    Backs up a database table to a SQL file with INSERT statements

    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to backup
        output_file (str): Path to the output file

    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Create cursor
        cursor = conn.cursor()
        
        # Execute query to fetch all rows
        cursor.execute(f"SELECT * FROM {table_name}")
        
        # Get column names from cursor description
        columns = [desc[0] for desc in cursor.description]
        
        # Open output file
        with open(output_file, 'w') as f:
            # Write header comment
            f.write(f"-- Backup of table {table_name}\n")
            f.write(f"-- Generated on {datetime.datetime.now().isoformat()}\n\n")
            
            # Write INSERT statements for each row
            row_count = 0
            for row in cursor:
                # Format values for SQL
                values = []
                for val in row:
                    if val is None:
                        values.append("NULL")
                    elif isinstance(val, (int, float)):
                        values.append(str(val))
                    elif isinstance(val, (datetime.date, datetime.datetime)):
                        values.append(f"'{val.isoformat()}'")
                    else:
                        # Escape single quotes in string values
                        values.append(f"'{str(val).replace('\'', '\'\'')}'")
                
                # Write INSERT statement
                column_str = ", ".join(columns)
                value_str = ", ".join(values)
                f.write(f"INSERT INTO {table_name} ({column_str}) VALUES ({value_str});\n")
                row_count += 1
        
        LOGGER.info(f"Backed up {row_count} records from {table_name} to {output_file}")
        return True
    except (psycopg2.Error, IOError) as e:
        LOGGER.error(f"Error backing up table {table_name} to SQL: {str(e)}")
        return False


def backup_table_to_csv(conn, table_name, output_file):
    """
    Backs up a database table to a CSV file

    Args:
        conn (psycopg2.connection): Database connection
        table_name (str): Name of the table to backup
        output_file (str): Path to the output file

    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Create cursor
        cursor = conn.cursor()
        
        # Execute query to fetch all rows
        cursor.execute(f"SELECT * FROM {table_name}")
        
        # Get column names from cursor description
        columns = [desc[0] for desc in cursor.description]
        
        # Open output file
        with open(output_file, 'w') as f:
            # Write header row
            f.write(",".join(columns) + "\n")
            
            # Write data rows
            row_count = 0
            for row in cursor:
                # Format values for CSV
                values = []
                for val in row:
                    if val is None:
                        values.append("")
                    elif isinstance(val, (datetime.date, datetime.datetime)):
                        values.append(val.isoformat())
                    elif isinstance(val, (int, float)):
                        values.append(str(val))
                    else:
                        # Escape quotes and wrap in quotes if contains comma
                        val_str = str(val).replace('"', '""')
                        if "," in val_str:
                            val_str = f'"{val_str}"'
                        values.append(val_str)
                
                # Write row
                f.write(",".join(values) + "\n")
                row_count += 1
        
        LOGGER.info(f"Backed up {row_count} records from {table_name} to {output_file}")
        return True
    except (psycopg2.Error, IOError) as e:
        LOGGER.error(f"Error backing up table {table_name} to CSV: {str(e)}")
        return False


def create_backup_manifest(backup_dir, environment, tables, format):
    """
    Creates a manifest file with backup metadata

    Args:
        backup_dir (str): Path to the backup directory
        environment (str): Target environment
        tables (list): List of tables backed up
        format (str): Backup format

    Returns:
        bool: True if successful, False otherwise
    """
    try:
        # Create manifest data
        manifest = {
            "timestamp": datetime.datetime.now().isoformat(),
            "environment": environment,
            "tables": tables,
            "format": format,
            "db_version": None  # Placeholder for database version
        }
        
        # Path to manifest file
        manifest_file = os.path.join(backup_dir, MANIFEST_FILENAME)
        
        # Write manifest to file
        with open(manifest_file, 'w') as f:
            json.dump(manifest, f, indent=2)
        
        LOGGER.info(f"Created backup manifest: {manifest_file}")
        return True
    except IOError as e:
        LOGGER.error(f"Error creating backup manifest: {str(e)}")
        return False


def compress_backup(backup_dir):
    """
    Compresses the backup directory into a tar.gz archive

    Args:
        backup_dir (str): Path to the backup directory

    Returns:
        str: Path to the compressed archive
    """
    try:
        # Archive filename
        archive_name = backup_dir + ".tar.gz"
        
        # Create archive
        with tarfile.open(archive_name, "w:gz") as tar:
            # Add all files in the backup directory
            tar.add(backup_dir, arcname=os.path.basename(backup_dir))
        
        # Verify archive was created
        if not os.path.exists(archive_name):
            raise IOError(f"Failed to create archive: {archive_name}")
        
        # Remove original directory
        shutil.rmtree(backup_dir)
        
        LOGGER.info(f"Compressed backup to {archive_name}")
        return archive_name
    except (IOError, tarfile.TarError) as e:
        LOGGER.error(f"Error compressing backup: {str(e)}")
        raise


def cleanup_old_backups(output_dir, environment, retention_days):
    """
    Removes backups older than the retention period

    Args:
        output_dir (str): Base output directory
        environment (str): Target environment
        retention_days (int): Number of days to retain backups

    Returns:
        int: Number of backups removed
    """
    try:
        # Calculate cutoff date
        cutoff_date = datetime.datetime.now() - datetime.timedelta(days=retention_days)
        
        # Pattern for backup directories and archives
        pattern = f"{environment}_backup_"
        
        removed_count = 0
        
        # List all items in output directory
        for item in os.listdir(output_dir):
            item_path = os.path.join(output_dir, item)
            
            # Skip if not a backup for the specified environment
            if not item.startswith(pattern):
                continue
            
            try:
                # Extract timestamp from filename
                # Format: {environment}_backup_YYYYMMDD_HHMMSS
                timestamp_str = item.replace(pattern, "").split(".")[0]
                timestamp = datetime.datetime.strptime(timestamp_str, "%Y%m%d_%H%M%S")
                
                # Check if backup is older than cutoff date
                if timestamp < cutoff_date:
                    if os.path.isdir(item_path):
                        shutil.rmtree(item_path)
                        removed_count += 1
                    elif os.path.isfile(item_path):
                        os.remove(item_path)
                        removed_count += 1
                    
                    LOGGER.info(f"Removed old backup: {item}")
            except (ValueError, IndexError):
                LOGGER.warning(f"Could not parse timestamp from filename: {item}")
        
        LOGGER.info(f"Cleaned up {removed_count} old backups")
        return removed_count
    except OSError as e:
        LOGGER.error(f"Error cleaning up old backups: {str(e)}")
        return 0


def backup_metadata(environment, output_dir, tables, format, compress=True, notify=False):
    """
    Main function to backup database metadata

    Args:
        environment (str): Target environment
        output_dir (str): Output directory for backups
        tables (list): List of tables to backup
        format (str): Output format (json, sql, csv)
        compress (bool): Whether to compress backup files
        notify (bool): Whether to send notification about backup completion

    Returns:
        bool: True if backup is successful, False otherwise
    """
    # Validate environment
    if not validate_environment(environment):
        return False
    
    try:
        # Get environment configuration
        env_config = get_environment_config(environment)
        
        # Create backup directory
        backup_dir = create_backup_directory(output_dir, environment)
        
        # Get database connection
        db_config = env_config.get('database', {})
        conn = get_db_connection(db_config)
        
        # Backup each table
        success = True
        for table in tables:
            # Determine output file path
            output_file = os.path.join(backup_dir, f"{table}.{format}")
            
            # Backup table according to format
            if format == 'json':
                result = backup_table_to_json(conn, table, output_file)
            elif format == 'sql':
                result = backup_table_to_sql(conn, table, output_file)
            elif format == 'csv':
                result = backup_table_to_csv(conn, table, output_file)
            else:
                LOGGER.error(f"Unsupported format: {format}")
                result = False
            
            # Update success flag
            success = success and result
        
        # Create manifest file
        manifest_result = create_backup_manifest(backup_dir, environment, tables, format)
        success = success and manifest_result
        
        # Close database connection
        conn.close()
        
        # Compress backup if requested
        if compress and success:
            archive_path = compress_backup(backup_dir)
            backup_path = archive_path
        else:
            backup_path = backup_dir
        
        # Clean up old backups
        cleanup_old_backups(output_dir, environment, BACKUP_RETENTION_DAYS)
        
        # Send notification if requested
        if notify:
            notification_config = env_config.get('notification_channels', {})
            notification_data = {
                "environment": environment,
                "backup_path": backup_path,
                "tables": tables,
                "format": format,
                "timestamp": datetime.datetime.now().isoformat()
            }
            send_notification(
                message=f"Backup completed for {environment}",
                level="info" if success else "error",
                notification_config=notification_config,
                additional_data=notification_data
            )
        
        # Log completion
        if success:
            LOGGER.info(f"Backup completed successfully: {backup_path}")
        else:
            LOGGER.error(f"Backup completed with errors: {backup_path}")
        
        return success
    except Exception as e:
        LOGGER.error(f"Error during backup: {str(e)}")
        return False


def main():
    """
    Main entry point for the script

    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    try:
        # Parse command line arguments
        args = parse_arguments()
        
        # Execute backup
        success = backup_metadata(
            environment=args.environment,
            output_dir=args.output_dir,
            tables=args.tables,
            format=args.format,
            compress=args.compress,
            notify=args.notify
        )
        
        return 0 if success else 1
    except Exception as e:
        LOGGER.error(f"Unhandled exception: {str(e)}")
        return 1


if __name__ == "__main__":
    sys.exit(main())