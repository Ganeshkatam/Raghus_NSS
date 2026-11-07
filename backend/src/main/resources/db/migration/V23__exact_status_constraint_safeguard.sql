-- V23__exact_status_constraint_safeguard.sql
-- Rigorous safety check and constraint verification for service_hour_entries:
-- 1. Verify and ensure that the hours check constraint (hours > 0) is present and intact.
-- 2. Verify that the status check constraint enforces strictly ('APPROVED', 'PENDING', 'REJECTED', 'REVOKED').
-- 3. If any legacy status constraint exists (e.g. named 'service_hour_entries_status_check' with 3-value enum),
--    drop strictly that exact verified constraint and replace it with chk_service_hour_entries_status.
-- 4. Fail closed with RAISE EXCEPTION if any ambiguous or unexpected check constraint is detected.

DO $$
DECLARE
    v_hours_found BOOLEAN := false;
    v_status_found BOOLEAN := false;
    r RECORD;
BEGIN
    -- 1. Inspect all check constraints on service_hour_entries
    FOR r IN (
        SELECT conname, pg_get_constraintdef(oid) as def
        FROM pg_constraint
        WHERE conrelid = 'service_hour_entries'::regclass
          AND contype = 'c'
    ) LOOP
        -- Check if this is the hours check constraint
        IF r.conname = 'service_hour_entries_hours_check' OR r.def ~* '^\s*CHECK\s*\(\s*\(?hours\s*>\s*0' THEN
            v_hours_found := true;

        -- Check if this is the target status constraint (exact 4-value domain)
        ELSIF r.conname = 'chk_service_hour_entries_status' AND
              r.def ~* 'APPROVED' AND r.def ~* 'PENDING' AND r.def ~* 'REJECTED' AND r.def ~* 'REVOKED' THEN
            -- Verify it only checks status
            IF r.def ~* 'hours' OR r.def ~* 'volunteer' OR r.def ~* 'event' THEN
                RAISE EXCEPTION 'Constraint % definition (%) contains columns other than status. Aborting migration.', r.conname, r.def;
            END IF;
            v_status_found := true;

        -- Check if this is an un-migrated legacy status constraint (exact match on 3-value enum)
        ELSIF (r.conname = 'service_hour_entries_status_check' OR r.conname = 'chk_service_hour_entries_status') AND
              r.def ~* 'APPROVED' AND r.def ~* 'PENDING' AND r.def ~* 'REJECTED' AND NOT (r.def ~* 'REVOKED') THEN
            -- Verify it ONLY checks status and nothing else (no hours, volunteer, etc.)
            IF r.def ~* 'hours' OR r.def ~* 'volunteer' OR r.def ~* 'event' THEN
                RAISE EXCEPTION 'Ambiguous legacy constraint % (%): contains columns other than status. Aborting migration.', r.conname, r.def;
            END IF;
            -- Drop strictly this verified legacy constraint
            EXECUTE 'ALTER TABLE service_hour_entries DROP CONSTRAINT ' || quote_ident(r.conname);
            -- Add the validated 4-value status check
            EXECUTE 'ALTER TABLE service_hour_entries ADD CONSTRAINT chk_service_hour_entries_status CHECK (status IN (''APPROVED'', ''PENDING'', ''REJECTED'', ''REVOKED''))';
            v_status_found := true;

        ELSE
            -- Any unexpected constraint must cause the migration to fail closed rather than silently drop or ignore
            RAISE EXCEPTION 'Unexpected CHECK constraint found on service_hour_entries: % (%). Aborting migration to prevent silent removal.', r.conname, r.def;
        END IF;
    END LOOP;

    -- 2. Verify that the hours constraint is present
    IF NOT v_hours_found THEN
        RAISE EXCEPTION 'Invariant violation: hours check constraint (hours > 0) is missing on service_hour_entries.';
    END IF;

    -- 3. Verify that the status constraint is present and valid
    IF NOT v_status_found THEN
        RAISE EXCEPTION 'Invariant violation: valid status check constraint was not established on service_hour_entries.';
    END IF;
END $$;
