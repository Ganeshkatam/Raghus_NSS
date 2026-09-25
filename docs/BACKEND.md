# Backend Implementation & Architecture Specification

## NSS College Management Application
**Document Version:** 1.0  
**Runtime:** Java 21 LTS  
**Framework:** Spring Boot 3.4.x with Spring Security 6  
**Database:** PostgreSQL 17 (Supabase) with Flyway Database Migrations  

---

## 1. Backend Objectives

The backend represents the authoritative application and security boundary of the NSS platform. It owns identity, authentication, role-based capabilities, data invariants, attendance verification, service-hour calculations, report generation, and compliance audit records:

- Expose a stable, versioned REST API (`/api/v1`) for web and mobile clients.
- Enforce authentication and granular capability authorization strictly server-side.
- Maintain ACID transactional consistency for registrations, attendance, and service-hour ledgers.
- Provide fast, paginated, and indexed access to institutional records.
- Guarantee that internal database, JDBC, SQL, or stack-trace errors are completely filtered before reaching clients.
- Deliver reliable automated backups, Actuator health probes, and operational telemetry.

---

## 2. Technology Stack & Implementation Guidance

| Component | Technology | Implementation Guidance |
|---|---|---|
| **Runtime** | Java 21 LTS | Use modern Java features: records for immutable DTOs, pattern matching, virtual threads where applicable. |
| **Framework** | Spring Boot 3.4.x | Standardized annotations: `@RestController`, `@Service`, `@Repository`, constructor-based dependency injection. |
| **Security** | Spring Security 6 | Stateless JWT authentication filter, explicit `ProviderManager`, method-level `@PreAuthorize("hasAuthority(...)")`. |
| **Database** | PostgreSQL 17 | Relational integrity, foreign key constraints, composite unique indexes, UUID primary keys. |
| **Persistence** | Spring Data JPA / Hibernate | Eager loading for primary relations, explicit DTO projection to eliminate N+1 queries. |
| **Migrations** | Flyway | Strictly versioned migrations (`V1` through `V12`) under version control; clean-disabled in production. |
| **Connection Pool** | HikariCP | Configured with validation timeouts, minimum idle connections, and leak detection thresholds. |
| **Caching** | Redis | Stateless token blacklisting, rate-limiting, and temporary attendance session verification. |
| **Observability** | Spring Boot Actuator + SLF4J / Logback | Health and readiness probes (`/actuator/health`), structured logging, correlation IDs. |

---

## 3. Project Directory Structure

```
backend/
├── src/
│   ├── main/
│   │   ├── java/edu/college/nss/
│   │   │   ├── config/              # Application, Security, CORS, and JPA configuration
│   │   │   ├── domain/              # JPA Entities (User, Role, Volunteer, NssUnit, Event, Attendance, etc.)
│   │   │   ├── exception/           # Custom domain exceptions (AppException, ResourceNotFoundException)
│   │   │   ├── repository/         # Spring Data JPA repositories with custom JPQL queries
│   │   │   ├── security/           # CustomUserDetails, JwtTokenProvider, JwtAuthenticationFilter
│   │   │   ├── service/            # Application business services (AuthService, VolunteerService, EventService, etc.)
│   │   │   └── web/                # REST Controllers, GlobalExceptionHandler, and DTO records
│   │   └── resources/
│   │       ├── db/migration/       # Flyway migration scripts (V1 through V12)
│   │       ├── application.yml     # Production application configuration and error suppression
│   │       └── application-test.yml# In-memory H2 / test database configuration
│   └── test/                       # Comprehensive JUnit 5 and Spring Boot MockMvc integration tests
├── build.gradle                    # Gradle build definition with dependencies and test runners
└── gradlew.bat                     # Gradle wrapper executable
```

---

## 4. Domain Modules and Responsibilities

