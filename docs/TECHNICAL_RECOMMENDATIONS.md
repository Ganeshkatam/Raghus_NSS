# Technical Evaluation and Architectural Recommendations

This document captures the architectural evaluation, identified technical gaps, and concrete recommendations for the Raghus NSS Management Platform.

## 1. Architectural Strengths

* **Pragmatic Modular Monolith**: Opting for a single modular monolith rather than microservices aligns with operational scale, minimizes latency, eliminates distributed transaction overhead, and keeps deployments straightforward.
* **Ledger-Based Accounting**: Modeling volunteer service hours as an append-only ledger (`service_hour_entries`) rather than a mutable counter on user profiles guarantees auditable history, prevents race-condition overwrites, and supports historical recalculation.
* **Database-Enforced Invariants**: Critical business rules (capacity limits, duplicate registration prevention, unique attendance sessions) are enforced at the database layer through relational constraints and serializable/locking transactions.
* **Separation of Analytical Workloads**: Offloading heavy aggregations and report generation to a dedicated Python/Pandas service protects the transactional backend from CPU/memory spikes during batch exports.

---

## 2. Identified Technical Gaps

### 2.1 Database Schema Migration Management
* **Current State**: The repository relies on raw SQL scripts under `database/init/001_schema.sql` executed only during initial container volume creation, paired with Hibernate `ddl-auto: validate`.
* **Risk**: As the data model expands to 21+ tables, manual SQL execution fails across different developer machines, staging, and CI/CD pipelines. There is no automated rollback, drift detection, or repeatability.
* **Recommendation**: Introduce **Flyway** within the Spring Boot backend (`org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql`). All migrations must reside in `backend/src/main/resources/db/migration` using timestamped or versioned naming (`V1__...sql`).

### 2.2 Security and Identity Foundation
* **Current State**: `backend/build.gradle` lacks Spring Security dependencies.
* **Risk**: Phase 2 (Identity & RBAC) cannot proceed without establishing standard security filters, password hashing, and token/session validation. Implementing custom authentication outside vetted frameworks introduces vulnerability risks.
* **Recommendation**:
  * Add `spring-boot-starter-security`.
  * Employ BCrypt or Argon2 password hashing.
  * Use stateless JWT authentication with short-lived access tokens and Redis-backed refresh tokens, or secure HTTP-only session cookies.
  * Enforce role and permission checks at the controller/service boundary via `@PreAuthorize`.

### 2.3 Local Container Orchestration Completeness
* **Current State**: `docker-compose.yml` provisions only `postgres`, `redis`, and `backend`.
* **Risk**: Full-system local testing requires manually starting Vite and Python services separately.
* **Recommendation**:
  * Add a `frontend` service to `docker-compose.yml` with hot-reloading for development and multi-stage Nginx builds for staging.
  * Add an `analytics` service with FastAPI or Flask to expose endpoints for the Python reporting module.
  * Define explicit health checks and startup dependency ordering for all services.

### 2.4 Frontend Client Architecture
* **Current State**: Minimal entry point in `frontend/src/main.tsx` without routing, HTTP clients, or state handling.
* **Risk**: Feature modules implemented without standardized client conventions risk fragmented API handling, duplicated token refresh logic, and inconsistent error states.
* **Recommendation**:
  * Standardize on React Router for route hierarchy and authentication guards.
  * Implement a centralized HTTP client (Axios or Fetch wrapper) with request/response interceptors to handle `Authorization` headers and standardized API error parsing.
  * Establish a clear separation between server state (TanStack Query or custom hooks) and local UI state.

---

## 3. Technical Decision Records (TDRs)

### TDR-001: Automated Database Migrations with Flyway
* **Context**: Schema integrity is paramount across 21 interconnected entities.
* **Decision**: Adopt Flyway inside Spring Boot to govern all schema mutations automatically on application startup.
* **Consequences**: No manual SQL execution in production. All schema changes are source-controlled, reviewed, and immutable once applied.

### TDR-002: Stateless Token Authentication with Refresh Rotation
* **Context**: Multi-device access (desktop portal, mobile web) requires scalable authentication without sticky sessions.
* **Decision**: Issue short-lived JWT access tokens (15 minutes) and store rotatable refresh tokens in Redis with user revocation support.
* **Consequences**: Fast authorization checks without hitting the database for every request, with centralized revocation via Redis.

### TDR-003: Full-Stack Docker Compose Environment
* **Context**: Developers need to run and verify the entire platform with a single command.
* **Decision**: Expand `docker-compose.yml` to include `postgres`, `redis`, `backend`, `frontend`, and `analytics`.
* **Consequences**: Standardized developer onboarding, reliable smoke tests in CI, and uniform networking.

---

## 4. Phase-Wise Execution Roadmap

### Immediate Next Steps (Foundation Hardening & Phase 2)
1. **Migration Tooling**: Add Flyway dependencies and migrate `001_schema.sql` to `V1__init_schema.sql`.
2. **Identity Schema (V2)**: Author `V2__identity_and_rbac.sql` creating `users`, `roles`, `permissions`, and `user_roles`.
3. **Security Integration**: Configure Spring Security, JWT filters, authentication endpoints (`/api/v1/auth/login`, `/api/v1/auth/refresh`), and user service.
4. **Compose Enhancement**: Add `frontend` and `analytics` to `docker-compose.yml`.
5. **Frontend Core**: Configure React Router, HTTP interceptor, and Auth Provider.
