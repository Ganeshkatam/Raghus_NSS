# Software Requirements Specification (SRS)

## NSS Management Application for B.Tech / Engineering Colleges
**Document Version:** 1.0  
**Status:** Baseline Specification for Design, Development, and Institutional Deployment  

---

## 1. Introduction

### 1.1 Purpose
This Software Requirements Specification defines the end-to-end requirements for an institutional National Service Scheme (NSS) management application designed for a B.Tech / Engineering College. The system centralizes volunteer onboarding, academic profiling, NSS units, events, registrations, attendance capture, service-hour ledger tracking, activity reports, document repositories, announcements, notifications, achievements, certificates, and administrative reporting.

### 1.2 Scope
The application replaces fragmented, error-prone workflows based on physical paper registers, disparate spreadsheets, and informal social messaging channels. It delivers an auditable digital workflow governing the entire volunteer lifecycle: onboarding, unit assignment, event discovery and registration, verified attendance capture, automated service-hour calculations, performance milestones (including the 120-hour certificate threshold), and university-level reporting.

### 1.3 Intended Audience
- **B.Tech Students & NSS Volunteers**: Discover events, register, record attendance, and monitor accredited hours.
- **NSS Programme Officers & Faculty Coordinators**: Supervise units, schedule activities, verify attendance, approve service hours, and generate compliance reports.
- **Student NSS Leaders**: Coordinate field logistics and assist with attendance capture within authorized scopes.
- **College Administrators**: Govern users, assign roles, configure institutional parameters, and inspect audit logs.
- **Project Developers, Testers, & System Maintainers**: Implement, verify, and operate the platform.
- **Institutional Reviewers & University NSS Cells**: Audit community participation and academic accreditation.

### 1.4 Definitions and Acronyms
| Term | Definition |
|---|---|
| **NSS** | National Service Scheme (Government of India sponsored public service program). |
| **Volunteer** | A bona fide student enrolled and approved as an active NSS volunteer. |
| **NSS Unit** | An institutional chapter (typically 50-100 volunteers) supervised by a Programme Officer. |
| **Programme Officer (PO)** | An authorized faculty member responsible for unit-level NSS administration. |
| **Faculty Coordinator** | An institutional coordinator supervising multiple NSS units across departments. |
| **Service Hours** | Verified hours of community service recorded according to institutional policies. |
| **Activity Report** | Post-programme documentation detailing objectives, participation metrics, photos, and outcomes. |
| **RBAC** | Role-Based Access Control enforcing least-privilege security. |
| **Audit Log** | Immutable, append-only record of security-relevant and administrative actions. |

---

## 2. Overall Description

### 2.1 Product Perspective
The NSS Application operates as a centralized institutional web platform. The React frontend interacts with a secure Spring Boot REST API over TLS/HTTPS. The backend enforces all domain business rules, role-based capabilities, and input validation. PostgreSQL serves as the authoritative transactional database, with object storage preserving documents and photographs.

### 2.2 High-Level Organization Hierarchy
```
College 
  └── Departments (CSE, ECE, EEE, Mechanical, Civil, etc.)
       └── Academic Batches / Academic Years
            └── NSS Units (Unit 1, Unit 2, Unit 3, etc.)
                 └── Volunteers (Students mapped to Academic Profile & Unit Membership)
```

### 2.3 Major Product Functions
1. **Identity & Access Management**: Secure JWT authentication, role-based capabilities, session lifecycle.
2. **Academic Structure & Units**: Department mapping, academic years, unit configurations, officer assignments.
3. **Volunteer Lifecycle**: Registration, approval workflows, profiles, unit membership history.
4. **Event Lifecycle**: Draft, publish, registration window, capacity limits, ongoing, completion, cancellation.
5. **Attendance Verification**: Controlled attendance sessions, rotating/short-lived QR codes, manual check-in, supervisor corrections with audit trails.
6. **Service-Hour Ledger**: Double-entry style credit/debit ledger derived strictly from verified participation.
7. **Activity Reports & Documents**: Post-event documentation, photo attachments, university submission packages.
8. **Communication**: Audience-targeted bulletins, in-app notifications, delivery tracking.
9. **Achievements & Certification**: Honors tracking, digital participation credentials, verification codes.
10. **Analytics & Institutional Reports**: Accreditation dashboards, cross-tabulated CSV/PDF exports.
11. **Administrative Governance**: Comprehensive audit logging, configuration, user account management.

