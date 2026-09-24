-- V11__migrate_all_primary_keys_to_uuid.sql
-- Comprehensive migration of all remaining primary keys and referencing foreign keys to random UUIDs

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Add temporary UUID columns for all PKs
ALTER TABLE roles ADD COLUMN IF NOT EXISTS new_role_id UUID DEFAULT gen_random_uuid();
UPDATE roles SET new_role_id = gen_random_uuid() WHERE new_role_id IS NULL;
ALTER TABLE roles ALTER COLUMN new_role_id SET NOT NULL;

ALTER TABLE permissions ADD COLUMN IF NOT EXISTS new_permission_id UUID DEFAULT gen_random_uuid();
UPDATE permissions SET new_permission_id = gen_random_uuid() WHERE new_permission_id IS NULL;
ALTER TABLE permissions ALTER COLUMN new_permission_id SET NOT NULL;

ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS new_user_role_id UUID DEFAULT gen_random_uuid();
UPDATE user_roles SET new_user_role_id = gen_random_uuid() WHERE new_user_role_id IS NULL;
ALTER TABLE user_roles ALTER COLUMN new_user_role_id SET NOT NULL;

ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS new_audit_id UUID DEFAULT gen_random_uuid();
UPDATE audit_logs SET new_audit_id = gen_random_uuid() WHERE new_audit_id IS NULL;
ALTER TABLE audit_logs ALTER COLUMN new_audit_id SET NOT NULL;

ALTER TABLE volunteers ADD COLUMN IF NOT EXISTS new_volunteer_id UUID DEFAULT gen_random_uuid();
UPDATE volunteers SET new_volunteer_id = gen_random_uuid() WHERE new_volunteer_id IS NULL;
ALTER TABLE volunteers ALTER COLUMN new_volunteer_id SET NOT NULL;

ALTER TABLE nss_units ADD COLUMN IF NOT EXISTS new_unit_id UUID DEFAULT gen_random_uuid();
UPDATE nss_units SET new_unit_id = gen_random_uuid() WHERE new_unit_id IS NULL;
ALTER TABLE nss_units ALTER COLUMN new_unit_id SET NOT NULL;

ALTER TABLE unit_memberships ADD COLUMN IF NOT EXISTS new_membership_id UUID DEFAULT gen_random_uuid();
UPDATE unit_memberships SET new_membership_id = gen_random_uuid() WHERE new_membership_id IS NULL;
ALTER TABLE unit_memberships ALTER COLUMN new_membership_id SET NOT NULL;

ALTER TABLE events ADD COLUMN IF NOT EXISTS new_event_id UUID DEFAULT gen_random_uuid();
UPDATE events SET new_event_id = gen_random_uuid() WHERE new_event_id IS NULL;
ALTER TABLE events ALTER COLUMN new_event_id SET NOT NULL;

ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS new_registration_id UUID DEFAULT gen_random_uuid();
UPDATE event_registrations SET new_registration_id = gen_random_uuid() WHERE new_registration_id IS NULL;
ALTER TABLE event_registrations ALTER COLUMN new_registration_id SET NOT NULL;

ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS new_session_id UUID DEFAULT gen_random_uuid();
UPDATE attendance_sessions SET new_session_id = gen_random_uuid() WHERE new_session_id IS NULL;
ALTER TABLE attendance_sessions ALTER COLUMN new_session_id SET NOT NULL;

ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS new_attendance_id UUID DEFAULT gen_random_uuid();
UPDATE attendance_records SET new_attendance_id = gen_random_uuid() WHERE new_attendance_id IS NULL;
ALTER TABLE attendance_records ALTER COLUMN new_attendance_id SET NOT NULL;

ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS new_correction_id UUID DEFAULT gen_random_uuid();
UPDATE attendance_corrections SET new_correction_id = gen_random_uuid() WHERE new_correction_id IS NULL;
ALTER TABLE attendance_corrections ALTER COLUMN new_correction_id SET NOT NULL;

ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS new_entry_id UUID DEFAULT gen_random_uuid();
UPDATE service_hour_entries SET new_entry_id = gen_random_uuid() WHERE new_entry_id IS NULL;
ALTER TABLE service_hour_entries ALTER COLUMN new_entry_id SET NOT NULL;

