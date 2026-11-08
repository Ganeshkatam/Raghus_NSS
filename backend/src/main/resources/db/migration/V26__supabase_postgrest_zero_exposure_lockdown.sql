-- V26__supabase_postgrest_zero_exposure_lockdown.sql
-- Production Security Hardening: Supabase PostgREST Zero-Direct-Exposure Lockdown
-- 1. Revoke all privileges (SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER)
--    from PostgREST roles ('anon', 'authenticated') and 'PUBLIC' on all tables, sequences, and routines in public schema.
-- 2. Alter default privileges in schema public so future objects do not grant access to PUBLIC, anon, or authenticated.
-- 3. Configure views in schema public with security_invoker = true (PostgreSQL 15+) to prevent RLS bypass.
-- 4. Enable Row Level Security (RLS) on all public relation tables (excluding flyway_schema_history).
-- 5. Guarantee explicit owner grants for 'postgres' and 'service_role'.
-- 6. With zero RLS policies defined and grants revoked, direct PostgREST HTTP queries are fail-closed.
-- 7. The Spring Boot backend connects as the database owner ('postgres' with rolbypassrls=true),
--    preserving full operational query capabilities and serving public data strictly via sanitized API DTOs.

DO $$
DECLARE
    r RECORD;
BEGIN
    -- 1. Safely revoke table, sequence, and routine privileges from 'anon' if role exists
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon';
        EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM anon';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM anon';
    END IF;

    -- 2. Safely revoke table, sequence, and routine privileges from 'authenticated' if role exists
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM authenticated';
    END IF;

    -- 3. Revoke all table, sequence, and routine privileges from pseudo-role 'PUBLIC' in schema public
    EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC';
    EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC';
    EXECUTE 'REVOKE ALL ON ALL ROUTINES IN SCHEMA public FROM PUBLIC';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC';
    EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON ROUTINES FROM PUBLIC';

    -- 4. Guarantee backend database owner retains full access across public schema
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        EXECUTE 'GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres';
        EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres';
        EXECUTE 'GRANT ALL ON ALL ROUTINES IN SCHEMA public TO postgres';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON ROUTINES FROM PUBLIC, anon, authenticated';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        EXECUTE 'GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role';
        EXECUTE 'GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role';
        EXECUTE 'GRANT ALL ON ALL ROUTINES IN SCHEMA public TO service_role';
    END IF;

    -- 5. Set security_invoker = true on all views in public schema to enforce RLS of invoking user
    FOR r IN (
        SELECT table_name 
        FROM information_schema.views 
        WHERE table_schema = 'public'
    ) LOOP
        BEGIN
            EXECUTE 'ALTER VIEW public.' || quote_ident(r.table_name) || ' SET (security_invoker = true)';
        EXCEPTION WHEN OTHERS THEN
            NULL;
        END;
    END LOOP;

    -- 6. Enable Row Level Security on all application tables in public schema
    FOR r IN (
        SELECT tablename 
        FROM pg_tables 
        WHERE schemaname = 'public' 
          AND tablename NOT IN ('flyway_schema_history')
    ) LOOP
        EXECUTE 'ALTER TABLE public.' || quote_ident(r.tablename) || ' ENABLE ROW LEVEL SECURITY';
    END LOOP;
END $$;