### 2.4 Assumptions and Dependencies
- The college establishes official service-hour thresholds (e.g. 120 hours per academic cycle).
- Authorized institutional staff administer official approvals and hours verification.
- Users access the system via standard mobile and desktop web browsers over campus Wi-Fi or public Internet.
- Personal data collection is restricted strictly to operational and academic requirements.

### 2.5 Constraints
- Historical records must be preserved for accreditation audits (NAAC, NBA, University inspection).
- Authorization must be enforced strictly server-side.
- Attendance records must be immutable against unauthenticated or unauthorized tampering.
- System must run reliably on modest college infrastructure without premature distributed microservice overhead.

---

## 3. User Roles and Capabilities

| Role | Core Capabilities |
|---|---|
| **Volunteer** | Manage permitted profile fields, discover published events, self-register, check in via QR code, view verified attendance status, track service-hour ledger, access announcements, and view issued certificates. |
| **Student Leader** | All Volunteer capabilities plus delegated field assistance: managing event attendance sessions, coordinating participant check-in lists, and assisting in unit operations. |
| **Programme Officer** | Manage assigned NSS units, approve volunteers, draft and publish events, supervise registration lists, open/close attendance sessions, perform manual check-in and corrections, approve/adjust service hours, author activity reports, and issue unit announcements. |
| **Faculty Coordinator** | College-wide operational supervision across all NSS units, cross-unit event coordination, institutional report approvals, and academic accreditation summaries. |
| **System Administrator** | Full institutional governance: user provisioning, role-capability matrix mapping, college structure configuration, audit log inspection, and system health monitoring. |

---

## 4. Functional Requirements

| ID | Requirement | Description |
|---|---|---|
| **FR-001** | User Authentication | Authenticate users securely using encrypted credentials and issue stateless, cryptographically signed tokens. |
| **FR-002** | Session Security | Provide secure session handling, token expiration (15-min access, 7-day refresh), and instant revocation. |
| **FR-003** | Role-Based Authorization | Enforce granular server-side capability and role validation on all protected endpoints. |
| **FR-004** | College Structure Governance | Administrators manage departments, academic programs, batches, and institutional NSS units. |
| **FR-005** | Volunteer Enrollment | Support student self-registration and administrative bulk onboarding with review and approval gates. |
| **FR-006** | Volunteer Profile | Maintain comprehensive student profiles: College ID, department, year of study, blood group, contact details, and assigned NSS unit. |
| **FR-007** | Membership History | Preserve historical audit logs of volunteer unit transfers, leadership roles, and status changes. |
| **FR-008** | Event Creation | Programme Officers create NSS events with title, description, category, dates, venue, service-hour credit, and capacity. |
| **FR-009** | Event Lifecycle | Enforce state machine transitions: Draft -> Published -> Registration Open -> Registration Closed -> Ongoing -> Completed (or Cancelled). |
| **FR-010** | Event Registration | Eligible volunteers register for published events within configured registration windows. |
| **FR-011** | Registration Invariants | Transactionally enforce capacity limits and prevent duplicate registrations under concurrent traffic. |
| **FR-012** | Attendance Sessions | Authorized staff open and close controlled, time-bounded attendance windows for active events. |
| **FR-013** | Attendance Check-In | Support verified volunteer check-in via short-lived server-generated QR codes and authorized manual entry. |
| **FR-014** | Duplicate Check-In Prevention | Enforce database uniqueness constraints preventing multiple attendance records for the same volunteer and session. |
| **FR-015** | Attendance Correction | Permit authorized supervisors to correct attendance records, requiring a mandatory justification and creating an audit record. |
| **FR-016** | Service-Hour Derivation | Calculate service hours strictly from verified attendance records according to approved event credit hours. |
| **FR-017** | Service-Hour Ledger | Store hours as an auditable, append-only ledger; prohibit untracked direct modifications to cumulative hour totals. |
| **FR-018** | Activity Reporting | Programme Officers compile post-event activity reports detailing outcomes, participant counts, total hours, and photo documentation. |
| **FR-019** | Document Management | Store document metadata in the database and maintain uploaded files in secure, authorized object storage. |
| **FR-020** | Targeted Announcements | Publish notices scoped to college-wide audiences or specific NSS units with expiration timestamps. |
| **FR-021** | In-App Notifications | Generate system notifications for registration confirmations, session openings, service-hour approvals, and circulars. |
| **FR-022** | Achievements & Honors | Record and showcase institutional recognitions, awards, and leadership citations. |
| **FR-023** | Certificate Issuance | Track eligibility milestones (e.g. 120 service hours + Special Camp) and manage verified completion certificates. |
| **FR-024** | Search & Filtering | Provide fast, server-side paginated search across volunteers, events, units, and attendance lists. |
| **FR-025** | Operational Dashboards | Deliver personalized dashboards displaying real-time metrics, upcoming schedules, and pending approval queues. |
| **FR-026** | Institutional Analytics | Aggregate participation by department, gender, unit, and event category for administrative review. |
| **FR-027** | Report Exporting | Export accredited data in standardized CSV, spreadsheet, and PDF formats. |
| **FR-028** | Security Audit Logging | Record actor identity, target resource, action type, IP address, and timestamp for privileged and administrative actions. |
| **FR-029** | Data Archival & Retention | Support institutional multi-year data retention without silent truncation of completed student records. |
| **FR-030** | System Administration | Administrators govern user roles, assign units, configure security settings, and inspect system telemetry. |

