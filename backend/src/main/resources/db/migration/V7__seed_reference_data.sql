-- V7__seed_reference_data.sql
-- Seed Core Roles, Permissions, and Initial Administrator

INSERT INTO roles (role_id, name, description) VALUES
(1, 'ROLE_ADMIN', 'System Administrator with full institutional privileges'),
(2, 'ROLE_PROGRAMME_OFFICER', 'Programme Officer managing assigned NSS unit and events'),
(3, 'ROLE_FACULTY_COORDINATOR', 'Faculty Coordinator supervising cross-unit operations'),
(4, 'ROLE_STUDENT_LEADER', 'Student Leader assisting in unit and event operations'),
(5, 'ROLE_VOLUNTEER', 'Registered NSS Volunteer')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (permission_id, name, description) VALUES
(1, 'USERS_MANAGE', 'Create, update, deactivate user accounts'),
(2, 'UNITS_MANAGE', 'Create and configure NSS units and officer assignments'),
(3, 'VOLUNTEERS_MANAGE', 'Approve, assign, and manage volunteer records'),
(4, 'EVENTS_MANAGE', 'Create, edit, publish, and complete events'),
(5, 'ATTENDANCE_MANAGE', 'Open attendance sessions, record attendance, and make corrections'),
(6, 'SERVICE_HOURS_MANAGE', 'Approve, adjust, and audit service-hour ledger entries'),
(7, 'REPORTS_VIEW', 'View and export activity, attendance, and service reports')
ON CONFLICT (name) DO NOTHING;

-- Map Admin Permissions (Role 1 has all permissions)
INSERT INTO role_permissions (role_id, permission_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7),
(2, 3), (2, 4), (2, 5), (2, 6), (2, 7),
(3, 4), (3, 5), (3, 7),
(4, 5),
(5, 7)
ON CONFLICT DO NOTHING;

-- Seed Default Admin User
-- Email: admin@raghunss.edu
-- Password: Admin@Password123 (BCrypt hash)
INSERT INTO users (user_id, name, email, password_hash, phone, status) VALUES
(1, 'System Administrator', 'admin@raghunss.edu', '$2a$12$UtbYOLduwv0yY.jmXIW1a.cUcRGQCyYwQOzMxgOn73TFNYDefz3DW', '9876543210', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

-- Assign Admin Role
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1)
ON CONFLICT (user_id, role_id) DO NOTHING;
