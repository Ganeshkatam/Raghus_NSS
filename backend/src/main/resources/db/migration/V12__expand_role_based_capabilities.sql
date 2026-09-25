-- V12__expand_role_based_capabilities.sql
-- Define complete role-based capabilities for all existing NSS modules

-- Insert expanded capabilities
INSERT INTO permissions (permission_id, name, description) VALUES
(gen_random_uuid(), 'USERS_VIEW', 'View user directory and profiles'),
(gen_random_uuid(), 'UNITS_VIEW', 'View NSS units, rosters, and officer assignments'),
(gen_random_uuid(), 'VOLUNTEERS_VIEW', 'View volunteer directory and profiles'),
(gen_random_uuid(), 'EVENTS_VIEW', 'Browse published events, schedules, and details'),
(gen_random_uuid(), 'EVENTS_REGISTER', 'Register and cancel registrations for events'),
(gen_random_uuid(), 'ATTENDANCE_VIEW', 'View attendance rosters and check-in history'),
(gen_random_uuid(), 'ATTENDANCE_CHECKIN', 'Scan QR code or enter session code to check in'),
(gen_random_uuid(), 'SERVICE_HOURS_VIEW', 'View service hour ledger records and totals'),
(gen_random_uuid(), 'SERVICE_HOURS_LOG', 'Submit and credit manual service hours for activities'),
(gen_random_uuid(), 'ANNOUNCEMENTS_VIEW', 'Read institutional and unit announcements'),
(gen_random_uuid(), 'ANNOUNCEMENTS_MANAGE', 'Publish, update, and archive announcements'),
(gen_random_uuid(), 'REPORTS_EXPORT', 'Export volunteer, attendance, and service records')
ON CONFLICT (name) DO NOTHING;

-- Map capabilities to ADMIN (all permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('ADMIN', 'ROLE_ADMIN')
ON CONFLICT DO NOTHING;

-- Map capabilities to FACULTY_COORDINATOR
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('FACULTY_COORDINATOR', 'ROLE_FACULTY_COORDINATOR')
AND p.name IN (
    'USERS_VIEW',
    'UNITS_VIEW', 'UNITS_MANAGE',
    'VOLUNTEERS_VIEW', 'VOLUNTEERS_MANAGE',
    'EVENTS_VIEW', 'EVENTS_MANAGE', 'EVENTS_REGISTER',
    'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE',
    'SERVICE_HOURS_VIEW', 'SERVICE_HOURS_MANAGE', 'SERVICE_HOURS_LOG',
    'ANNOUNCEMENTS_VIEW', 'ANNOUNCEMENTS_MANAGE',
    'REPORTS_VIEW', 'REPORTS_EXPORT'
)
ON CONFLICT DO NOTHING;

-- Map capabilities to PROGRAMME_OFFICER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('PROGRAMME_OFFICER', 'ROLE_PROGRAMME_OFFICER')
AND p.name IN (
    'UNITS_VIEW', 'UNITS_MANAGE',
    'VOLUNTEERS_VIEW', 'VOLUNTEERS_MANAGE',
    'EVENTS_VIEW', 'EVENTS_MANAGE', 'EVENTS_REGISTER',
    'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE',
    'SERVICE_HOURS_VIEW', 'SERVICE_HOURS_MANAGE', 'SERVICE_HOURS_LOG',
    'ANNOUNCEMENTS_VIEW', 'ANNOUNCEMENTS_MANAGE',
    'REPORTS_VIEW', 'REPORTS_EXPORT'
)
ON CONFLICT DO NOTHING;

-- Map capabilities to STUDENT_LEADER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('STUDENT_LEADER', 'ROLE_STUDENT_LEADER')
AND p.name IN (
    'UNITS_VIEW',
    'VOLUNTEERS_VIEW',
    'EVENTS_VIEW', 'EVENTS_REGISTER',
    'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE', 'ATTENDANCE_CHECKIN',
    'SERVICE_HOURS_VIEW', 'SERVICE_HOURS_LOG',
    'ANNOUNCEMENTS_VIEW',
    'REPORTS_VIEW'
)
ON CONFLICT DO NOTHING;

-- Map capabilities to VOLUNTEER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
CROSS JOIN permissions p
WHERE r.name IN ('VOLUNTEER', 'ROLE_VOLUNTEER')
AND p.name IN (
    'UNITS_VIEW',
    'VOLUNTEERS_VIEW',
    'EVENTS_VIEW', 'EVENTS_REGISTER',
    'ATTENDANCE_VIEW', 'ATTENDANCE_CHECKIN',
    'SERVICE_HOURS_VIEW',
    'ANNOUNCEMENTS_VIEW',
    'REPORTS_VIEW'
)
ON CONFLICT DO NOTHING;