| Module | Core Responsibilities |
|---|---|
| **Auth & Security** | Login, token issuance, token refresh, current user context (`/auth/me`), BCrypt encryption, token revocation. |
| **Users & Roles** | User accounts, status (`ACTIVE`, `INACTIVE`, `SUSPENDED`), role and capability mapping. |
| **Volunteers** | Volunteer profile creation, enrollment verification, college ID, department mapping, academic year. |
| **NSS Units** | Unit provisioning, Programme Officer assignments, member lists, active status tracking. |
| **Events** | Event scheduling, venue, credit hours, capacity, registration window, and state machine lifecycle. |
| **Registrations** | Volunteer self-registration, capacity checking under concurrency, cancellation, participant lists. |
| **Attendance** | Controlled sessions, dynamic QR verification, manual check-ins, supervisor corrections, and audit history. |
| **Service Hours** | Authoritative service-hour ledger, event credit derivation, manual claims review, and progress metrics. |
| **Announcements** | Unit-wide or college-wide bulletins, audience targeting, priority ranking, and expiration. |
| **Notifications** | Per-user alert inbox, event reminders, read/unread state tracking. |
| **Achievements** | Honor nominations, awards, leadership citations, and institutional recognition records. |
| **Certificates** | Eligibility verification (120 hours + Special Camp), certificate number generation, and status tracking. |
| **Reports** | Aggregated KPIs, CSV exports (volunteers, attendance, hours), and university compliance summaries. |
| **Audit Logs** | Tamper-resistant recording of actor, target entity, action, timestamp, and client metadata. |

---

## 5. Relational Schema & Table Definitions

```
Table: users
├── user_id (UUID, PK)
├── name (VARCHAR(150), NOT NULL)
├── email (VARCHAR(255), UNIQUE, NOT NULL)
├── password_hash (VARCHAR(255), NOT NULL)
├── phone (VARCHAR(20))
├── status (VARCHAR(20), NOT NULL, DEFAULT 'ACTIVE')
├── created_at (TIMESTAMPTZ, NOT NULL)
└── updated_at (TIMESTAMPTZ, NOT NULL)

Table: roles
├── role_id (UUID, PK)
├── name (VARCHAR(50), UNIQUE, NOT NULL)
├── description (VARCHAR(255))
└── created_at (TIMESTAMPTZ, NOT NULL)

Table: permissions
├── permission_id (UUID, PK)
├── name (VARCHAR(100), UNIQUE, NOT NULL)
└── description (VARCHAR(255))

Table: user_roles (UUID PK, user_id FK, role_id FK)
Table: role_permissions (role_id FK, permission_id FK, PK(role_id, permission_id))
Table: nss_units (unit_id UUID PK, unit_name, unit_number UNIQUE, officer_id FK)
Table: volunteers (volunteer_id UUID PK, user_id FK UNIQUE, college_id UNIQUE, department, year_of_study)
Table: unit_memberships (membership_id UUID PK, volunteer_id FK, unit_id FK, joined_at, is_active)
Table: events (event_id UUID PK, unit_id FK, created_by FK, title, event_type, start_at, end_at, capacity, status)
Table: event_registrations (registration_id UUID PK, event_id FK, volunteer_id FK, status, UNIQUE(event_id, volunteer_id))
Table: attendance_sessions (session_id UUID PK, event_id FK, opened_by FK, starts_at, expires_at, status, token_hash)
Table: attendance_records (attendance_id UUID PK, session_id FK, volunteer_id FK, checked_in_at, status)
Table: attendance_corrections (correction_id UUID PK, attendance_id FK, requested_by FK, reviewed_by FK, reason, status)
Table: service_hour_entries (entry_id UUID PK, volunteer_id FK, event_id FK, attendance_id FK, hours, status, approved_by FK)
Table: announcements (announcement_id UUID PK, created_by FK, unit_id FK, title, content, published_at, expires_at)
Table: notifications (notification_id UUID PK, user_id FK, title, message, is_read, created_at)
Table: achievements (achievement_id UUID PK, volunteer_id FK, title, description, status, awarded_at)
Table: certificates (certificate_id UUID PK, volunteer_id FK, certificate_number UNIQUE, status, issued_at)
Table: audit_logs (audit_id UUID PK, actor_user_id FK, action, entity_type, entity_id, created_at)
```

---

## 6. Authentication & Capability Authorization

### 6.1 Authentication Workflow
1. Client issues `POST /api/v1/auth/login` with email and password.
2. `AuthService` delegates authentication to an explicit `ProviderManager` holding a `DaoAuthenticationProvider` wired with `CustomUserDetailsService` and `PasswordEncoder`.
3. `CustomUserDetails` loads user entity, assigns roles (`ADMIN`, `PROGRAMME_OFFICER`, etc.), and extracts all mapped permissions (`UNITS_MANAGE`, `EVENTS_MANAGE`, etc.) as granted authorities.
4. If authentication succeeds and account is active, a signed JWT access token (15-min expiration) and refresh token (7-day expiration) are generated.
5. Response returns `AuthResponse` containing tokens and `UserDto` populated with roles and granular permissions.