ALTER TABLE documents ADD COLUMN IF NOT EXISTS new_document_id UUID DEFAULT gen_random_uuid();
UPDATE documents SET new_document_id = gen_random_uuid() WHERE new_document_id IS NULL;
ALTER TABLE documents ALTER COLUMN new_document_id SET NOT NULL;

ALTER TABLE activity_reports ADD COLUMN IF NOT EXISTS new_report_id UUID DEFAULT gen_random_uuid();
UPDATE activity_reports SET new_report_id = gen_random_uuid() WHERE new_report_id IS NULL;
ALTER TABLE activity_reports ALTER COLUMN new_report_id SET NOT NULL;

ALTER TABLE achievements ADD COLUMN IF NOT EXISTS new_achievement_id UUID DEFAULT gen_random_uuid();
UPDATE achievements SET new_achievement_id = gen_random_uuid() WHERE new_achievement_id IS NULL;
ALTER TABLE achievements ALTER COLUMN new_achievement_id SET NOT NULL;

ALTER TABLE certificates ADD COLUMN IF NOT EXISTS new_certificate_id UUID DEFAULT gen_random_uuid();
UPDATE certificates SET new_certificate_id = gen_random_uuid() WHERE new_certificate_id IS NULL;
ALTER TABLE certificates ALTER COLUMN new_certificate_id SET NOT NULL;

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS new_announcement_id UUID DEFAULT gen_random_uuid();
UPDATE announcements SET new_announcement_id = gen_random_uuid() WHERE new_announcement_id IS NULL;
ALTER TABLE announcements ALTER COLUMN new_announcement_id SET NOT NULL;

ALTER TABLE notifications ADD COLUMN IF NOT EXISTS new_notification_id UUID DEFAULT gen_random_uuid();
UPDATE notifications SET new_notification_id = gen_random_uuid() WHERE new_notification_id IS NULL;
ALTER TABLE notifications ALTER COLUMN new_notification_id SET NOT NULL;


-- 2. Add temporary UUID columns for referencing FKs
ALTER TABLE role_permissions ADD COLUMN IF NOT EXISTS new_role_id UUID;
ALTER TABLE role_permissions ADD COLUMN IF NOT EXISTS new_permission_id UUID;
ALTER TABLE user_roles ADD COLUMN IF NOT EXISTS new_role_id UUID;

ALTER TABLE unit_memberships ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;
ALTER TABLE unit_memberships ADD COLUMN IF NOT EXISTS new_unit_id UUID;

ALTER TABLE events ADD COLUMN IF NOT EXISTS new_unit_id UUID;

ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS new_event_id UUID;
ALTER TABLE event_registrations ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;

ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS new_event_id UUID;

ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS new_session_id UUID;
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;

ALTER TABLE attendance_corrections ADD COLUMN IF NOT EXISTS new_attendance_id UUID;

ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS new_event_id UUID;
ALTER TABLE service_hour_entries ADD COLUMN IF NOT EXISTS new_attendance_id UUID;

ALTER TABLE activity_reports ADD COLUMN IF NOT EXISTS new_event_id UUID;
ALTER TABLE activity_reports ADD COLUMN IF NOT EXISTS new_document_id UUID;

ALTER TABLE achievements ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;
ALTER TABLE achievements ADD COLUMN IF NOT EXISTS new_document_id UUID;

ALTER TABLE certificates ADD COLUMN IF NOT EXISTS new_volunteer_id UUID;
ALTER TABLE certificates ADD COLUMN IF NOT EXISTS new_document_id UUID;

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS new_unit_id UUID;


-- 3. Populate new UUID foreign keys from parent tables
UPDATE role_permissions rp SET new_role_id = r.new_role_id FROM roles r WHERE rp.role_id = r.role_id;
UPDATE role_permissions rp SET new_permission_id = p.new_permission_id FROM permissions p WHERE rp.permission_id = p.permission_id;
UPDATE user_roles ur SET new_role_id = r.new_role_id FROM roles r WHERE ur.role_id = r.role_id;

