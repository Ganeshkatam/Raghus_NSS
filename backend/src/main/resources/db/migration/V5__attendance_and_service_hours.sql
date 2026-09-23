-- V5__attendance_and_service_hours.sql
-- Attendance Sessions, Attendance Records, Corrections, and Service Hours Ledger

CREATE TABLE IF NOT EXISTS attendance_sessions (
    session_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    opened_by BIGINT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'EXPIRED')),
    token_hash VARCHAR(255),
    qr_secret VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_session_times CHECK (expires_at > starts_at)
);

CREATE INDEX idx_att_sessions_event ON attendance_sessions(event_id);
CREATE INDEX idx_att_sessions_status ON attendance_sessions(status);

CREATE TABLE IF NOT EXISTS attendance_records (
    attendance_id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES attendance_sessions(session_id) ON DELETE CASCADE,
    volunteer_id BIGINT NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    checked_in_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    check_in_method VARCHAR(30) NOT NULL DEFAULT 'QR' CHECK (check_in_method IN ('QR', 'MANUAL_COORDINATOR')),
    status VARCHAR(20) NOT NULL DEFAULT 'PRESENT' CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED')),
    CONSTRAINT uq_session_volunteer_attendance UNIQUE (session_id, volunteer_id)
);

CREATE INDEX idx_att_records_session ON attendance_records(session_id);
CREATE INDEX idx_att_records_volunteer ON attendance_records(volunteer_id);

CREATE TABLE IF NOT EXISTS attendance_corrections (
    correction_id BIGSERIAL PRIMARY KEY,
    attendance_id BIGINT NOT NULL REFERENCES attendance_records(attendance_id) ON DELETE CASCADE,
    corrected_by BIGINT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_att_corrections_record ON attendance_corrections(attendance_id);

CREATE TABLE IF NOT EXISTS service_hour_entries (
    entry_id BIGSERIAL PRIMARY KEY,
    volunteer_id BIGINT NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    event_id BIGINT REFERENCES events(event_id) ON DELETE SET NULL,
    attendance_id BIGINT REFERENCES attendance_records(attendance_id) ON DELETE SET NULL,
    hours NUMERIC(5,2) NOT NULL CHECK (hours > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED')),
    approved_by BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_service_hours_volunteer ON service_hour_entries(volunteer_id);
CREATE INDEX idx_service_hours_event ON service_hour_entries(event_id);
CREATE INDEX idx_service_hours_status ON service_hour_entries(status);
