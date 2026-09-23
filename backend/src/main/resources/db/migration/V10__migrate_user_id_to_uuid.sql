-- V10__migrate_user_id_to_uuid.sql
-- Migrate users.user_id from BIGINT to random UUID

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Add temporary UUID columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS new_user_id UUID DEFAULT gen_random_uuid();
UPDATE users SET new_user_id = gen_random_uuid() WHERE new_user_id IS NULL;
ALTER TABLE users ALTER COLUMN new_user_id SET NOT NULL;

ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS new_user_id UUID;
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_actor_user_id UUID;
ALTER TABLE volunteers ADD COLUMN IF NOT EXISTS new_user_id UUID;
ALTER TABLE nss_units ADD COLUMN IF NOT EXISTS new_officer_id UUID;
ALTER TABLE events ADD COLUMN IF NOT EXISTS new_created_by UUID;
ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS new_opened_by UUID;
ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS new_corrected_by UUID;
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS new_approved_by UUID;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS new_uploaded_by UUID;
ALTER TABLE activity_reports ADD COLUMN IF NOT EXISTS new_submitted_by UUID;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS new_issued_by UUID;
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS new_created_by UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS new_user_id UUID;

-- 2. Populate UUID columns from users table
UPDATE user_roles ur SET new_user_id = u.new_user_id FROM users u WHERE ur.user_id = u.user_id;
UPDATE audit_logs al SET new_actor_user_id = u.new_user_id FROM users u WHERE al.actor_user_id = u.user_id;
UPDATE volunteers v SET new_user_id = u.new_user_id FROM users u WHERE v.user_id = u.user_id;
UPDATE nss_units nu SET new_officer_id = u.new_user_id FROM users u WHERE nu.officer_id = u.user_id;
UPDATE events e SET new_created_by = u.new_user_id FROM users u WHERE e.created_by = u.user_id;
UPDATE attendance_sessions s SET new_opened_by = u.new_user_id FROM users u WHERE s.opened_by = u.user_id;
UPDATE attendance_corrections c SET new_corrected_by = u.new_user_id FROM users u WHERE c.corrected_by = u.user_id;
UPDATE service_hour_entries sh SET new_approved_by = u.new_user_id FROM users u WHERE sh.approved_by = u.user_id;
UPDATE documents d SET new_uploaded_by = u.new_user_id FROM users u WHERE d.uploaded_by = u.user_id;
UPDATE activity_reports r SET new_submitted_by = u.new_user_id FROM users u WHERE r.submitted_by = u.user_id;
UPDATE certificates cert SET new_issued_by = u.new_user_id FROM users u WHERE cert.issued_by = u.user_id;
UPDATE announcements a SET new_created_by = u.new_user_id FROM users u WHERE a.created_by = u.user_id;
UPDATE notifications n SET new_user_id = u.new_user_id FROM users u WHERE n.user_id = u.user_id;

-- 3. Drop all foreign key constraints referencing users(user_id)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT tc.table_schema, tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND ccu.table_name = 'users'
          AND ccu.column_name = 'user_id'
    ) LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', r.table_schema, r.table_name, r.constraint_name);
    END LOOP;
END $$;

-- 4. Drop old primary key on users
DO $$
DECLARE
    pk_name TEXT;
BEGIN
    SELECT constraint_name INTO pk_name
    FROM information_schema.table_constraints
    WHERE table_name = 'users' AND constraint_type = 'PRIMARY KEY';
    IF pk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', pk_name);
    END IF;
END $$;

-- 5. Drop old columns and rename new UUID columns

-- users
ALTER TABLE users DROP COLUMN user_id CASCADE;
ALTER TABLE users RENAME COLUMN new_user_id TO user_id;
ALTER TABLE users ADD PRIMARY KEY (user_id);

-- user_roles
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS uq_user_roles;
DROP INDEX IF EXISTS idx_user_roles_user;
ALTER TABLE user_roles DROP COLUMN user_id CASCADE;
ALTER TABLE user_roles RENAME COLUMN new_user_id TO user_id;
ALTER TABLE user_roles ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles UNIQUE (user_id, role_id);
CREATE INDEX idx_user_roles_user ON user_roles(user_id);

