-- V24__allow_auto_absent_attendance_method.sql
-- Rigorous safety check and constraint verification for attendance_records.check_in_method:
-- 1. Verify that attendance_records.status constraint is intact (strictly 'PRESENT', 'ABSENT', 'EXCUSED').
-- 2. Inspect all check constraints on attendance_records using pg_constraint and pg_attribute column attribution.
-- 3. Replace legacy 2-value check_in_method check ('QR', 'MANUAL_COORDINATOR') with chk_attendance_records_check_in_method
--    allowing exactly ('QR', 'MANUAL_COORDINATOR', 'AUTO_ABSENT') to support automated session closeout.
-- 4. Fail closed with RAISE EXCEPTION if any ambiguous, multi-column, or unexpected check constraint is detected.

DO $$
DECLARE
    v_status_found BOOLEAN := false;
    v_method_found BOOLEAN := false;
    r RECORD;
BEGIN
    -- 1. Inspect all check constraints on attendance_records
    FOR r IN (
        SELECT 
            c.conname, 
            pg_get_constraintdef(c.oid) AS def,
            ARRAY(
                SELECT a.attname::text
                FROM pg_attribute a
                WHERE a.attrelid = c.conrelid
                  AND a.attnum = ANY(c.conkey)
                ORDER BY a.attnum
            ) AS cols
        FROM pg_constraint c
        WHERE c.conrelid = 'attendance_records'::regclass
          AND c.contype = 'c'
    ) LOOP
        -- Check if this is the status constraint: strictly binds to [status] and checks PRESENT, ABSENT, EXCUSED
        IF r.cols = ARRAY['status'] AND
           r.def ~* 'PRESENT' AND r.def ~* 'ABSENT' AND r.def ~* 'EXCUSED' AND NOT (r.def ~* 'AUTO_ABSENT') THEN
            v_status_found := true;

        -- Check if check_in_method constraint is already migrated with exact 3 allowed values
        ELSIF r.cols = ARRAY['check_in_method'] AND r.conname = 'chk_attendance_records_check_in_method' AND
              r.def ~* 'QR' AND r.def ~* 'MANUAL_COORDINATOR' AND r.def ~* 'AUTO_ABSENT' THEN
            v_method_found := true;

        -- Check if this is a legacy check_in_method constraint (exact match on strictly check_in_method column)
        ELSIF r.cols = ARRAY['check_in_method'] AND
              r.def ~* 'QR' AND r.def ~* 'MANUAL_COORDINATOR' AND NOT (r.def ~* 'AUTO_ABSENT') THEN
            EXECUTE 'ALTER TABLE attendance_records DROP CONSTRAINT ' || quote_ident(r.conname);
            EXECUTE 'ALTER TABLE attendance_records ADD CONSTRAINT chk_attendance_records_check_in_method CHECK (check_in_method IN (''QR'', ''MANUAL_COORDINATOR'', ''AUTO_ABSENT''))';
            v_method_found := true;

        ELSE
            RAISE EXCEPTION 'Unexpected CHECK constraint found on attendance_records: % (cols: %, def: %). Aborting migration.', 
                r.conname, r.cols, r.def;
        END IF;
    END LOOP;

    -- 2. Verify that status constraint is present
    IF NOT v_status_found THEN
        RAISE EXCEPTION 'Invariant violation: valid status check constraint was not found on attendance_records.';
    END IF;

    -- 3. Verify that check_in_method constraint was established
    IF NOT v_method_found THEN
        RAISE EXCEPTION 'Invariant violation: valid check_in_method constraint was not established on attendance_records.';
    END IF;
END $$;

