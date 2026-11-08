-- V27__postgrest_privilege_and_view_hardening.sql
-- Follow-up to immutable V26. Keep all post-deployment changes in a new versioned migration.
-- This migration is intentionally fail-closed: any failed privilege or view operation aborts it.

DO $$
DECLARE
    r RECORD;
BEGIN
    -- Revoke existing privileges inherited by PostgREST roles and PUBLIC.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM authenticated';
    END IF;

    EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC';
    EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC';
    EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM PUBLIC';

    -- Harden future objects created by the production migration owner.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON ROUTINES FROM PUBLIC';

        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM anon';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON ROUTINES FROM anon';
        END IF;

        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM authenticated';
            EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON ROUTINES FROM authenticated';
        END IF;

        -- Preserve backend owner access explicitly.
        EXECUTE 'GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres';
        EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres';
        EXECUTE 'GRANT ALL ON ALL ROUTINES IN SCHEMA public TO postgres';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        EXECUTE 'GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role';
        EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role';
        EXECUTE 'GRANT ALL ON ALL ROUTINES IN SCHEMA public TO service_role';
    END IF;

    -- PostgreSQL 15+ supports security_invoker on views. Do not swallow errors:
    -- an unsupported or unsecurable view must stop migration rather than pass silently.
    FOR r IN (
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = 'public'
    ) LOOP
        EXECUTE 'ALTER VIEW public.' || quote_ident(r.table_name) ||
                ' SET (security_invoker = true)';
    END LOOP;
END $$;
