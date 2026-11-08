-- V25__prevent_duplicate_active_service_hours.sql
-- Invariant: At most one active ('PENDING' or 'APPROVED') service hour entry may exist
-- for any given (volunteer_id, event_id) pair.
-- Terminal or reversible historical statuses ('REVOKED', 'REJECTED') are excluded from
-- this partial index to preserve full audit history.

CREATE UNIQUE INDEX IF NOT EXISTS uq_service_hours_one_active_per_volunteer_event
ON service_hour_entries (volunteer_id, event_id)
WHERE status IN ('PENDING', 'APPROVED') AND event_id IS NOT NULL;

