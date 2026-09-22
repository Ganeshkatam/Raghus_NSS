# System Architecture

## Architecture style

The first production release uses a modular monolith. A single college does not need microservice operational complexity at the initial scale.

Logical layers:

1. Presentation — React web application.
2. API — HTTP routing, validation, authentication and rate limiting.
3. Application — use cases and orchestration.
4. Domain — NSS business rules and invariants.
5. Persistence — PostgreSQL/JPA data access.
6. Infrastructure — Redis, documents, notifications, jobs, logging and external services.

## Technology

- React + TypeScript + Vite
- Java 21 + Spring Boot
- PostgreSQL
- Redis
- Python + Pandas for analytics/reporting workloads
- Docker
- GitHub Actions
- OpenAPI

## Security boundary

The backend is authoritative. The client cannot decide roles, service-hour totals, timestamps, approval states, registration capacity or attendance validity.

## Deployment

Development uses Docker Compose for PostgreSQL, Redis and the application stack. Production should use managed PostgreSQL/object storage where practical, encrypted secrets, health probes, backups and controlled migrations.

## Observability

Use structured logs, request/correlation IDs, API latency/error metrics, database health, authentication-failure metrics, background-job metrics and audit monitoring.

## Reliability

Critical state transitions are transactional. Database constraints protect uniqueness and referential integrity. Backups are only considered valid after restoration tests.