### 6.2 Granular Endpoint Authorization
Endpoints enforce capability checks using `@PreAuthorize`:
```java
// Unit Creation - Authorized for UNITS_MANAGE capability or authorized roles
@PostMapping("/units")
@PreAuthorize("hasAuthority('UNITS_MANAGE') or hasAnyRole('ADMIN', 'FACULTY_COORDINATOR', 'PROGRAMME_OFFICER')")
public ResponseEntity<UnitResponse> createUnit(@Valid @RequestBody UnitRequest request) { ... }

// Attendance Session - Authorized for ATTENDANCE_MANAGE capability
@PostMapping("/events/{eventId}/attendance/sessions")
@PreAuthorize("hasAuthority('ATTENDANCE_MANAGE') or hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER','STUDENT_LEADER')")
public ResponseEntity<SessionResponse> openSession(...) { ... }
```

---

## 7. Error Handling & Leakage Prevention

To ensure that internal database errors, JDBC stack traces, and framework internals never reach clients, error handling operates under multi-layered defense:

1. **Spring Boot Container Configuration (`application.yml`)**:
   ```yaml
   server:
     error:
       include-message: never
       include-binding-errors: never
       include-stacktrace: never
       include-exception: false
   ```
2. **Global Exception Handler (`GlobalExceptionHandler.java`)**:
   - `AuthenticationException`: Sanitized to `"Invalid email or password."`; nested database/persistence exceptions return structured `SERVICE_UNAVAILABLE` (503).
   - `DataAccessException`, `PersistenceException`, `SQLException`: Intercepted and mapped to `503 SERVICE_UNAVAILABLE` with `"A temporary database error occurred. Please try again shortly."`
   - `sanitizeMessage()`: Strips all technical tokens (`sql`, `jdbc`, `resultset`, `column`, `table`, `hibernate`, `psql`, `bad value for type`, `syntax`, `nullpointer`, `hikari`, `timeout`).

---

## 8. Standard REST Request Lifecycle

```
HTTP Request (Client)
        │
        ▼
Request Logging & Correlation ID Injection
        │
        ▼
JwtAuthenticationFilter (Validates Token & Loads CustomUserDetails into SecurityContext)
        │
        ▼
Spring Security Authorization Filter (@PreAuthorize Method Security Check)
        │
        ▼
Request Body Validation (Jakarta Bean Validation @Valid)
        │
        ▼
RestController Endpoint
        │
        ▼
Application Service Layer (@Transactional)
        │
        ▼
Domain Invariants & Business Rule Validation
        │
        ▼
Spring Data JPA Repository Execution
        │
        ▼
PostgreSQL Transaction Commit
        │
        ▼
Return Immutable Response DTO Record (HTTP 200 / 201 / 204)
```

---

## 9. Backend Testing & Verification Standards

### 9.1 Test Levels
1. **Unit Tests**: Pure business logic (service-hour calculation rules, event state transitions, eligibility criteria).
2. **Integration Tests**: Spring Boot `@WebMvcTest` and `@SpringBootTest` with `@AutoConfigureMockMvc`, validating authentication, authorization boundaries, and DTO contracts.
3. **Database Tests**: Verify foreign key constraints, composite unique indexes, and rollback behavior.

### 9.2 Boundary Test Cases
- Registration at exact event capacity vs. 1 participant beyond capacity.
- Duplicate event registration attempt under concurrent requests.
- Attendance check-in after session expiration timestamp.
- Manual service-hour adjustment with negative or out-of-range values.
- Unauthorized cross-unit modification attempts by unassigned officers.

---

## 10. Operational Health & Runbook

- **Liveness Endpoint**: `GET /actuator/health/liveness` (Confirms JVM is executing).
- **Readiness Endpoint**: `GET /actuator/health/readiness` (Confirms database connection pool is healthy).
- **Graceful Shutdown**: Configured with 30-second shutdown phase (`server.shutdown: graceful`).
- **Flyway Migrations**: Applied automatically on application startup before the web container accepts traffic.
