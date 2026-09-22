# Software Requirements Specification

## Purpose

The NSS Management Application centralizes college NSS operations: volunteer records, NSS units, events, registrations, attendance, verified service hours, reports, documents, announcements, achievements, certificates and administration.

## Users and roles

| Role | Scope |
|---|---|
| Volunteer | Own profile, eligible events, registrations, attendance status and service history |
| Student Leader | Delegated event and operational assistance |
| Programme Officer | Assigned-unit volunteers, events, attendance, reports and service records |
| Faculty Coordinator | Authorized cross-unit coordination and review |
| Administrator | System-wide users, roles, units, configuration and audit |

Authorization is server-side and scope-aware. Frontend visibility is never treated as authorization.

## Functional requirements

1. Authenticate and securely manage user sessions.
2. Manage college structure, departments, academic batches and NSS units.
3. Create, approve and maintain volunteer records.
4. Maintain historical NSS unit memberships.
5. Create and manage event lifecycles.
6. Allow eligible volunteers to register for events.
7. Prevent duplicate registrations and enforce capacity transactionally.
8. Open controlled attendance sessions.
9. Support authenticated QR-based or coordinator attendance verification.
10. Prevent duplicate attendance.
11. Allow controlled attendance correction with a mandatory reason and audit trail.
12. Maintain an auditable service-hour ledger.
13. Generate activity reports.
14. Manage authorized documents and photographs.
15. Publish announcements and notifications.
16. Track achievements and certificates.
17. Provide dashboards, search, filtering and exports.
18. Record security-sensitive and administrative audit events.

## Core workflows

### Volunteer onboarding

Account/profile creation → eligibility validation → coordinator approval → NSS unit assignment → active volunteer.

### Event participation

Draft event → publish → registration → registration close/capacity → attendance session → verified attendance → service-hour ledger → activity report → completion.

### Attendance correction

Authorized actor → select record → provide reason → preserve original state → transactional correction → audit → service-hour adjustment if applicable.

## Non-functional requirements

- PostgreSQL is the authoritative transactional store.
- Critical operations use transactions and database constraints.
- APIs target p95 latency below 500 ms under the defined baseline workload, excluding large exports/downloads.
- Production data has encrypted backups and tested restoration.
- Logs, health checks, request IDs and metrics support operations.
- Personal data is minimized and access-controlled.
- Application should follow WCAG-aligned accessibility practices.
- Dependencies and containers are scanned in CI.

## Global error codes

AUTHENTICATION_REQUIRED, FORBIDDEN, VALIDATION_ERROR, RESOURCE_NOT_FOUND, CONFLICT, DUPLICATE_REGISTRATION, REGISTRATION_CLOSED, EVENT_CAPACITY_REACHED, ATTENDANCE_SESSION_EXPIRED, DUPLICATE_ATTENDANCE, REPORT_NOT_READY, RATE_LIMITED, INTERNAL_ERROR.

## Acceptance principles

Every protected operation must verify authentication, authorization and resource scope. Duplicate submissions must not create duplicate institutional records. Race-sensitive rules must be enforced by the database and transactions, not only by UI logic.
