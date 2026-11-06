-- V21__lock_compromised_admin_and_revoke_sessions.sql
-- Remediate committed credentials (P0.3):
-- 1. Safely locate and replace only the specific users status domain CHECK constraint with one allowing 'LOCKED'.
-- 2. Add must_change_password flag for controlled credential provisioning.
-- 3. Lock confirmed compromised administrator identities without altering emails or user relationships.
-- 4. Invalidate all active persistent sessions for compromised identities.

-- 1. Safely locate and replace only the specific users status domain constraint
DO $$
DECLARE
    v_target_conname text;
    v_match_count integer;
    v_already_updated boolean := false;
BEGIN
    -- Check if a status check constraint allowing 'LOCKED' already exists
    SELECT EXISTS (
        SELECT 1 
        FROM pg_constraint c
        WHERE c.conrelid = 'users'::regclass 
          AND c.contype = 'c' 
          AND (
              c.conname = 'users_status_check'
              OR pg_get_constraintdef(c.oid) ~* '^\s*CHECK\s*\(\(?\(?status\)?'
          )
          AND pg_get_constraintdef(c.oid) LIKE '%''LOCKED''%'
    ) INTO v_already_updated;

    IF v_already_updated THEN
        RAISE NOTICE 'Status check constraint already includes LOCKED. Skipping constraint replacement.';
    ELSE
        -- Find existing status check constraint that does not yet include LOCKED
        SELECT COUNT(*), MAX(c.conname)
        INTO v_match_count, v_target_conname
        FROM pg_constraint c
        WHERE c.conrelid = 'users'::regclass 
          AND c.contype = 'c' 
          AND (
              c.conname = 'users_status_check'
              OR c.conkey = ARRAY[(SELECT attnum FROM pg_attribute WHERE attrelid = 'users'::regclass AND attname = 'status')]
              OR pg_get_constraintdef(c.oid) ~* '^\s*CHECK\s*\(\(?\(?status\)?'
          )
          AND pg_get_constraintdef(c.oid) NOT LIKE '%''LOCKED''%';

        IF v_match_count = 1 THEN
            EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(v_target_conname);
            ALTER TABLE users ADD CONSTRAINT users_status_check 
                CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED'));
            RAISE NOTICE 'Successfully replaced status check constraint % with users_status_check', v_target_conname;
        ELSIF v_match_count = 0 THEN
            RAISE EXCEPTION 'Status domain check constraint not found on users table';
        ELSE
            RAISE EXCEPTION 'Ambiguous status CHECK constraints found on users table (count=%)', v_match_count;
        END IF;
    END IF;
END $$;

-- 2. Add must_change_password column if not exists
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Lock confirmed compromised administrator accounts without renaming or altering identity
UPDATE users
SET status = 'LOCKED',
    password_hash = '*COMPROMISED_LOCKED*',
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(email) IN ('admin@raghunss.edu', 'katamganesh61@gmail.com');

-- 4. Revoke all active persistent auth_sessions for compromised identities
UPDATE auth_sessions
SET is_active = FALSE,
    revoked_at = CURRENT_TIMESTAMP,
    revoke_reason = 'COMPROMISED_CREDENTIAL_LOCK'
WHERE is_active = TRUE
  AND user_id IN (
      SELECT user_id FROM users 
      WHERE LOWER(email) IN ('admin@raghunss.edu', 'katamganesh61@gmail.com')
  );
