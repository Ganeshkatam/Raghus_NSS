# System Architecture & Technical Design Report

## NSS College Management Application
**Document Version:** 1.0  
**Scope:** Complete System Architecture, Technology Selection, Domain Modeling, and Operational Controls  
**Architecture Style:** Secure Modular Monolith with REST API and Relational Storage  

---

## 1. Executive Summary

This report defines the production-grade architecture for the National Service Scheme (NSS) Management Application at Raghu Engineering College. The system centralizes volunteer onboarding, NSS unit administration, community activities, event registration, verified attendance capture, service-hour calculation, bulletins, documentation, reports, achievements, certificates, and institutional controls.

The recommended architectural pattern is a **Modular Monolith**: a single deployable backend containing strong, explicit domain boundaries, a relational PostgreSQL database as the authoritative system of record, object storage for media and certificates, and a responsive web application frontend. This design maximizes engineering velocity and minimizes operational complexity while maintaining high transactional integrity and a clean architectural path for future multi-college expansion.

### Core Architectural Principles
1. **Server-Authoritative Business Logic**: The server enforces all eligibility, capacity, timestamps, attendance validity, and service-hour calculations. Client state is never trusted as authoritative.
2. **Double-Entry Style Service-Hour Accounting**: Service hours are derived exclusively from verified attendance and audited adjustments; direct mutation of cumulative totals is forbidden.
3. **Least Privilege & Role-Based Access Control**: Granular capabilities govern every protected resource; frontend visibility is strictly a UX convenience and never security.
4. **Comprehensive Auditability**: Sensitive administrative changes, attendance corrections, and security events produce immutable, traceable audit trails.

---

## 2. System Overview

### 2.1 Primary System Actors
- **NSS Volunteer**: Maintains personal profile, explores community events, self-registers, performs verified QR check-in, inspects verified service hours, and accesses issued certificates.
- **Student NSS Leader**: Assists in on-ground event coordination, manages participant check-ins, and supports attendance verification within assigned units.
- **NSS Programme Officer (PO)**: Governs assigned NSS units, schedules community events, manages registrations, supervises attendance sessions, approves service hours, and compiles activity reports.
- **Faculty Coordinator**: Exercises cross-unit supervision, coordinates multi-department initiatives, reviews institutional metrics, and approves academic accreditations.
- **System Administrator**: Configures institutional parameters, manages users and role-capability mappings, inspects audit logs, and monitors system health.

### 2.2 System Goals
- Establish a single institutional source of truth for all college NSS operations.
- Eliminate proxy attendance, duplicate registrations, and paper-based record manipulation.
- Automate service-hour calculations tied directly to verified attendance.
- Provide real-time operational analytics and exportable compliance reports.
- Enforce strict server-side authorization and data security.

### 2.3 Non-Goals for Initial Release
- Microservice decomposition across independent network boundaries.
- Autonomous AI-driven attendance decision-making without supervisor oversight.
- Direct replacement of campus-wide ERP identity databases (operates independently with future SSO compatibility).

---

## 3. Functional Architecture & Bounded Modules

The system is organized into modular bounded contexts. Communication between modules occurs through explicit application service contracts and domain events:

| Module | Core Responsibilities |
|---|---|
| **Identity & Access** | Authentication, stateless JWT issuance, token lifecycle, password encryption, and capability validation. |
| **Volunteer Management** | Student profiling, academic department mapping, enrollment lifecycle, and leadership designations. |
| **Unit Management** | NSS unit provisioning, Programme Officer assignments, volunteer lists, and unit-scoped access boundaries. |
| **Event Management** | Programme lifecycle (Draft -> Published -> Open -> Closed -> Completed -> Cancelled), venue, capacity, and scheduling. |
| **Event Registration** | Volunteer self-registration, capacity reservation, cutoff enforcement, and registration roster tracking. |
| **Attendance** | Controlled sessions, dynamic QR verification, manual check-in lists, supervisor corrections, and audit history. |
| **Service Hours** | Authoritative service-hour ledger, event credit derivation, manual claims review, and 120-hour milestone progress. |
| **Activity Reporting** | Post-programme documentation, participant summaries, outcomes, and photo documentation. |
| **Communication** | Targeted announcements (college-wide or unit-scoped), notification inbox, and delivery tracking. |
| **Recognition & Credentials** | Achievement citations, award nominations, certificate eligibility validation, and digital certificate issuance. |
| **Documents & Media** | Secure file upload metadata, MIME-type validation, and object storage integration for circulars and reports. |
| **Reports & Analytics** | Executive KPI dashboards, participation matrices, and multi-format data exports (CSV/PDF). |
| **Audit & Governance** | Tamper-resistant logging of privileged changes, configuration updates, and system parameters. |

