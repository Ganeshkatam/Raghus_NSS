-- V20__create_auth_sessions.sql
-- Create auth_sessions table for server-side refresh session tracking

CREATE TABLE auth_sessions (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(64) NOT NULL,
    token_family_id UUID NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_label VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoke_reason VARCHAR(50),
    CONSTRAINT uk_auth_sessions_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT chk_auth_sessions_lifecycle CHECK (
        (is_active = TRUE AND revoked_at IS NULL) OR
        (is_active = FALSE AND revoked_at IS NOT NULL)
    )
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions(user_id);
CREATE INDEX idx_auth_sessions_token_family_id ON auth_sessions(token_family_id);
CREATE INDEX idx_auth_sessions_active_expiry ON auth_sessions(is_active, expires_at);
