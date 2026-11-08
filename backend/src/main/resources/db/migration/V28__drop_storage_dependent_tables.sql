-- V28__drop_storage_dependent_tables.sql
-- Architecture Hardening: Drop storage-dependent and unused recognition/document tables.
-- Platform is 100% relational: volunteers, units, events, registrations, attendance, service hours, announcements, and notifications.
-- Eliminates requirements for external cloud object storage (AWS S3, Supabase Storage), binary storage costs, and multipart uploads.

DROP TABLE IF EXISTS certificates CASCADE;
DROP TABLE IF EXISTS achievements CASCADE;
DROP TABLE IF EXISTS activity_reports CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
DROP TABLE IF EXISTS event_media CASCADE;
DROP TABLE IF EXISTS activity_documents CASCADE;
