-- V22__attendance_and_service_hour_ledger_invariants.sql
-- Enforce database-level invariants for attendance corrections and service hour ledger:
-- 1. At most one PENDING correction per attendance record
-- 2. Expand status domain check constraint on service_hour_entries to include 'REVOKED'
-- 3. At most one active (PENDING or APPROVED) manual event claim per volunteer and event
-- 4. At most one APPROVED credit entry per attendance record

-- 1. Enforce at most one PENDING correction per attendance record
CREATE UNIQUE INDEX IF NOT EXISTS uq_att_corrections_one_pending_per_record
ON attendance_corrections (attendance_id)
WHERE status = 'PENDING';

-- 2. Safely expand status domain check constraint on service_hour_entries to allow 'REVOKED'
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'service_hour_entries'::regclass
          AND contype = 'c'
          AND (
              conname = 'service_hour_entries_status_check'
              OR pg_get_constraintdef(oid) ILIKE '%status%'
          )
    ) LOOP
        EXECUTE 'ALTER TABLE service_hour_entries DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE service_hour_entries
    ADD CONSTRAINT chk_service_hour_entries_status
    CHECK (status IN ('APPROVED', 'PENDING', 'REJECTED', 'REVOKED'));

-- 3. Enforce at most one active (PENDING or APPROVED) manual event claim per volunteer and event
CREATE UNIQUE INDEX IF NOT EXISTS uq_service_hours_one_active_event_claim
ON service_hour_entries (volunteer_id, event_id)
WHERE status IN ('PENDING', 'APPROVED') AND attendance_id IS NULL;

-- 4. Enforce at most one APPROVED credit entry per attendance record
CREATE UNIQUE INDEX IF NOT EXISTS uq_service_hours_one_approved_per_attendance
ON service_hour_entries (attendance_id)
WHERE status = 'APPROVED';

