-- V21__lock_compromised_admin_and_revoke_sessions.sql
-- Remediate committed credentials (P0.3):
-- 1. Safely replace only the specific users status domain CHECK constraint with one allowing 'LOCKED'.
-- 2. Add must_change_password flag for controlled credential provisioning.
-- 3. Lock confirmed compromised administrator identities without altering emails or user relationships.
-- 4. Invalidate all active persistent sessions for compromised identities.

-- 1. Safely locate and replace only the specific users status domain constraint
DO $$
DECLARE
    v_conname text;
BEGIN
    FOR v_conname IN (
        SELECT conname 
        FROM pg_constraint 
        WHERE conrelid = 'users'::regclass 
          AND contype = 'c' 
          AND (
              conname = 'users_status_check'
              OR (
                  pg_get_constraintdef(oid) LIKE '%ACTIVE%' 
                  AND pg_get_constraintdef(oid) LIKE '%INACTIVE%' 
                  AND pg_get_constraintdef(oid) LIKE '%SUSPENDED%'
              )
          )
    ) LOOP
        EXECUTE 'ALTER TABLE users DROP CONSTRAINT ' || quote_ident(v_conname);
    END LOOP;

    -- Add the updated domain check constraint allowing 'LOCKED'
    ALTER TABLE users ADD CONSTRAINT users_status_check 
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'LOCKED'));
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
