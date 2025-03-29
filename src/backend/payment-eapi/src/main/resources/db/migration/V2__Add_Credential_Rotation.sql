-- Add rotation_state column to credentials table
ALTER TABLE credentials ADD COLUMN rotation_state VARCHAR(20);

COMMENT ON COLUMN credentials.rotation_state IS 'Current state of credential rotation (INITIATED, DUAL_ACTIVE, OLD_DEPRECATED, NEW_ACTIVE, FAILED)';

-- Create credential_rotation_history table for tracking rotation events
CREATE TABLE credential_rotation_history (
    rotation_id UUID PRIMARY KEY,
    client_id VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    old_version VARCHAR(50) NOT NULL,
    new_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    initiated_by VARCHAR(100),
    notes TEXT
);

-- Create indexes for performance optimization
CREATE INDEX idx_rotation_client ON credential_rotation_history(client_id);
CREATE INDEX idx_rotation_status ON credential_rotation_history(status);

-- Add foreign key constraint
ALTER TABLE credential_rotation_history ADD CONSTRAINT fk_rotation_client_id FOREIGN KEY (client_id) REFERENCES credentials(client_id);

-- Add comment for documentation
COMMENT ON TABLE credential_rotation_history IS 'Stores history of credential rotation events for audit and tracking purposes';