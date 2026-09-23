-- V8__update_admin_password_hash.sql
-- Ensure production administrator password hash is updated to valid BCrypt hash
UPDATE users 
SET password_hash = '$2a$12$UtbYOLduwv0yY.jmXIW1a.cUcRGQCyYwQOzMxgOn73TFNYDefz3DW' 
WHERE email = 'admin@raghunss.edu';