---

## 4. High-Level System Architecture

```
                  ┌──────────────────────────────────────────────┐
                  │    Web & Mobile Clients (React + Vite)      │
                  └──────────────────────┬───────────────────────┘
                                         │ HTTPS / TLS
                                         ▼
                  ┌──────────────────────────────────────────────┐
                  │   Reverse Proxy / Load Balancer (Render)     │
                  └──────────────────────┬───────────────────────┘
                                         │
                                         ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                APPLICATION BACKEND API                                 │
│                                                                                        │
│   ┌─────────────────────┐    ┌─────────────────────┐    ┌──────────────────────────┐   │
│   │ Authentication &    │    │ Request Pipeline &  │    │  Global Error Filter &   │   │
│   │ Method Security     │───►│ Validation (DTOs)   │───►│  Structured Response     │   │
│   └─────────────────────┘    └─────────────────────┘    └──────────────────────────┘   │
│                                         │                                              │
│                                         ▼                                              │
│                         ┌───────────────────────────────┐                              │
│                         │     REST Controller Layer     │                              │
│                         └───────────────┬───────────────┘                              │
│                                         │                                              │
│                                         ▼                                              │
│                         ┌───────────────────────────────┐                              │
│                         │   Application Service Layer   │                              │
│                         └───────────────┬───────────────┘                              │
│                                         │                                              │
│               ┌─────────────────────────┴─────────────────────────┐                    │
│               ▼                                                   ▼                    │
│   ┌───────────────────────┐                           ┌───────────────────────────┐    │
│   │  Domain Rules Layer   │                           │ Persistence Repositories  │    │
│   └───────────────────────┘                           └─────────────┬─────────────┘    │
└─────────────────────────────────────────────────────────────────────┼──────────────────┘
                                                                      │
                                         ┌────────────────────────────┴─────────────┐
                                         ▼                                          ▼
                         ┌───────────────────────────────┐          ┌───────────────────────────────┐
                         │     PostgreSQL Database       │          │   Object Storage (Documents)  │
                         │  (Transactional Ledger & PKs) │          │    (Photos, Reports, Certs)   │
                         └───────────────────────────────┘          └───────────────────────────────┘
```

### Technology Selection Rationale
| Layer | Technology | Engineering Rationale |
|---|---|---|
| **Frontend** | React + TypeScript + Vite | Strong static typing, rich component ecosystem, high performance, fast production builds. |
| **Backend API** | Java 21 + Spring Boot 3 | Enterprise stability, robust dependency injection, declarative transactions, mature Spring Security 6 RBAC. |
| **Database** | PostgreSQL 17 (Supabase) | Full ACID compliance, foreign key constraints, JSON support, robust indexing, UUID primary keys. |
| **Migrations** | Flyway | Strict version-controlled database schema evolution; prevents manual unverified production DDL changes. |
| **Cache & Sessions** | Redis | High-speed token revocation, rate limiting, and temporary attendance session caching. |
| **Object Storage** | S3-Compatible Cloud Storage | Scalable, decoupled storage for heavy binaries (activity photos, signed circulars, PDF certificates). |

---

## 5. Data Architecture & Relational Integrity

PostgreSQL is the single authoritative system of record. Relational integrity is enforced at the database boundary via foreign keys, unique indexes, and transactional boundaries.

```
[Users] 1 ──── 0..1 [Volunteers] 1 ──── N [Unit Memberships] N ──── 1 [NSS Units]
   │                       │                                               │
   │ 1                     │ 1                                             │ 1
   │                       │                                               │
   ▼ N                     ▼ N                                             ▼ N
[User Roles]      [Event Registrations]                             [Events]
   │                       │                                           │
   │ N                     │ N                                         │ 1
   │                       │                                           │
   ▼ 1                     ▼ 1                                         ▼ 1..N
 [Roles]          [Attendance Records] ◄────────────────────── [Attendance Sessions]
   │                       │
   │ 1                     │ 1
   │                       │
   ▼ N                     ▼ 1
[Role Permissions] [Service Hour Entries]
```

### Core Invariants Enforced by Architecture
1. **At-Most-One Active Membership**: A volunteer can possess at most one active membership in an NSS unit at any given timestamp.
2. **Registration Idempotency & Capacity**: `UNIQUE(event_id, volunteer_id)` enforces single registration; capacity checks occur inside database transactions.
3. **Session-Bound Attendance**: Attendance check-in records must reference an active, non-expired attendance session and an eligible registered volunteer.
4. **Ledger Immutability**: Service hours are append-only. Adjustments add corrective ledger rows (`CREDIT` / `DEBIT`) with mandatory audit notes; existing rows are never silently mutated.
5. **Decoupled File Storage**: The database stores opaque file keys, MIME types, and size metrics; file binaries are stored in dedicated object buckets.

