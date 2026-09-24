-- V13__v1_expanded_capabilities.sql
-- Schema extensions for Phase A V1 production capabilities:
-- Volunteer onboarding/status, Unit transfers, Event waitlist, Service hour categories,
-- Attendance correction workflow, and Notifications.

-- 1. Volunteers: expand status domain
ALTER TABLE volunteers DROP CONSTRAINT IF EXISTS volunteers_status_check;
ALTER TABLE volunteers ADD CONSTRAINT volunteers_status_check 
    CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'ALUMNI'));

-- 2. NSS Units: capacity constraint
ALTER TABLE nss_units ADD COLUMN IF NOT EXISTS capacity INT NOT NULL DEFAULT 100;

-- 3. Volunteer Status History
CREATE TABLE IF NOT EXISTS volunteer_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason VARCHAR(255),
    changed_by_user_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_vsh_volunteer ON volunteer_status_history(volunteer_id);

-- 4. Unit Transfer History
CREATE TABLE IF NOT EXISTS unit_transfer_history (
    transfer_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    volunteer_id UUID NOT NULL REFERENCES volunteers(volunteer_id) ON DELETE CASCADE,
    from_unit_id UUID REFERENCES nss_units(unit_id) ON DELETE SET NULL,
    to_unit_id UUID NOT NULL REFERENCES nss_units(unit_id) ON DELETE CASCADE,
    reason VARCHAR(255),
    authorized_by_id UUID REFERENCES users(user_id) ON DELETE SET NULL,
    transferred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_uth_volunteer ON unit_transfer_history(volunteer_id);

-- 5. Event Registrations: waitlist and cancellation tracking
ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(255);
ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS waitlist_position INT;
ALTER TABLE event_registrations DROP CONSTRAINT IF EXISTS event_registrations_status_check;
ALTER TABLE event_registrations ADD CONSTRAINT event_registrations_status_check 
    CHECK (status IN ('CONFIRMED', 'WAITLISTED', 'CANCELLED', 'ATTENDED'));

-- 6. Service Hour Entries: categorization and evidence
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS category VARCHAR(50) NOT NULL DEFAULT 'REGULAR_ACTIVITY';
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS evidence_note TEXT;
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS activity_date DATE;

-- 7. Attendance Corrections: review tracking
ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS review_remarks VARCHAR(255);
ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS reviewed_by_id UUID REFERENCES users(user_id) ON DELETE SET NULL;
ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE;

-- 8. Notifications: deep links and category classification
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link VARCHAR(255);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS category VARCHAR(50) DEFAULT 'GENERAL';

-- 9. Announcements: unit targeting, priority, and expiration
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS unit_id UUID REFERENCES nss_units(unit_id) ON DELETE SET NULL;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
