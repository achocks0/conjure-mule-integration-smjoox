-- V1__Initial_Schema.sql
-- Initial database schema for Payment API Security Enhancement

-- Create credentials table for storing credential metadata
CREATE TABLE credentials (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL UNIQUE,
    hashed_secret VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);

-- Create tokens table for storing authentication tokens
CREATE TABLE tokens (
    id BIGSERIAL PRIMARY KEY,
    token_string TEXT NOT NULL,
    jti VARCHAR(50) NOT NULL UNIQUE,
    client_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expiration_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

-- Create authentication_events table for audit logging
CREATE TABLE authentication_events (
    event_id UUID PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    event_time TIMESTAMP NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(255)
);

-- Create indexes for performance optimization
CREATE INDEX idx_client_id ON credentials(client_id);
CREATE INDEX idx_token_jti ON tokens(jti);
CREATE INDEX idx_token_client_id ON tokens(client_id);
CREATE INDEX idx_token_expiration ON tokens(expiration_time);
CREATE INDEX idx_auth_client_time ON authentication_events(client_id, event_time);
CREATE INDEX idx_auth_status ON authentication_events(status);

-- Add comments for documentation
COMMENT ON TABLE credentials IS 'Stores credential metadata for authentication with vendors';
COMMENT ON TABLE tokens IS 'Stores authentication tokens for service-to-service communication';
COMMENT ON TABLE authentication_events IS 'Stores authentication event logs for audit and security monitoring';