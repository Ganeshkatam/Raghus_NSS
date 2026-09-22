# Implementation Plan

## Phase 0 — Baseline

Freeze requirements, roles, workflows, API conventions, error model and data model.

## Phase 1 — Foundation

- Repository structure
- Docker Compose (PostgreSQL, Redis, backend, frontend, analytics)
- PostgreSQL & Redis configuration
- Flyway database migration foundation
- Spring Boot application
- React application shell
- Python analytics service
- CI pipeline
- Environment configuration
- Health/readiness endpoints

## Phase 2 — Identity and RBAC

- Spring Security integration (stateless JWT authentication with Redis token revocation)
- Password hashing (BCrypt / Argon2)
- Database migrations: users, roles, permissions, user_roles
- Account activation/deactivation and server-side RBAC authorization (@PreAuthorize)
- Authentication API endpoints (/api/v1/auth/login, /api/v1/auth/refresh, /api/v1/auth/me)

## Phase 3 — Volunteers and units

NSS units, volunteer profiles, unit memberships, membership history and scope-aware queries.

## Phase 4 — Events and registration

Event lifecycle, registration windows, eligibility, capacity, duplicate-registration constraint and transactional last-seat handling.

## Phase 5 — Attendance

Attendance sessions, short-lived QR credentials, check-in validation, duplicate prevention, corrections and audit records.

## Phase 6 — Service hours

Use an append-oriented ledger linked to verified participation. Avoid relying on a mutable total as the source of truth.

## Phase 7 — Communication

Announcements, in-app notifications and optional asynchronous delivery.

## Phase 8 — Reporting

Attendance, participation, service-hour and unit reports with permission-aware filters and secure exports.

## Phase 9 — Achievements and certificates

Achievement records, eligibility rules, certificate records and controlled document generation.

## Phase 10 — Hardening

Security tests, authorization tests, rate limits, dependency scanning, observability, backup/restore testing and deployment smoke tests.

## Database migration order

1. users
2. roles
3. permissions
4. user_roles
5. nss_units
6. volunteers
7. unit_memberships
8. events
9. event_registrations
10. attendance_sessions
11. attendance_records
12. attendance_corrections
13. service_hour_entries
14. announcements
15. notifications
16. achievements
17. documents
18. certificates
19. activity_reports
20. reports
21. audit_logs

## MVP

Authentication → Volunteers → Units → Events → Registration → QR Attendance → Service Hours → Dashboard.
