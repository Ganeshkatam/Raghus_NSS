# AGENTS.md

Agent conventions and operational reference for the NSS College Management Application.

---

## Repository Layout

```
REC_NSS/
  backend/        Spring Boot 3.5 / Java 21 API
  frontend/       React 19 / TypeScript / Vite 7 SPA
  docs/           Architecture and design documents
  scripts/        Utility scripts
  database/       Standalone SQL references
  analytics/      Analytics tooling
  docker-compose.yml
  render.yaml     Render deployment config
  vercel.json     Vercel deployment config
```

---

## Backend

**Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA (Hibernate 6), Flyway, PostgreSQL (production), H2 (tests), Redis (token revocation), JWT (access + refresh).

**Package root:** `edu.college.nss`

| Package | Purpose |
|---|---|
| `domain` | JPA entities and domain rules |
| `repository` | Spring Data JPA repositories |
| `service` | Application services (all business logic) |
| `web` | REST controllers and DTOs |
| `security` | JWT filter, UserDetailsService, SecurityConfig |
| `exception` | Custom exceptions and global error handler |

### Commands

Run from `backend/`:

```powershell
# Compile
.\gradlew.bat classes

# Full test suite
.\gradlew.bat test

# Single test class
.\gradlew.bat test --tests edu.college.nss.service.MultiUnitEventIntegrationTest

# Single test method
.\gradlew.bat test --tests edu.college.nss.service.MultiUnitEventIntegrationTest.methodName

# Build JAR
.\gradlew.bat bootJar
```

### Test environment

- Flyway is **disabled** in tests (`spring.flyway.enabled: false`).
- Schema is created by `ddl-auto: create-drop` against H2 in PostgreSQL compatibility mode.
- Redis is declared but not exercised in integration tests.
- Test `application.yml` is at `src/test/resources/application.yml`. Do not add `show-sql` or `org.hibernate.SQL: DEBUG` permanently; those are diagnostic only.

### Database migrations

Flyway migrations live in `src/main/resources/db/migration/`. Current highest version: **V19**.

- Always increment the version number sequentially.
- Never edit a committed migration. Create a new version instead.
- Migrations must be PostgreSQL-compatible. H2 `MODE=PostgreSQL` covers most syntax but not all; test critical DDL against PostgreSQL directly.
- `key` is a reserved word in H2; avoid it as a column name (use `setting_key`, `config_key`, etc.).

### JPA / Hibernate conventions

- `FetchType.LAZY` on all `@ManyToMany` and `@OneToMany` associations.
- Use `@EntityGraph` on repository query methods to fetch required associations in a single join query for single-entity lookups (`findById`, `findByIdWithLock`).
- For **paged queries** (`Page<T>`), do **not** include collection associations (`@ManyToMany`) in the `@EntityGraph`. Doing so triggers Hibernate `HHH90003004` in-memory pagination. Instead, annotate the collection field with `@BatchSize(size = 25)` so Hibernate issues one secondary SELECT per batch of IDs after the paginated main query returns.
- Always supply a `countQuery` on `@Query` methods that return `Page<T>`.

### Authorization rules

- `ADMIN` and `FACULTY_COORDINATOR` can read and manage all units.
- `PROGRAMME_OFFICER` manages only the unit for which they are the assigned officer.
- A PO can **see** published events belonging to a unit they participate in but cannot manage them.
- `VOLUNTEER` can only register for and interact with events that their active unit is eligible for.
- Authorization is enforced server-side in service methods. Frontend role checks are UX only.

### Event scope model

Events have one of three scopes stored in `events.event_scope`:

| Scope | Participating units |
|---|---|
| `UNIT` | Organizing unit only |
| `MULTI_UNIT` | Organizing unit + explicitly listed units (minimum 2 total) |
| `COLLEGE_WIDE` | All units automatically |

The organizing unit is `events.unit_id`. Participating units are in the `event_units` join table. `MULTI_UNIT` requires at least 2 participating units at both create and update time. This invariant is enforced in `EventService`.

---

## Frontend

**Stack:** React 19, TypeScript, Vite 7, React Router 7, Vanilla CSS.

**Commands** — run from `frontend/`:

```powershell
# Type-check and production build (run before committing)
npm run build

# Development server
npm run dev
```

The build script runs `tsc --noEmit` before Vite, so TypeScript errors will fail the build.

---

## Commit conventions

Use Conventional Commits:

```
type(scope): short description

Types: feat, fix, perf, refactor, test, docs, chore
Scope: events, volunteers, units, auth, attendance, service-hours, reports, frontend, etc.
```

Examples:
```
feat(events): add college-wide and multi-unit event scope
fix(events): enforce multi-unit update invariant, fix po3 test principal, and set lazy fetch
perf(events): fix in-memory pagination by removing participatingUnits from search EntityGraph
```

**Commit after every verified change.** Do not batch unrelated changes into a single commit.

Never use emojis in commit messages, code, or comments.

---

## Git workflow

- Single branch: `main`. All changes go directly to `main`.
- Always run `git status` and `git diff` before committing to confirm the diff is clean and scoped.
- Push immediately after committing. Use PowerShell semicolon syntax:
  ```powershell
  git add . ; git commit -m "..." ; git push origin main
  ```
- Never force-push. Never rewrite history.

---

## Deployment

| Component | Platform | Trigger |
|---|---|---|
| Backend | Render (Docker) | Push to `main` via `render.yaml` |
| Frontend | Vercel | Push to `main` via `vercel.json` |

- Backend API base: `https://api-nss.onrender.com`
- Frontend: `https://recnss.vercel.app`
- Health endpoint: `GET /actuator/health`

Render free-tier instances spin down after inactivity; the first request after spin-down will be slow.

---

## Key architectural decisions

1. **Modular monolith.** All backend logic is in one deployable. Module boundaries are enforced by package structure and service layering, not network calls.
2. **Append-only service-hour ledger.** Hours are never mutated directly. Credits derive from verified attendance; corrections create audited debit/credit entries.
3. **Server-authoritative timestamps.** The server sets all `createdAt`, `updatedAt`, and event transition timestamps. Clients never supply these.
4. **Pagination safety.** Paged queries must use database-level `LIMIT`/`OFFSET`. Never mix collection-fetching `@EntityGraph` with `Pageable` — use `@BatchSize` instead.
5. **Single active membership.** A volunteer can only be an active member of one NSS unit at a time, enforced by a partial unique index in `V16`.