UPDATE unit_memberships um SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE um.volunteer_id = v.volunteer_id;
UPDATE unit_memberships um SET new_unit_id = u.new_unit_id FROM nss_units u WHERE um.unit_id = u.unit_id;

UPDATE events e SET new_unit_id = u.new_unit_id FROM nss_units u WHERE e.unit_id = u.unit_id;

UPDATE event_registrations er SET new_event_id = e.new_event_id FROM events e WHERE er.event_id = e.event_id;
UPDATE event_registrations er SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE er.volunteer_id = v.volunteer_id;

UPDATE attendance_sessions ass SET new_event_id = e.new_event_id FROM events e WHERE ass.event_id = e.event_id;

UPDATE attendance_records ar SET new_session_id = ass.new_session_id FROM attendance_sessions ass WHERE ar.session_id = ass.session_id;
UPDATE attendance_records ar SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE ar.volunteer_id = v.volunteer_id;

UPDATE attendance_corrections ac SET new_attendance_id = ar.new_attendance_id FROM attendance_records ar WHERE ac.attendance_id = ar.attendance_id;

UPDATE service_hour_entries she SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE she.volunteer_id = v.volunteer_id;
UPDATE service_hour_entries she SET new_event_id = e.new_event_id FROM events e WHERE she.event_id = e.event_id;
UPDATE service_hour_entries she SET new_attendance_id = ar.new_attendance_id FROM attendance_records ar WHERE she.attendance_id = ar.attendance_id;

UPDATE activity_reports rep SET new_event_id = e.new_event_id FROM events e WHERE rep.event_id = e.event_id;
UPDATE activity_reports rep SET new_document_id = d.new_document_id FROM documents d WHERE rep.document_id = d.document_id;

UPDATE achievements ach SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE ach.volunteer_id = v.volunteer_id;
UPDATE achievements ach SET new_document_id = d.new_document_id FROM documents d WHERE ach.document_id = d.document_id;

UPDATE certificates cert SET new_volunteer_id = v.new_volunteer_id FROM volunteers v WHERE cert.volunteer_id = v.volunteer_id;
UPDATE certificates cert SET new_document_id = d.new_document_id FROM documents d WHERE cert.document_id = d.document_id;

UPDATE announcements ann SET new_unit_id = u.new_unit_id FROM nss_units u WHERE ann.unit_id = u.unit_id;


-- 4. Drop all existing foreign key constraints across the public schema
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT tc.table_schema, tc.table_name, tc.constraint_name
        FROM information_schema.table_constraints tc
        WHERE tc.constraint_type = 'FOREIGN KEY'
          AND tc.table_schema = 'public'
    ) LOOP
        EXECUTE format('ALTER TABLE %I.%I DROP CONSTRAINT %I', r.table_schema, r.table_name, r.constraint_name);
    END LOOP;
END $$;


-- 5. Drop old primary keys on all tables except app_metadata and users (which was already migrated)
DO $$
DECLARE
    t TEXT;
    pk_name TEXT;
BEGIN
    FOR t IN SELECT unnest(ARRAY[
        'roles', 'permissions', 'role_permissions', 'user_roles', 'audit_logs',
        'volunteers', 'nss_units', 'unit_memberships', 'events', 'event_registrations',
        'attendance_sessions', 'attendance_records', 'attendance_corrections',
        'service_hour_entries', 'documents', 'activity_reports', 'achievements',
        'certificates', 'announcements', 'notifications'
    ]) LOOP
        SELECT constraint_name INTO pk_name
        FROM information_schema.table_constraints
        WHERE table_schema = 'public' AND table_name = t AND constraint_type = 'PRIMARY KEY';
        IF pk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, pk_name);
        END IF;
    END LOOP;
END $$;


-- 6. Drop old columns and rename new UUID columns, set constraints and recreate indexes

-- roles
ALTER TABLE roles DROP COLUMN role_id CASCADE;
ALTER TABLE roles RENAME COLUMN new_role_id TO role_id;
ALTER TABLE roles ADD PRIMARY KEY (role_id);