-- audit_logs
DROP INDEX IF EXISTS idx_audit_logs_actor;
ALTER TABLE audit_logs DROP COLUMN actor_user_id CASCADE;
ALTER TABLE audit_logs RENAME COLUMN new_actor_user_id TO actor_user_id;
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users(user_id) ON DELETE SET NULL;
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id);

-- volunteers
ALTER TABLE volunteers DROP COLUMN user_id CASCADE;
ALTER TABLE volunteers RENAME COLUMN new_user_id TO user_id;
ALTER TABLE volunteers ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE volunteers ADD CONSTRAINT uq_volunteers_user_id UNIQUE (user_id);
ALTER TABLE volunteers ADD CONSTRAINT fk_volunteers_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- nss_units
DROP INDEX IF EXISTS idx_nss_units_officer;
ALTER TABLE nss_units DROP COLUMN officer_id CASCADE;
ALTER TABLE nss_units RENAME COLUMN new_officer_id TO officer_id;
ALTER TABLE nss_units ADD CONSTRAINT fk_nss_units_officer FOREIGN KEY (officer_id) REFERENCES users(user_id) ON DELETE SET NULL;
CREATE INDEX idx_nss_units_officer ON nss_units(officer_id);

-- events
ALTER TABLE events DROP COLUMN created_by CASCADE;
ALTER TABLE events RENAME COLUMN new_created_by TO created_by;
ALTER TABLE events ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE events ADD CONSTRAINT fk_events_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- attendance_sessions
ALTER TABLE attendance_sessions DROP COLUMN opened_by CASCADE;
ALTER TABLE attendance_sessions RENAME COLUMN new_opened_by TO opened_by;
ALTER TABLE attendance_sessions ALTER COLUMN opened_by SET NOT NULL;
ALTER TABLE attendance_sessions ADD CONSTRAINT fk_attendance_sessions_opened_by FOREIGN KEY (opened_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- attendance_corrections
ALTER TABLE attendance_corrections DROP COLUMN corrected_by CASCADE;
ALTER TABLE attendance_corrections RENAME COLUMN new_corrected_by TO corrected_by;
ALTER TABLE attendance_corrections ALTER COLUMN corrected_by SET NOT NULL;
ALTER TABLE attendance_corrections ADD CONSTRAINT fk_attendance_corrections_corrected_by FOREIGN KEY (corrected_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- service_hour_entries
ALTER TABLE service_hour_entries DROP COLUMN approved_by CASCADE;
ALTER TABLE service_hour_entries RENAME COLUMN new_approved_by TO approved_by;
ALTER TABLE service_hour_entries ADD CONSTRAINT fk_service_hour_approved_by FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL;

-- documents
ALTER TABLE documents DROP COLUMN uploaded_by CASCADE;
ALTER TABLE documents RENAME COLUMN new_uploaded_by TO uploaded_by;
ALTER TABLE documents ADD CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL;

-- activity_reports
ALTER TABLE activity_reports DROP COLUMN submitted_by CASCADE;
ALTER TABLE activity_reports RENAME COLUMN new_submitted_by TO submitted_by;
ALTER TABLE activity_reports ALTER COLUMN submitted_by SET NOT NULL;
ALTER TABLE activity_reports ADD CONSTRAINT fk_activity_reports_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- certificates
ALTER TABLE certificates DROP COLUMN issued_by CASCADE;
ALTER TABLE certificates RENAME COLUMN new_issued_by TO issued_by;
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_issued_by FOREIGN KEY (issued_by) REFERENCES users(user_id) ON DELETE SET NULL;

-- announcements
ALTER TABLE announcements DROP COLUMN created_by CASCADE;
ALTER TABLE announcements RENAME COLUMN new_created_by TO created_by;
ALTER TABLE announcements ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT;

-- notifications
DROP INDEX IF EXISTS idx_notifications_user_read;
ALTER TABLE notifications DROP COLUMN user_id CASCADE;
ALTER TABLE notifications RENAME COLUMN new_user_id TO user_id;
ALTER TABLE notifications ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);
