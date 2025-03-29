#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Utility script for performing database maintenance tasks such as table optimization,
index maintenance, and data archiving for the Payment API Security Enhancement project.

This script is designed to be run as a scheduled task to maintain database performance
and ensure compliance with data retention policies.
"""

# Internal imports
from config import LOGGER, setup_logging, load_config, get_database_config, DatabaseConfig
from utils import DatabaseManager, DatabaseError, execute_db_query, batch_execute_db_query, format_timestamp

# External imports
import argparse  # standard library
import sys  # standard library
import os  # standard library
import datetime  # standard library
import time  # standard library
import psycopg2  # version 2.9.3

# Global variables
logger = LOGGER
DEFAULT_BATCH_SIZE = 1000
DEFAULT_VACUUM_TABLES = ['TOKEN_METADATA', 'AUTHENTICATION_EVENT', 'CREDENTIAL_ROTATION']
DEFAULT_REINDEX_TABLES = ['TOKEN_METADATA', 'AUTHENTICATION_EVENT', 'CLIENT_CREDENTIAL', 'CREDENTIAL_ROTATION']
DEFAULT_ANALYZE_TABLES = ['TOKEN_METADATA', 'AUTHENTICATION_EVENT', 'CLIENT_CREDENTIAL', 'CREDENTIAL_ROTATION']
DEFAULT_ARCHIVE_TABLES = ['AUTHENTICATION_EVENT']
DEFAULT_RETENTION_DAYS = {
    'TOKEN_METADATA': 90,
    'AUTHENTICATION_EVENT': 90,
    'CREDENTIAL_ROTATION': 365
}


class MaintenanceOptions:
    """Configuration class for database maintenance operations."""
    
    def __init__(self, vacuum=False, reindex=False, analyze=False, archive=False,
                 tables=None, retention_days=None, batch_size=DEFAULT_BATCH_SIZE, dry_run=False):
        """
        Initializes a new MaintenanceOptions instance.
        
        Args:
            vacuum (bool): Whether to perform VACUUM operation
            reindex (bool): Whether to rebuild indexes
            analyze (bool): Whether to update table statistics
            archive (bool): Whether to archive old data
            tables (list): List of tables to maintain
            retention_days (dict): Retention period in days for each table
            batch_size (int): Number of records to process in each batch
            dry_run (bool): Whether to perform a dry run without making changes
        """
        self.vacuum = vacuum
        self.reindex = reindex
        self.analyze = analyze
        self.archive = archive
        self.tables = tables or []
        self.retention_days = retention_days or DEFAULT_RETENTION_DAYS
        self.batch_size = batch_size
        self.dry_run = dry_run
    
    def to_dict(self):
        """
        Converts the configuration to a dictionary.
        
        Returns:
            dict: Dictionary representation of the configuration
        """
        return {
            'vacuum': self.vacuum,
            'reindex': self.reindex,
            'analyze': self.analyze,
            'archive': self.archive,
            'tables': self.tables,
            'retention_days': self.retention_days,
            'batch_size': self.batch_size,
            'dry_run': self.dry_run
        }
    
    @staticmethod
    def from_dict(config_dict):
        """
        Creates a MaintenanceOptions instance from a dictionary.
        
        Args:
            config_dict (dict): Configuration dictionary
        
        Returns:
            MaintenanceOptions: MaintenanceOptions instance
        """
        return MaintenanceOptions(
            vacuum=config_dict.get('vacuum', False),
            reindex=config_dict.get('reindex', False),
            analyze=config_dict.get('analyze', False),
            archive=config_dict.get('archive', False),
            tables=config_dict.get('tables', []),
            retention_days=config_dict.get('retention_days', DEFAULT_RETENTION_DAYS),
            batch_size=config_dict.get('batch_size', DEFAULT_BATCH_SIZE),
            dry_run=config_dict.get('dry_run', False)
        )
    
    def validate(self):
        """
        Validates the maintenance options.
        
        Returns:
            bool: True if configuration is valid, False otherwise
        """
        # Check if at least one operation is enabled
        if not any([self.vacuum, self.reindex, self.analyze, self.archive]):
            logger.error("No maintenance operations specified")
            return False
        
        # Check batch size
        if self.batch_size <= 0:
            logger.error(f"Invalid batch size: {self.batch_size}")
            return False
        
        # Check tables if specified
        if self.tables and not all(isinstance(table, str) for table in self.tables):
            logger.error("Invalid table names in tables list")
            return False
        
        # Check retention days
        if not isinstance(self.retention_days, dict):
            logger.error("retention_days must be a dictionary")
            return False
        
        for table, days in self.retention_days.items():
            if not isinstance(days, int) or days <= 0:
                logger.error(f"Invalid retention period for table {table}: {days}")
                return False
        
        return True


def parse_arguments():
    """
    Parses command-line arguments for the script.
    
    Returns:
        argparse.Namespace: Parsed command-line arguments
    """
    parser = argparse.ArgumentParser(
        description='Database maintenance utility for Payment API Security Enhancement project'
    )
    
    parser.add_argument('--config', type=str, default=None,
                        help='Path to configuration file')
    
    parser.add_argument('--vacuum', action='store_true',
                        help='Perform VACUUM operation on tables')
    
    parser.add_argument('--reindex', action='store_true',
                        help='Rebuild indexes on tables')
    
    parser.add_argument('--analyze', action='store_true',
                        help='Update table statistics for query planner')
    
    parser.add_argument('--archive', action='store_true',
                        help='Archive old data based on retention policy')
    
    parser.add_argument('--all', action='store_true',
                        help='Perform all maintenance operations')
    
    parser.add_argument('--tables', type=str, nargs='+',
                        help='Specific tables to maintain (space-separated)')
    
    parser.add_argument('--batch-size', type=int, default=DEFAULT_BATCH_SIZE,
                        help=f'Batch size for operations (default: {DEFAULT_BATCH_SIZE})')
    
    parser.add_argument('--dry-run', action='store_true',
                        help='Perform a dry run without making actual changes')
    
    parser.add_argument('--verbose', action='store_true',
                        help='Enable verbose logging')
    
    return parser.parse_args()


def vacuum_tables(db_manager, tables, dry_run):
    """
    Performs VACUUM operation on specified tables to reclaim storage and update statistics.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        tables (list): List of tables to vacuum
        dry_run (bool): Whether to perform a dry run without making changes
    
    Returns:
        dict: Operation statistics
    """
    logger.info("Starting VACUUM operation")
    stats = {'tables_processed': 0, 'status': {}}
    
    if dry_run:
        logger.info("DRY RUN: No actual changes will be made")
    
    for table in tables:
        logger.info(f"Vacuuming table: {table}")
        try:
            if not dry_run:
                # Execute VACUUM ANALYZE on the table
                query = f"VACUUM ANALYZE {table};"
                db_manager.execute_query(query)
            
            stats['tables_processed'] += 1
            stats['status'][table] = 'success'
            logger.info(f"VACUUM completed for table {table}")
            
        except Exception as e:
            logger.error(f"Error vacuuming table {table}: {str(e)}")
            stats['status'][table] = f'error: {str(e)}'
    
    logger.info(f"VACUUM operation completed. {stats['tables_processed']} tables processed.")
    return stats


def reindex_tables(db_manager, tables, dry_run):
    """
    Rebuilds indexes on specified tables to improve query performance.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        tables (list): List of tables to reindex
        dry_run (bool): Whether to perform a dry run without making changes
    
    Returns:
        dict: Operation statistics
    """
    logger.info("Starting REINDEX operation")
    stats = {'tables_processed': 0, 'status': {}}
    
    if dry_run:
        logger.info("DRY RUN: No actual changes will be made")
    
    for table in tables:
        logger.info(f"Reindexing table: {table}")
        try:
            if not dry_run:
                # Execute REINDEX TABLE on the table
                query = f"REINDEX TABLE {table};"
                db_manager.execute_query(query)
            
            stats['tables_processed'] += 1
            stats['status'][table] = 'success'
            logger.info(f"REINDEX completed for table {table}")
            
        except Exception as e:
            logger.error(f"Error reindexing table {table}: {str(e)}")
            stats['status'][table] = f'error: {str(e)}'
    
    logger.info(f"REINDEX operation completed. {stats['tables_processed']} tables processed.")
    return stats


def analyze_tables(db_manager, tables, dry_run):
    """
    Updates statistics on specified tables for the query planner.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        tables (list): List of tables to analyze
        dry_run (bool): Whether to perform a dry run without making changes
    
    Returns:
        dict: Operation statistics
    """
    logger.info("Starting ANALYZE operation")
    stats = {'tables_processed': 0, 'status': {}}
    
    if dry_run:
        logger.info("DRY RUN: No actual changes will be made")
    
    for table in tables:
        logger.info(f"Analyzing table: {table}")
        try:
            if not dry_run:
                # Execute ANALYZE on the table
                query = f"ANALYZE {table};"
                db_manager.execute_query(query)
            
            stats['tables_processed'] += 1
            stats['status'][table] = 'success'
            logger.info(f"ANALYZE completed for table {table}")
            
        except Exception as e:
            logger.error(f"Error analyzing table {table}: {str(e)}")
            stats['status'][table] = f'error: {str(e)}'
    
    logger.info(f"ANALYZE operation completed. {stats['tables_processed']} tables processed.")
    return stats


def archive_data(db_manager, tables, retention_days, batch_size, dry_run):
    """
    Archives old data from specified tables based on retention policy.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        tables (list): List of tables to archive data from
        retention_days (dict): Retention period in days for each table
        batch_size (int): Number of records to process in each batch
        dry_run (bool): Whether to perform a dry run without making changes
    
    Returns:
        dict: Operation statistics
    """
    logger.info("Starting archiving operation")
    stats = {'tables_processed': 0, 'rows_archived': 0, 'status': {}}
    
    if dry_run:
        logger.info("DRY RUN: No actual changes will be made")
    
    for table in tables:
        logger.info(f"Archiving data from table: {table}")
        
        # Get retention period for the table or use default
        retention_period = retention_days.get(table, 90)
        
        # Calculate cutoff date based on retention period
        cutoff_date = datetime.datetime.now() - datetime.timedelta(days=retention_period)
        
        logger.info(f"Archiving data older than {cutoff_date.isoformat()} from {table}")
        
        try:
            # Handle specifically for the AUTHENTICATION_EVENT table with monthly partitioning
            if table == 'AUTHENTICATION_EVENT':
                # Find partitions older than the cutoff date
                partition_query = """
                    SELECT table_name 
                    FROM information_schema.tables 
                    WHERE table_name LIKE 'authentication_event_%' 
                    AND table_name != 'authentication_event_archive'
                    ORDER BY table_name;
                """
                
                partitions = db_manager.execute_query(partition_query, fetch_all=True)
                
                if partitions:
                    for partition_record in partitions:
                        partition_name = partition_record[0]
                        
                        # Extract date from partition name (format: authentication_event_YYYYMM)
                        try:
                            partition_date_str = partition_name.split('_')[-1]
                            year = int(partition_date_str[:4])
                            month = int(partition_date_str[4:])
                            partition_date = datetime.datetime(year, month, 1)
                            
                            # Check if partition is older than cutoff date
                            if partition_date < cutoff_date:
                                logger.info(f"Archiving partition {partition_name}")
                                
                                if not dry_run:
                                    # Get row count before dropping the partition
                                    count_query = f"SELECT COUNT(*) FROM {partition_name};"
                                    result = db_manager.execute_query(count_query)
                                    rows_in_partition = result[0] if result else 0
                                    
                                    # Move data to archive table
                                    archive_query = f"""
                                        INSERT INTO authentication_event_archive
                                        SELECT * FROM {partition_name};
                                    """
                                    db_manager.execute_query(archive_query)
                                    
                                    # Drop the partition after archiving
                                    drop_query = f"DROP TABLE {partition_name};"
                                    db_manager.execute_query(drop_query)
                                    
                                    db_manager.connection.commit()
                                    
                                    stats['rows_archived'] += rows_in_partition
                                else:
                                    # For dry run, just count the rows
                                    count_query = f"SELECT COUNT(*) FROM {partition_name};"
                                    result = db_manager.execute_query(count_query)
                                    rows_in_partition = result[0] if result else 0
                                    logger.info(f"DRY RUN: Would archive {rows_in_partition} rows from partition {partition_name}")
                                
                                # Update statistics
                                if partition_name not in stats['status']:
                                    stats['status'][partition_name] = {}
                                
                                stats['status'][partition_name] = {
                                    'status': 'success', 
                                    'rows_archived': rows_in_partition
                                }
                                stats['tables_processed'] += 1
                                
                        except (ValueError, IndexError) as e:
                            logger.warning(f"Could not parse date from partition name {partition_name}: {str(e)}")
            else:
                # For other tables, archive data in batches
                total_archived = 0
                
                # Get the timestamp field name for the table
                timestamp_field_map = {
                    'TOKEN_METADATA': 'expires_at',
                    'CREDENTIAL_ROTATION': 'completed_at',
                    'CLIENT_CREDENTIAL': 'updated_at'
                }
                
                timestamp_field = timestamp_field_map.get(table, 'event_time')
                
                # Convert cutoff date to timestamp if needed
                cutoff_timestamp = cutoff_date.timestamp() if timestamp_field == 'expires_at' else cutoff_date
                
                # Query to get count of records to archive
                count_query = f"""
                    SELECT COUNT(*) FROM {table} 
                    WHERE {timestamp_field} < %s;
                """
                
                result = db_manager.execute_query(count_query, (cutoff_timestamp,))
                total_to_archive = result[0] if result else 0
                
                logger.info(f"Found {total_to_archive} records to archive in {table}")
                
                if total_to_archive > 0 and not dry_run:
                    # Archive data in batches
                    while total_archived < total_to_archive:
                        # Determine archive table name
                        archive_table = f"{table.lower()}_archive"
                        
                        # Check if archive table exists, create if not
                        check_archive_table_query = f"""
                            SELECT EXISTS (
                                SELECT FROM information_schema.tables 
                                WHERE table_name = '{archive_table}'
                            );
                        """
                        table_exists = db_manager.execute_query(check_archive_table_query)[0]
                        
                        if not table_exists:
                            # Create archive table with same structure
                            create_archive_table_query = f"""
                                CREATE TABLE IF NOT EXISTS {archive_table} 
                                (LIKE {table} INCLUDING ALL);
                            """
                            db_manager.execute_query(create_archive_table_query)
                            logger.info(f"Created archive table {archive_table}")
                        
                        # Move data to archive table in batches
                        archive_query = f"""
                            WITH rows_to_archive AS (
                                SELECT * FROM {table}
                                WHERE {timestamp_field} < %s
                                LIMIT {batch_size}
                            )
                            INSERT INTO {archive_table}
                            SELECT * FROM rows_to_archive;
                        """
                        
                        db_manager.execute_query(archive_query, (cutoff_timestamp,))
                        
                        # Delete archived rows from source table
                        delete_query = f"""
                            DELETE FROM {table}
                            WHERE {table}.{timestamp_field} < %s
                            AND {table}.{timestamp_field} IN (
                                SELECT {archive_table}.{timestamp_field} 
                                FROM {archive_table}
                                WHERE {archive_table}.{timestamp_field} < %s
                                ORDER BY {archive_table}.{timestamp_field}
                                LIMIT {batch_size}
                            );
                        """
                        
                        db_manager.execute_query(delete_query, (cutoff_timestamp, cutoff_timestamp))
                        rows_affected = db_manager.connection.cursor.rowcount
                        
                        total_archived += rows_affected
                        stats['rows_archived'] += rows_affected
                        
                        db_manager.connection.commit()
                        
                        logger.info(f"Archived {total_archived}/{total_to_archive} records from {table}")
                        
                        # Break if no more rows were affected
                        if rows_affected == 0:
                            break
                elif dry_run:
                    logger.info(f"DRY RUN: Would archive {total_to_archive} records from {table}")
                
                # Update statistics
                stats['tables_processed'] += 1
                stats['status'][table] = {
                    'status': 'success',
                    'rows_archived': total_to_archive if dry_run else total_archived
                }
                
        except Exception as e:
            logger.error(f"Error archiving data from table {table}: {str(e)}")
            stats['status'][table] = {'status': f'error: {str(e)}', 'rows_archived': 0}
    
    logger.info(f"Archiving operation completed. {stats['rows_archived']} rows archived from {stats['tables_processed']} tables.")
    return stats


def perform_maintenance(db_manager, options):
    """
    Performs selected maintenance operations on database tables.
    
    Args:
        db_manager (DatabaseManager): Database manager instance
        options (dict): Maintenance options
    
    Returns:
        dict: Maintenance statistics
    """
    logger.info("Starting database maintenance")
    stats = {}
    
    # Extract options
    vacuum = options.get('vacuum', False)
    reindex = options.get('reindex', False)
    analyze = options.get('analyze', False)
    archive = options.get('archive', False)
    tables = options.get('tables', [])
    batch_size = options.get('batch_size', DEFAULT_BATCH_SIZE)
    retention_days = options.get('retention_days', DEFAULT_RETENTION_DAYS)
    dry_run = options.get('dry_run', False)
    
    # Use default tables if none specified, based on operation
    if not tables:
        if vacuum:
            vacuum_tables_list = DEFAULT_VACUUM_TABLES
        else:
            vacuum_tables_list = []
        
        if reindex:
            reindex_tables_list = DEFAULT_REINDEX_TABLES
        else:
            reindex_tables_list = []
        
        if analyze:
            analyze_tables_list = DEFAULT_ANALYZE_TABLES
        else:
            analyze_tables_list = []
        
        if archive:
            archive_tables_list = DEFAULT_ARCHIVE_TABLES
        else:
            archive_tables_list = []
    else:
        vacuum_tables_list = tables if vacuum else []
        reindex_tables_list = tables if reindex else []
        analyze_tables_list = tables if analyze else []
        archive_tables_list = tables if archive else []
    
    # Start timer
    start_time = time.time()
    
    # Perform selected operations
    if vacuum and vacuum_tables_list:
        vacuum_stats = vacuum_tables(db_manager, vacuum_tables_list, dry_run)
        stats['vacuum'] = vacuum_stats
    
    if reindex and reindex_tables_list:
        reindex_stats = reindex_tables(db_manager, reindex_tables_list, dry_run)
        stats['reindex'] = reindex_stats
    
    if analyze and analyze_tables_list:
        analyze_stats = analyze_tables(db_manager, analyze_tables_list, dry_run)
        stats['analyze'] = analyze_stats
    
    if archive and archive_tables_list:
        archive_stats = archive_data(db_manager, archive_tables_list, retention_days, batch_size, dry_run)
        stats['archive'] = archive_stats
    
    # Calculate execution time
    execution_time = time.time() - start_time
    stats['execution_time'] = execution_time
    
    logger.info(f"Database maintenance completed in {execution_time:.2f} seconds")
    logger.info(f"Maintenance statistics: {stats}")
    
    return stats


def main():
    """
    Main entry point for the script.
    
    Returns:
        int: Exit code (0 for success, non-zero for failure)
    """
    # Parse command-line arguments
    args = parse_arguments()
    
    # Set up logging
    log_level = "DEBUG" if args.verbose else "INFO"
    setup_logging(log_level)
    
    logger.info("Starting database maintenance script")
    
    try:
        # Load configuration
        config_file = args.config
        config = load_config(config_file, env_prefix="PAYMENT_")
        
        # Get database configuration
        db_config = get_database_config(config)
        db_config_obj = DatabaseConfig.from_dict(db_config)
        
        if not db_config_obj.validate():
            logger.error("Invalid database configuration")
            return 1
        
        # Initialize database manager
        db_manager = DatabaseManager(db_config_obj)
        
        # Connect to database
        if not db_manager.connect():
            logger.error("Failed to connect to database")
            return 1
        
        # Prepare maintenance options
        options = {
            'vacuum': args.vacuum,
            'reindex': args.reindex,
            'analyze': args.analyze,
            'archive': args.archive,
            'tables': args.tables,
            'batch_size': args.batch_size,
            'dry_run': args.dry_run
        }
        
        # If --all flag is used, enable all operations
        if args.all:
            options['vacuum'] = True
            options['reindex'] = True
            options['analyze'] = True
            options['archive'] = True
        
        # Create and validate maintenance options
        maintenance_options = MaintenanceOptions.from_dict(options)
        
        if not maintenance_options.validate():
            logger.error("Invalid maintenance options")
            return 1
        
        # Perform maintenance
        stats = perform_maintenance(db_manager, maintenance_options.to_dict())
        
        # Disconnect from database
        db_manager.disconnect()
        
        logger.info("Database maintenance script completed successfully")
        return 0
        
    except Exception as e:
        logger.error(f"Error during database maintenance: {str(e)}")
        return 1


if __name__ == '__main__':
    sys.exit(main())