-- permissions
ALTER TABLE permissions DROP COLUMN permission_id CASCADE;
ALTER TABLE permissions RENAME COLUMN new_permission_id TO permission_id;
ALTER TABLE permissions ADD PRIMARY KEY (permission_id);

-- role_permissions
ALTER TABLE role_permissions DROP COLUMN role_id CASCADE;
ALTER TABLE role_permissions DROP COLUMN permission_id CASCADE;
ALTER TABLE role_permissions RENAME COLUMN new_role_id TO role_id;
ALTER TABLE role_permissions RENAME COLUMN new_permission_id TO permission_id;
ALTER TABLE role_permissions ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE role_permissions ALTER COLUMN permission_id SET NOT NULL;
ALTER TABLE role_permissions ADD PRIMARY KEY (role_id, permission_id);
ALTER TABLE role_permissions ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE;
ALTER TABLE role_permissions ADD CONSTRAINT fk_role_permissions_perm FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE;

-- user_roles
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS uq_user_roles;
DROP INDEX IF EXISTS idx_user_roles_role;
ALTER TABLE user_roles DROP COLUMN user_role_id CASCADE;
ALTER TABLE user_roles DROP COLUMN role_id CASCADE;
ALTER TABLE user_roles RENAME COLUMN new_user_role_id TO user_role_id;
ALTER TABLE user_roles RENAME COLUMN new_role_id TO role_id;
ALTER TABLE user_roles ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE user_roles ADD PRIMARY KEY (user_role_id);
ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE;
ALTER TABLE user_roles ADD CONSTRAINT uq_user_roles UNIQUE (user_id, role_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

-- audit_logs
ALTER TABLE audit_logs DROP COLUMN audit_id CASCADE;
ALTER TABLE audit_logs RENAME COLUMN new_audit_id TO audit_id;
ALTER TABLE audit_logs ADD PRIMARY KEY (audit_id);
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users(user_id) ON DELETE SET NULL;

-- volunteers
ALTER TABLE volunteers DROP CONSTRAINT IF EXISTS uq_volunteers_user_id;
ALTER TABLE volunteers DROP COLUMN volunteer_id CASCADE;
ALTER TABLE volunteers RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE volunteers ADD PRIMARY KEY (volunteer_id);
ALTER TABLE volunteers ADD CONSTRAINT uq_volunteers_user_id UNIQUE (user_id);
ALTER TABLE volunteers ADD CONSTRAINT fk_volunteers_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;

-- nss_units
ALTER TABLE nss_units DROP COLUMN unit_id CASCADE;
ALTER TABLE nss_units RENAME COLUMN new_unit_id TO unit_id;
ALTER TABLE nss_units ADD PRIMARY KEY (unit_id);
ALTER TABLE nss_units ADD CONSTRAINT fk_nss_units_officer FOREIGN KEY (officer_id) REFERENCES users(user_id) ON DELETE SET NULL;

-- unit_memberships
ALTER TABLE unit_memberships DROP CONSTRAINT IF EXISTS uq_active_unit_membership;
DROP INDEX IF EXISTS idx_unit_memberships_volunteer;
DROP INDEX IF EXISTS idx_unit_memberships_unit;
ALTER TABLE unit_memberships DROP COLUMN membership_id CASCADE;
ALTER TABLE unit_memberships DROP COLUMN volunteer_id CASCADE;
ALTER TABLE unit_memberships DROP COLUMN unit_id CASCADE;
ALTER TABLE unit_memberships RENAME COLUMN new_membership_id TO membership_id;
ALTER TABLE unit_memberships RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE unit_memberships RENAME COLUMN new_unit_id TO unit_id;
ALTER TABLE unit_memberships ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE unit_memberships ALTER COLUMN unit_id SET NOT NULL;
ALTER TABLE unit_memberships ADD PRIMARY KEY (membership_id);
ALTER TABLE unit_memberships ADD CONSTRAINT fk_unit_memberships_vol FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE unit_memberships ADD CONSTRAINT fk_unit_memberships_unit FOREIGN KEY (unit_id) REFERENCES nss_units(unit_id) ON DELETE CASCADE;
ALTER TABLE unit_memberships ADD CONSTRAINT uq_active_unit_membership UNIQUE (volunteer_id, unit_id, is_active);
CREATE INDEX idx_unit_memberships_volunteer ON unit_memberships(volunteer_id);
CREATE INDEX idx_unit_memberships_unit ON unit_memberships(unit_id);

-- events
DROP INDEX IF EXISTS idx_events_unit;
ALTER TABLE events DROP COLUMN event_id CASCADE;
ALTER TABLE events DROP COLUMN unit_id CASCADE;
ALTER TABLE events RENAME COLUMN new_event_id TO event_id;
ALTER TABLE events RENAME COLUMN new_unit_id TO unit_id;
ALTER TABLE events ALTER COLUMN unit_id SET NOT NULL;
ALTER TABLE events ADD PRIMARY KEY (event_id);
ALTER TABLE events ADD CONSTRAINT fk_events_unit FOREIGN KEY (unit_id) REFERENCES nss_units(unit_id) ON DELETE RESTRICT;
ALTER TABLE events ADD CONSTRAINT fk_events_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT;
CREATE INDEX idx_events_unit ON events(unit_id);

-- event_registrations
ALTER TABLE event_registrations DROP CONSTRAINT IF EXISTS uq_event_volunteer_registration;
DROP INDEX IF EXISTS idx_event_reg_event;
DROP INDEX IF EXISTS idx_event_reg_volunteer;
ALTER TABLE event_registrations DROP COLUMN registration_id CASCADE;
ALTER TABLE event_registrations DROP COLUMN event_id CASCADE;
ALTER TABLE event_registrations DROP COLUMN volunteer_id CASCADE;
ALTER TABLE event_registrations RENAME COLUMN new_registration_id TO registration_id;
ALTER TABLE event_registrations RENAME COLUMN new_event_id TO event_id;
ALTER TABLE event_registrations RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE event_registrations ALTER COLUMN event_id SET NOT NULL;
ALTER TABLE event_registrations ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE event_registrations ADD PRIMARY KEY (registration_id);
ALTER TABLE event_registrations ADD CONSTRAINT fk_event_registrations_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE;
ALTER TABLE event_registrations ADD CONSTRAINT fk_event_registrations_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE event_registrations ADD CONSTRAINT uq_event_volunteer_registration UNIQUE (event_id, volunteer_id);
CREATE INDEX idx_event_reg_event ON event_registrations(event_id);
CREATE INDEX idx_event_reg_volunteer ON event_registrations(volunteer_id);

-- attendance_sessions
DROP INDEX IF EXISTS idx_att_sessions_event;
ALTER TABLE attendance_sessions DROP COLUMN session_id CASCADE;
ALTER TABLE attendance_sessions DROP COLUMN event_id CASCADE;
ALTER TABLE attendance_sessions RENAME COLUMN new_session_id TO session_id;
ALTER TABLE attendance_sessions RENAME COLUMN new_event_id TO event_id;
ALTER TABLE attendance_sessions ALTER COLUMN event_id SET NOT NULL;
ALTER TABLE attendance_sessions ADD PRIMARY KEY (session_id);
ALTER TABLE attendance_sessions ADD CONSTRAINT fk_attendance_sessions_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE;
ALTER TABLE attendance_sessions ADD CONSTRAINT fk_attendance_sessions_opened_by FOREIGN KEY (opened_by) REFERENCES users(user_id) ON DELETE RESTRICT;
CREATE INDEX idx_att_sessions_event ON attendance_sessions(event_id);

-- attendance_records
ALTER TABLE attendance_records DROP CONSTRAINT IF EXISTS uq_session_volunteer_attendance;
DROP INDEX IF EXISTS idx_att_records_session;
DROP INDEX IF EXISTS idx_att_records_volunteer;
ALTER TABLE attendance_records DROP COLUMN attendance_id CASCADE;
ALTER TABLE attendance_records DROP COLUMN session_id CASCADE;
ALTER TABLE attendance_records DROP COLUMN volunteer_id CASCADE;
ALTER TABLE attendance_records RENAME COLUMN new_attendance_id TO attendance_id;
ALTER TABLE attendance_records RENAME COLUMN new_session_id TO session_id;
ALTER TABLE attendance_records RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE attendance_records ALTER COLUMN session_id SET NOT NULL;
ALTER TABLE attendance_records ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE attendance_records ADD PRIMARY KEY (attendance_id);
ALTER TABLE attendance_records ADD CONSTRAINT fk_attendance_records_session FOREIGN KEY (session_id) REFERENCES attendance_sessions(session_id) ON DELETE CASCADE;
ALTER TABLE attendance_records ADD CONSTRAINT fk_attendance_records_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE attendance_records ADD CONSTRAINT uq_session_volunteer_attendance UNIQUE (session_id, volunteer_id);
CREATE INDEX idx_att_records_session ON attendance_records(session_id);
CREATE INDEX idx_att_records_volunteer ON attendance_records(volunteer_id);

-- attendance_corrections
DROP INDEX IF EXISTS idx_att_corrections_record;
ALTER TABLE attendance_corrections DROP COLUMN correction_id CASCADE;
ALTER TABLE attendance_corrections DROP COLUMN attendance_id CASCADE;
ALTER TABLE attendance_corrections RENAME COLUMN new_correction_id TO correction_id;
ALTER TABLE attendance_corrections RENAME COLUMN new_attendance_id TO attendance_id;
ALTER TABLE attendance_corrections ALTER COLUMN attendance_id SET NOT NULL;
ALTER TABLE attendance_corrections ADD PRIMARY KEY (correction_id);
ALTER TABLE attendance_corrections ADD CONSTRAINT fk_attendance_corrections_att FOREIGN KEY (attendance_id) REFERENCES attendance_records(attendance_id) ON DELETE CASCADE;
ALTER TABLE attendance_corrections ADD CONSTRAINT fk_attendance_corrections_corrected_by FOREIGN KEY (corrected_by) REFERENCES users(user_id) ON DELETE RESTRICT;
CREATE INDEX idx_att_corrections_record ON attendance_corrections(attendance_id);

-- service_hour_entries
DROP INDEX IF EXISTS idx_service_hours_volunteer;
DROP INDEX IF EXISTS idx_service_hours_event;
ALTER TABLE service_hour_entries DROP COLUMN entry_id CASCADE;
ALTER TABLE service_hour_entries DROP COLUMN volunteer_id CASCADE;
ALTER TABLE service_hour_entries DROP COLUMN event_id CASCADE;
ALTER TABLE service_hour_entries DROP COLUMN attendance_id CASCADE;
ALTER TABLE service_hour_entries RENAME COLUMN new_entry_id TO entry_id;
ALTER TABLE service_hour_entries RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE service_hour_entries RENAME COLUMN new_event_id TO event_id;
ALTER TABLE service_hour_entries RENAME COLUMN new_attendance_id TO attendance_id;
ALTER TABLE service_hour_entries ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE service_hour_entries ADD PRIMARY KEY (entry_id);
ALTER TABLE service_hour_entries ADD CONSTRAINT fk_service_hour_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE service_hour_entries ADD CONSTRAINT fk_service_hour_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE SET NULL;
ALTER TABLE service_hour_entries ADD CONSTRAINT fk_service_hour_att FOREIGN KEY (attendance_id) REFERENCES attendance_records(attendance_id) ON DELETE SET NULL;
ALTER TABLE service_hour_entries ADD CONSTRAINT fk_service_hour_approved_by FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL;
CREATE INDEX idx_service_hours_volunteer ON service_hour_entries(volunteer_id);
CREATE INDEX idx_service_hours_event ON service_hour_entries(event_id);

-- documents
ALTER TABLE documents DROP COLUMN document_id CASCADE;
ALTER TABLE documents RENAME COLUMN new_document_id TO document_id;
ALTER TABLE documents ADD PRIMARY KEY (document_id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(user_id) ON DELETE SET NULL;

-- activity_reports
DROP INDEX IF EXISTS idx_activity_reports_event;
ALTER TABLE activity_reports DROP COLUMN report_id CASCADE;
ALTER TABLE activity_reports DROP COLUMN event_id CASCADE;
ALTER TABLE activity_reports DROP COLUMN document_id CASCADE;
ALTER TABLE activity_reports RENAME COLUMN new_report_id TO report_id;
ALTER TABLE activity_reports RENAME COLUMN new_event_id TO event_id;
ALTER TABLE activity_reports RENAME COLUMN new_document_id TO document_id;
ALTER TABLE activity_reports ALTER COLUMN event_id SET NOT NULL;
ALTER TABLE activity_reports ADD PRIMARY KEY (report_id);
ALTER TABLE activity_reports ADD CONSTRAINT fk_activity_reports_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE;
ALTER TABLE activity_reports ADD CONSTRAINT fk_activity_reports_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE activity_reports ADD CONSTRAINT fk_activity_reports_doc FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL;
CREATE INDEX idx_activity_reports_event ON activity_reports(event_id);

-- achievements
DROP INDEX IF EXISTS idx_achievements_volunteer;
ALTER TABLE achievements DROP COLUMN achievement_id CASCADE;
ALTER TABLE achievements DROP COLUMN volunteer_id CASCADE;
ALTER TABLE achievements DROP COLUMN document_id CASCADE;
ALTER TABLE achievements RENAME COLUMN new_achievement_id TO achievement_id;
ALTER TABLE achievements RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE achievements RENAME COLUMN new_document_id TO document_id;
ALTER TABLE achievements ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE achievements ADD PRIMARY KEY (achievement_id);
ALTER TABLE achievements ADD CONSTRAINT fk_achievements_vol FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE achievements ADD CONSTRAINT fk_achievements_doc FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL;
CREATE INDEX idx_achievements_volunteer ON achievements(volunteer_id);

-- certificates
DROP INDEX IF EXISTS idx_certificates_volunteer;
DROP INDEX IF EXISTS idx_certificates_number;
ALTER TABLE certificates DROP COLUMN certificate_id CASCADE;
ALTER TABLE certificates DROP COLUMN volunteer_id CASCADE;
ALTER TABLE certificates DROP COLUMN document_id CASCADE;
ALTER TABLE certificates RENAME COLUMN new_certificate_id TO certificate_id;
ALTER TABLE certificates RENAME COLUMN new_volunteer_id TO volunteer_id;
ALTER TABLE certificates RENAME COLUMN new_document_id TO document_id;
ALTER TABLE certificates ALTER COLUMN volunteer_id SET NOT NULL;
ALTER TABLE certificates ADD PRIMARY KEY (certificate_id);
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_vol FOREIGN KEY (volunteer_id) REFERENCES volunteers(volunteer_id) ON DELETE CASCADE;
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_doc FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE SET NULL;
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_issued_by FOREIGN KEY (issued_by) REFERENCES users(user_id) ON DELETE SET NULL;
ALTER TABLE certificates ADD CONSTRAINT uq_certificates_number UNIQUE (certificate_number);
CREATE INDEX idx_certificates_volunteer ON certificates(volunteer_id);
CREATE INDEX idx_certificates_number ON certificates(certificate_number);

-- announcements
DROP INDEX IF EXISTS idx_announcements_unit;
ALTER TABLE announcements DROP COLUMN announcement_id CASCADE;
ALTER TABLE announcements DROP COLUMN unit_id CASCADE;
ALTER TABLE announcements RENAME COLUMN new_announcement_id TO announcement_id;
ALTER TABLE announcements RENAME COLUMN new_unit_id TO unit_id;
ALTER TABLE announcements ADD PRIMARY KEY (announcement_id);
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE RESTRICT;
ALTER TABLE announcements ADD CONSTRAINT fk_announcements_unit FOREIGN KEY (unit_id) REFERENCES nss_units(unit_id) ON DELETE CASCADE;
CREATE INDEX idx_announcements_unit ON announcements(unit_id);

-- notifications
ALTER TABLE notifications DROP COLUMN notification_id CASCADE;
ALTER TABLE notifications RENAME COLUMN new_notification_id TO notification_id;
ALTER TABLE notifications ADD PRIMARY KEY (notification_id);
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE;