---

## 6. Security Architecture

### 6.1 Authentication & Token Lifecycle
- Credentials verified against BCrypt-hashed passwords (cost factor 12).
- Stateless JWT issuance containing claims: `sub` (email), `userId`, `roles`, and `permissions`.
- Access tokens expire after 15 minutes; refresh tokens expire after 7 days with rotation.
- Instant token blacklisting supported via Redis cache.

### 6.2 Authorization & Capability Matrix
- Authorization is strictly enforced at the API boundary using `@PreAuthorize("hasAuthority('...') or hasRole('...')")`.
- Horizontal authorization checks ensure Programme Officers can only access and modify records within their assigned NSS unit.
- Frontend permission checks purely dictate UI visibility; all business actions are re-authorized on the server.

### 6.3 Secure Error Filtering & Defense in Depth
- Generic exceptions are captured by a centralized `@RestControllerAdvice` exception handler.
- Under no circumstances are database error messages, JDBC exception strings, SQL queries, or internal stack traces exposed to end users.
- Server error responses follow a uniform contract:
  ```json
  {
    "error": {
      "code": "EVENT_CAPACITY_REACHED",
      "message": "This event has reached its maximum participant capacity.",
      "details": null
    }
  }
  ```

---

## 7. Attendance & Service-Hour Workflow

Because NSS service hours represent official academic and extra-curricular credentials, attendance verification is designed with high cryptographic and procedural integrity:

```
Officer Opens Attendance Session (Binds Event, Start Time, Expiration Time)
                               │
                               ▼
            Backend Generates Short-Lived Session Token
                               │
                               ▼
            Officer Displays Dynamic QR Code at Venue
                               │
                               ▼
        Volunteer Scans QR Code with Authenticated Mobile App
                               │
                               ▼
      Backend Validates Session Expiration, Event Window, & Volunteer Eligibility
                               │
                               ▼
        Database Transaction: Insert Verified Attendance Record
                               │
                               ▼
      Automatic Service-Hour Ledger Entry Generated (e.g. +4.0 Hours)
                               │
                               ▼
       Volunteer Progress Toward 120-Hour Certificate Milestone Updated
```

---

## 8. Deployment Topology & CI/CD Pipeline

```
Developer Git Commit
        │
        ▼
GitHub Actions CI/CD
   ├── 1. Code Checkout & Dependency Verification
   ├── 2. Backend Automated Test Suite (30 JUnit/Spring Tests)
   ├── 3. Frontend Production Build & TypeScript Verification (tsc + vite build)
   └── 4. Security & Vulnerability Scanning
        │
        ▼
Automated Cloud Deployment
   ├── Render Web Service: Dockerized Spring Boot Jar (https://api-nss.onrender.com)
   ├── Supabase: Managed PostgreSQL 17 Cloud Database (ap-south-1)
   └── Vercel: Edge-Deployed React/TypeScript Application (https://recnss.vercel.app)
```

---

## 9. Performance & Observability

- **Database Indexing**: Explicit B-tree indexes on foreign keys, `event_id`, `volunteer_id`, `college_id`, and `created_at` timestamps ensure fast indexed queries.
- **Connection Pooling**: HikariCP connection pool configured with automatic leak detection, validation timeouts, and sizing optimized for cloud deployment.
- **Health Probes**: Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/info`) integrated with Render zero-downtime health checking.
- **Graceful Shutdown**: 30-second shutdown phase ensures running transactions and pending database commits finish cleanly before container recycling.

---

## 10. Key Architectural Trade-Offs

1. **Modular Monolith vs Microservices**:
   - *Decision*: Adopt a modular monolith.
   - *Rationale*: A single engineering college workload does not justify the network latency, distributed transaction complexity, and operational overhead of multiple microservices. Strong module boundaries within Spring Boot allow future extraction if the system is expanded to multi-college tenancy.
2. **Ledger-Based Service Hours vs Single Mutable Counter**:
   - *Decision*: Maintain an append-only transaction ledger (`service_hour_entries`).
   - *Rationale*: Academic certificates require an indisputable audit trail. Storing discrete credits and debits guarantees that any adjustment can be traced back to an event, supervisor approval, or correction reason.
3. **Decoupled Object Storage vs Relational Byte Storage**:
   - *Decision*: Store document metadata in PostgreSQL and binary files in object storage.
   - *Rationale*: Prevents database bloat, keeps backups lightweight, and allows fast direct streaming of event photographs and activity documentation.
