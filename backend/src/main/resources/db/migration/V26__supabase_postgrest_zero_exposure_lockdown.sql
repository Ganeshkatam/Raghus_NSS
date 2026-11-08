-- V26__supabase_postgrest_zero_exposure_lockdown.sql
-- Production Security Hardening: Supabase PostgREST Zero-Direct-Exposure Lockdown
-- 1. Revoke all privileges (SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER)
--    from PostgREST roles ('anon', 'authenticated') on all tables, sequences, and routines in public schema.
-- 2. Enable Row Level Security (RLS) on all public relation tables (excluding flyway_schema_history).
-- 3. With zero RLS policies defined and grants revoked, direct PostgREST HTTP queries are fail-closed.
-- 4. The Spring Boot backend connects as the database owner ('postgres' with rolbypassrls=true),
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

    -- 3. Enable Row Level Security on all application tables in public schema
    FOR r IN (
        SELECT tablename 
        FROM pg_tables 
        WHERE schemaname = 'public' 
          AND tablename NOT IN ('flyway_schema_history')
    ) LOOP
        EXECUTE 'ALTER TABLE public.' || quote_ident(r.tablename) || ' ENABLE ROW LEVEL SECURITY';
    END LOOP;
END $$;
