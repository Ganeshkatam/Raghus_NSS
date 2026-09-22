# Backend Implementation

## Stack

Java 21, Spring Boot, Spring Security (Stateless JWT / Session), Spring Web, Validation, Spring Data JPA, Flyway, PostgreSQL, Redis and Actuator.

## Module boundaries

identity, users, roles, units, volunteers, events, registrations, attendance, service-hours, announcements, notifications, achievements, certificates, reports, documents, audit and administration.

Each module should separate controllers, DTOs, application services, domain rules and persistence concerns.

## API prefix

All public application endpoints use /api/v1.

Planned resource groups:

- /api/v1/auth
- /api/v1/users
- /api/v1/roles
- /api/v1/units
- /api/v1/volunteers
- /api/v1/events
- /api/v1/events/{id}/registrations
- /api/v1/attendance
- /api/v1/service-hours
- /api/v1/announcements
- /api/v1/notifications
- /api/v1/achievements
- /api/v1/certificates
- /api/v1/activity-reports
- /api/v1/reports
- /api/v1/documents
- /api/v1/admin

## Transactional invariants

- UNIQUE(event_id, volunteer_id) for registration.
- Prevent duplicate attendance per session and volunteer.
- Event capacity checked transactionally.
- Registration deadline enforced by server time.
- QR credentials are short-lived and never authoritative by themselves.
- Service hours originate from verified participation or authorized adjustments.
- Administrative corrections create audit entries.

## Testing

Unit tests cover domain rules. Integration tests use PostgreSQL/Testcontainers. API tests cover authorization and validation. End-to-end tests cover critical user journeys.
