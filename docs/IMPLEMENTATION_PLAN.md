# Implementation Plan & Roadmap Status

## Completed & Production-Hardened (Current Scope)

### Phase 0 — Baseline (Completed)
- Requirements, roles, workflows, API conventions, and data model frozen and documented.

### Phase 1 — Foundation (Completed)
- Multi-tier architecture: Spring Boot 3 backend, React 19 + TypeScript frontend, PostgreSQL 17, Redis 8.
- Multi-stage Dockerfiles and container orchestration via Docker Compose.
- Flyway database migration pipeline.
- Production environment configurations and health/readiness endpoints.

### Phase 2 — Identity & RBAC (Completed)
- Stateless JWT authentication with Redis token revocation.
- BCrypt password hashing.
- Role-based access control with `@PreAuthorize` across Admin, Programme Officer, and Volunteer tiers.
- `/api/v1/auth/login`, `/api/v1/auth/refresh`, and `/api/v1/auth/me` endpoints.

### Phase 3 — Volunteers & Units (Completed)
- NSS units, volunteer profiles, unit memberships, membership history, and scope-aware queries.

### Phase 4 — Events & Registration (Completed)
- Event lifecycle management (Draft, Published, Open, In Progress, Completed, Cancelled).
- Capacity management, registration deadlines, and duplicate prevention.

### Phase 5 — Attendance & QR Verification (Completed)
- Attendance sessions with rolling cryptographic QR verification tokens.
- Self check-in via mobile scan and supervisor manual check-in.
- Supervisor corrections with mandatory change rationale.

### Phase 6 — Service Hours (Completed)
- Append-oriented service hour ledger linked to verified participation.
- Volunteer claim submission, supervisor review queue, and milestone progress tracking.

### Phase 7 — Communication Engine (Completed)
- Audience-scoped announcements (college-wide or unit-specific).
- Notification engine with per-user unread tracking.

### Phase 8 — Reporting & Institutional Analytics (Existing baseline)
- Spring Boot currently provides institutional metrics and CSV exports for Volunteers, Events, and Service Hours.
- The Python `analytics/` directory is currently an offline utility, not a deployed FastAPI service.

### Phase 8A — Analytics Microservice Extraction (Planned)
- Add a private FastAPI service under `analytics/` with health probes, internal service authentication, validated report-job APIs, and bounded asynchronous execution.
- Provision a dedicated PostgreSQL read-only role and reviewed reporting views; do not reuse the application's unrestricted database credentials.
- Integrate Spring Boot through a typed internal client while retaining Spring Boot as the public authentication, authorization, and unit-scope boundary.
- Add private durable artifact storage, report retention, observability, contract tests, security tests, and Render staging deployment.
- Migrate report types incrementally after output parity and authorization tests; preserve existing CSV endpoints as a rollback path.
- Detailed acceptance criteria: [Analytics Microservice Architecture & Operations](ANALYTICS_MICROSERVICE.md).

### Phase 10 — Production Hardening (Completed)
- Hikari connection pooling, Flyway safety controls, and graceful shutdown.
- Strict CORS configuration and JWT secret length validation.
- Non-root container security (`nssuser:nssgroup`) and JVM container ergonomics.
- Nginx reverse proxy with security headers, Gzip compression, and asset caching.
- Render.yaml blueprint configured for Supabase PostgreSQL and Upstash Redis.

---

## Future Roadmap (Marked as Future Scope)

The following modules are catalogued as future enhancements beyond the current core production baseline:

### Phase 9A — Special Camps (Future)
- 7-day rural immersion camp management and allocation.
- Village adoption tracking and multi-session camp service hour logging.

### Phase 11 — Advanced Administration (Future)
- Detailed administrative audit log query console.
- In-depth system health diagnostics and runtime cache management.

## Out of Scope (Deferred to External Third-Party Systems)

To eliminate cloud object storage dependencies (AWS S3, Supabase Storage), multipart file uploads, and binary hosting costs, the following modules are formally deferred:
- **Digital Certificates**: Generation and cryptographic verification deferred to external academic transcript/credential systems. Milestone tracking (120/240 hours) remains fully operational via relational service-hour ledgers.
- **Achievements & Recognition**: Award nomination workflows and citations deferred to external institutional channels.
- **Document & Media Storage**: Binary storage of circulars, photos, and compliance attachments deferred to institutional cloud drives.
