-- V9__update_admin_credentials.sql
-- Update System Administrator email, name, and password to personalized credentials
UPDATE users 
SET email = 'katamganesh61@gmail.com',
    name = 'Katam Ganesh Reddy',
    password_hash = '$2a$12$Z/SuEJmsOwPVTEerl99ZCe6.nMd9E4EvzV9d6QqvdZkGoAN7SbYMe' 
WHERE user_id = 1 OR email = 'admin@raghunss.edu';
