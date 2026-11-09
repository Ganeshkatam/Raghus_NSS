-- V29__fix_unit_membership_uniqueness_and_session_invariants.sql
-- 1. Drop legacy composite uniqueness constraint that prevents repeated historical transfers between units
ALTER TABLE unit_memberships DROP CONSTRAINT IF EXISTS uq_active_unit_membership;

-- 2. Guarantee that at most one ACTIVE membership exists per volunteer across all NSS units
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_unit_membership_per_volunteer
ON unit_memberships (volunteer_id)
WHERE is_active = true;

-- 3. Guarantee that at most one OPEN attendance session exists per event
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_open_session_per_event
ON attendance_sessions (event_id)
WHERE status = 'OPEN';