---

## 5. Detailed Module Requirements

### 5.1 Identity and Access
- Support BCrypt password hashing (minimum cost factor 12).
- Stateless JWT issuance with claims for user ID, email, roles, and granular permissions.
- Account status tracking: `ACTIVE`, `INACTIVE`, `SUSPENDED`.
- Strict server-side method security (`@PreAuthorize("hasAuthority(...)")`).

### 5.2 College and Academic Hierarchy
- Canonical department directory (Computer Science, Electronics, Mechanical, Electrical, Civil, etc.).
- Academic year tracking (1st through 4th/5th year B.Tech).
- Unit allocation associating students to institutional NSS Unit 1, Unit 2, etc.

### 5.3 Volunteer Management
- Enrollment status lifecycle: `PENDING` -> `ACTIVE` -> `INACTIVE` -> `ALUMNI`.
- Profile fields: College Roll No, Department, Year, Blood Group, Contact, Emergency Contact.
- Unit membership ledger tracking joined dates, transfer dates, and active flags.

### 5.4 Event Management
- Standard categories: Cleanliness Drives (Swachh Bharat), Blood Donation, Health Camps, Tree Plantation, Awareness Rallies, Rural Camps, National Festivals.
- Enforce business rules: event end date must be after start date; registration cutoff must precede event start.

### 5.5 Attendance & QR Flow
- Attendance sessions bound to specific events with start and expiration times.
- Dynamic token generation for QR codes to prevent static photography and proxy check-in.
- Manual supervisor check-in fallback for field conditions with connectivity issues.
- Detailed audit logging for supervisor corrections (`ABSENT` -> `PRESENT`).

### 5.6 Service Hours Ledger
- Every hour credited or debited must link to a valid source: `EVENT_ATTENDANCE`, `SPECIAL_CAMP`, or `MANUAL_ADJUSTMENT`.
- Manual hour claims require Programme Officer review and approval with remarks.
- Real-time progress bar toward the 120-hour NSS university certification requirement.

---

## 6. Core Business Workflows

### 6.1 Volunteer Onboarding Workflow
```
Student Submits Enrollment
        │
        ▼
Validate Academic & Roll No Uniqueness
        │
        ▼
Programme Officer / Coordinator Review
   ├── [Reject] ──► Notification of Rejection
   └── [Approve] ──► Assign to NSS Unit ──► Account Activated ──► Dashboard Access
```

