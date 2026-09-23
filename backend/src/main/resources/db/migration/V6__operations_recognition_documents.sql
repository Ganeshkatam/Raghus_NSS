-- V6__operations_recognition_documents.sql
-- Documents, Activity Reports, Achievements, Certificates, Announcements, and Notifications

CREATE TABLE IF NOT EXISTS documents (
    document_id BIGSERIAL PRIMARY KEY,
    uploaded_by BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    storage_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activity_reports (
    report_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
    submitted_by BIGINT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    document_id BIGINT REFERENCES documents(document_id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT NOT NULL,
    attendee_count INT NOT NULL DEFAULT 0,
    service_hours_total NUMERIC(7,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED')),
    submitted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_reports_event ON activity_reports(event_id);

CREATE TABLE IF NOT EXISTS achievements (
    achievement_id BIGSERIAL PRIMARY KEY,
    volunteer_id BIGINT NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES documents(document_id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    awarded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_achievements_volunteer ON achievements(volunteer_id);

CREATE TABLE IF NOT EXISTS certificates (
    certificate_id BIGSERIAL PRIMARY KEY,
    volunteer_id BIGINT NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    document_id BIGINT REFERENCES documents(document_id) ON DELETE SET NULL,
    certificate_number VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    issued_by BIGINT REFERENCES users(user_id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED', 'REVOKED')),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_certificates_volunteer ON certificates(volunteer_id);
CREATE INDEX idx_certificates_number ON certificates(certificate_number);

CREATE TABLE IF NOT EXISTS announcements (
    announcement_id BIGSERIAL PRIMARY KEY,
    created_by BIGINT NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    unit_id BIGINT REFERENCES nss_units(unit_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_announcements_unit ON announcements(unit_id);
CREATE INDEX idx_announcements_published ON announcements(published_at);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
