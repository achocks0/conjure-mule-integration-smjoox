-- V1__Initial_Schema.sql
-- Initial database schema for Payment-SAPI
-- Version: 1.0.0
-- 
-- This script creates the following tables:
-- 1. payments - Stores payment transaction data
-- 2. tokens - Stores authentication token information
-- 3. authentication_events - Audit log for authentication activities

-- Create payments table
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL UNIQUE,
    client_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_payments_payment_id (payment_id),
    INDEX idx_payments_client_id (client_id),
    INDEX idx_payments_reference (client_id, reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create tokens table for JWT token management
CREATE TABLE tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(50) NOT NULL UNIQUE COMMENT 'JWT Token ID - unique identifier for the token',
    client_id VARCHAR(50) NOT NULL,
    token_string TEXT NOT NULL COMMENT 'The actual JWT token string',
    expiration_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'ACTIVE, REVOKED, EXPIRED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    user_agent VARCHAR(255),
    
    INDEX idx_token_jti (jti),
    INDEX idx_token_client_id (client_id),
    INDEX idx_token_expiration (expiration_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create authentication_events table for audit logging
CREATE TABLE authentication_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    client_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'LOGIN, TOKEN_VALIDATION, TOKEN_RENEWAL, etc.',
    status VARCHAR(20) NOT NULL COMMENT 'SUCCESS, FAILURE',
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) COMMENT 'IPv4 or IPv6 address',
    user_agent VARCHAR(255),
    details TEXT COMMENT 'Additional details about the event (no sensitive data)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_auth_event_id (event_id),
    INDEX idx_auth_client_time (client_id, event_time),
    INDEX idx_auth_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create credential_rotations table to track credential rotation activities
CREATE TABLE credential_rotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rotation_id VARCHAR(36) NOT NULL UNIQUE,
    client_id VARCHAR(50) NOT NULL,
    old_version VARCHAR(50) NOT NULL,
    new_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'IN_PROGRESS, COMPLETED, FAILED',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_rotation_client (client_id),
    INDEX idx_rotation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create token_blacklist table for explicitly revoked tokens
CREATE TABLE token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(50) NOT NULL UNIQUE,
    revocation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(255),
    revoked_by VARCHAR(50) NOT NULL,
    
    INDEX idx_blacklist_jti (jti)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add table comments
ALTER TABLE payments COMMENT 'Stores payment transaction data';
ALTER TABLE tokens COMMENT 'Stores JWT token information for authentication';
ALTER TABLE authentication_events COMMENT 'Audit log for authentication activities';
ALTER TABLE credential_rotations COMMENT 'Tracks credential rotation activities';
ALTER TABLE token_blacklist COMMENT 'Stores explicitly revoked tokens';

-- Create table for partitioning authentication events by month
-- This enables efficient management of the audit log with retention policies
CREATE TABLE authentication_events_partitioned (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    client_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_part_event_id (event_id),
    INDEX idx_part_client_time (client_id, event_time),
    INDEX idx_part_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
PARTITION BY RANGE (UNIX_TIMESTAMP(event_time)) (
    PARTITION p202301 VALUES LESS THAN (UNIX_TIMESTAMP('2023-02-01 00:00:00')),
    PARTITION p202302 VALUES LESS THAN (UNIX_TIMESTAMP('2023-03-01 00:00:00')),
    PARTITION p202303 VALUES LESS THAN (UNIX_TIMESTAMP('2023-04-01 00:00:00')),
    PARTITION p202304 VALUES LESS THAN (UNIX_TIMESTAMP('2023-05-01 00:00:00')),
    PARTITION p202305 VALUES LESS THAN (UNIX_TIMESTAMP('2023-06-01 00:00:00')),
    PARTITION p202306 VALUES LESS THAN (UNIX_TIMESTAMP('2023-07-01 00:00:00')),
    PARTITION p202307 VALUES LESS THAN (UNIX_TIMESTAMP('2023-08-01 00:00:00')),
    PARTITION p202308 VALUES LESS THAN (UNIX_TIMESTAMP('2023-09-01 00:00:00')),
    PARTITION p202309 VALUES LESS THAN (UNIX_TIMESTAMP('2023-10-01 00:00:00')),
    PARTITION p202310 VALUES LESS THAN (UNIX_TIMESTAMP('2023-11-01 00:00:00')),
    PARTITION p202311 VALUES LESS THAN (UNIX_TIMESTAMP('2023-12-01 00:00:00')),
    PARTITION p202312 VALUES LESS THAN (UNIX_TIMESTAMP('2024-01-01 00:00:00')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Create view for active tokens to simplify token validation queries
CREATE VIEW active_tokens AS
SELECT t.*
FROM tokens t
LEFT JOIN token_blacklist b ON t.jti = b.jti
WHERE t.status = 'ACTIVE'
  AND t.expiration_time > NOW()
  AND b.jti IS NULL;

-- Initial system indexes for performance optimization
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_tokens_status_expiration ON tokens (status, expiration_time);
CREATE INDEX idx_auth_events_time ON authentication_events (event_time);