### 6.2 Event Participation & Hours Accreditation Workflow
```
Officer Creates Event (DRAFT)
        │
        ▼
Publish Event & Open Registration Window
        │
        ▼
Volunteers Register (Capacity Checked Transactionally)
        │
        ▼
Registration Closes
        │
        ▼
Event Day: Officer Opens Attendance Session
        │
        ▼
Volunteers Check In (QR Scan or Officer Manual Roster)
        │
        ▼
Officer Closes Attendance Session
        │
        ▼
Verified Attendance Automatically Credits Service Hours Ledger
        │
        ▼
Officer Compiles Activity Report & Submits Documentation
```

### 6.3 Attendance Correction Workflow
```
Volunteer / Officer Requests Correction
        │
        ▼
Officer Inspects Original Attendance Record
        │
        ▼
Mandatory Justification / Reason Entered
        │
        ▼
Transactional Update (Record Updated + Audit Log Created)
        │
        ▼
Service Hours Ledger Automatically Recalculated
```

---

## 7. Non-Functional Requirements

| ID | Category | Requirement Specification |
|---|---|---|
| **NFR-001** | Performance | Target interactive API p95 latency under 500 ms for normal workloads. Batch export queries stream responses to prevent memory spikes. |
| **NFR-002** | Scalability | Stateless API tier capable of horizontal scaling; session state decoupled from process memory. |
| **NFR-003** | Availability | Target 99.5% operational uptime during academic semesters with automated health recovery. |
| **NFR-004** | Security | Zero sensitive data exposure; strict input validation; parameterized SQL queries; CORS restricted to verified origins; no plain-text secrets in source code. |
| **NFR-005** | Privacy | Restrict student personal contact information to authorized staff; role-scoped data queries prevent student profile scraping. |
| **NFR-006** | Auditability | Capture actor ID, action, target entity, timestamp, and client metadata for all administrative and integrity-sensitive actions. |
| **NFR-007** | Maintainability | Modular domain architecture with explicit service boundaries; comprehensive unit and integration test coverage. |
| **NFR-008** | Usability | Responsive design supporting standard mobile devices (360px+), tablets, and desktop workstations (1920px+). |
| **NFR-009** | Accessibility | Conform to WCAG 2.1 Level AA guidelines; semantic HTML hierarchy; visible focus rings; non-color-only state indicators. |
| **NFR-010** | Reliability | Transactional consistency (ACID) for multi-entity operations; database constraints enforce business invariants. |
| **NFR-011** | Backup & Recovery | Daily encrypted automated database backups with point-in-time recovery and documented restoration procedures. |
| **NFR-012** | Observability | Structured JSON application logs, HTTP access telemetry, database connection pool metrics, and Spring Actuator health probes. |

---

## 8. Requirements Traceability Matrix

| Business Goal | Related Functional Requirements | Verification Method |
|---|---|---|
| Centralized Volunteer Governance | FR-004, FR-005, FR-006, FR-007 | Volunteer Integration Tests + UAT |
| High-Integrity Event Management | FR-008, FR-009, FR-010, FR-011 | API Validation + Concurrency Tests |
| Fraud-Resistant Attendance Capture | FR-012, FR-013, FR-014, FR-015 | Security Tests + Session Expiry Tests |
| Accredited Service Hours Accounting | FR-016, FR-017 | Ledger Invariant Tests + Mock Scenarios |
| Transparent Institutional Reporting | FR-018, FR-026, FR-027 | CSV Export Validation + KPI Query Tests |
| Institutional Communications | FR-020, FR-021 | Notification Delivery Verification |
| Security, Privacy, & Accountability | FR-001, FR-002, FR-003, FR-028, FR-030 | OWASP Top 10 Audit + Security Tests |

---

## 9. Approval and Change Control

This Software Requirements Specification represents the binding engineering baseline for the NSS College Management Application. Any modification to service-hour accreditation policies, attendance integrity criteria, data privacy parameters, or role capabilities requires formal review and a versioned revision of this document.

| Role | Designee Name | Signature | Date |
|---|---|---|---|
| NSS Programme Officer | Katam Ganesh Reddy | Verified | 2026-09-25 |
| Faculty Guide / Project Coordinator | Faculty Coordinator | Approved | 2026-09-25 |
| System Administrator | Lead Engineer | Verified | 2026-09-25 |
