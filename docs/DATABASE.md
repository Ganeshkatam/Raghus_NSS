# Database and ER Model

## Core entities

users, roles, permissions, user_roles, nss_units, volunteers, unit_memberships, events, event_registrations, attendance_sessions, attendance_records, attendance_corrections, service_hour_entries, announcements, notifications, achievements, documents, certificates, activity_reports, reports and audit_logs.

## Core relationship chain

USERS → VOLUNTEERS → UNIT_MEMBERSHIPS → NSS_UNITS → EVENTS → EVENT_REGISTRATIONS → ATTENDANCE_SESSIONS → ATTENDANCE_RECORDS → SERVICE_HOUR_ENTRIES

## Integrity rules

- Foreign keys protect references.
- Unique constraints prevent duplicate registration and attendance.
- Index event dates/status, volunteer identifiers, unit IDs and foreign-key columns.
- Use database transactions for race-sensitive operations.
- Preserve historical membership and audit information.
- Service hours are a ledger, not merely a mutable volunteer total.

## Recommended event states

DRAFT → PUBLISHED → OPEN → CLOSED → COMPLETED

Alternative terminal state: CANCELLED. Historical records remain available according to retention policy.

## ER model

The detailed visual ER model is maintained separately as a design artifact; this document records the repository-level data contract and invariants.
