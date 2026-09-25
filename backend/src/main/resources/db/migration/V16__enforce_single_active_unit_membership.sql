-- V16__enforce_single_active_unit_membership.sql
-- Enforce database-level constraint: A volunteer can belong to AT MOST ONE active NSS Unit at any given time.

-- 1. Deactivate any duplicate active memberships keeping only the latest joined_at
UPDATE unit_memberships um1
SET is_active = false, left_at = CURRENT_TIMESTAMP
WHERE is_active = true
  AND EXISTS (
      SELECT 1 FROM unit_memberships um2
      WHERE um2.volunteer_id = um1.volunteer_id
        AND um2.is_active = true
        AND (um2.joined_at > um1.joined_at OR (um2.joined_at = um1.joined_at AND um2.membership_id > um1.membership_id))
  );

-- 2. Create partial unique index guaranteeing at most one active membership per volunteer across all units
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_unit_membership_per_volunteer
ON unit_memberships (volunteer_id)
WHERE is_active = true;
