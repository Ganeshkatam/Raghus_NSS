-- V17__audit_log_and_attendance_invariants.sql
-- 1. Central immutable audit log table
CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID,
    actor_name VARCHAR(255),
    actor_email VARCHAR(255),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    entity_name VARCHAR(255),
    previous_state VARCHAR(255),
    new_state VARCHAR(255),
    reason TEXT,
    before_json TEXT,
    after_json TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500)
);

-- Ensure all columns exist if audit_logs table was already created in an earlier migration (e.g. V2)
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_name VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS actor_email VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS entity_name VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS previous_state VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_state VARCHAR(255);
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS before_json TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS after_json TEXT;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_user_id);

-- 2. Ensure status column exists on attendance_corrections
ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
CREATE INDEX IF NOT EXISTS idx_att_corrections_status ON attendance_corrections(status);

-- 3. Enforce single OPEN attendance session per event at database level
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_open_session_per_event
ON attendance_sessions (event_id)
WHERE status = 'OPEN';
