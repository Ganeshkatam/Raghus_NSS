# Feature-Wise Specification

## Core Production Features (Active & Verified)

### 1. Authentication and Authorization
Secure login and session handling via stateless JWT tokens with Redis-backed token revocation, role assignment (Admin, Programme Officer, Volunteer), account status validation, and server-side method authorization (`@PreAuthorize`).

### 2. Volunteer Management
Volunteer profiles, enrollment status, college ID mapping, unit assignment, historical membership ledger, and leadership tracking.

### 3. NSS Units
Unit creation, unit numbering, programme officer assignment, volunteer membership management, and scope-aware access queries.

### 4. Events & Activities
Event lifecycle (Draft, Published, Open, In Progress, Completed, Cancelled), scheduling, venue, category, capacity, and transactional registration window handling.

### 5. Registration
Volunteer self-registration with automatic duplicate rejection, capacity limit validation, deadline enforcement, and status auditing.

### 6. Attendance & QR Verification
Authorized attendance sessions with rolling cryptographic QR verification tokens, authenticated volunteer self-check-in, programme officer manual check-in, duplicate prevention, and auditable supervisor corrections.

### 7. Service Hours & Milestone Progress
Append-oriented service hour claim submission, event-linked verification, programme officer approval queue, auditable adjustment records, and 120-hour milestone progress tracking.

### 8. Announcements & Notifications
Audience-scoped institutional bulletins (college-wide or unit-specific), importance ranking, in-app notification inbox, and per-user read/unread tracking.

### 9. Reports & Analytics
Executive institutional metrics, per-unit participation matrix, and 1-click CSV exports for Volunteers, Events, and Service Hours.

### 10. Operational Dashboards
Role-tailored dashboards providing immediate KPI summaries, pending administrative reviews, recent activities, and milestone progress.

---

## Future Roadmap Features (Future Scope)

The following modules are catalogued for future institutional expansion:

### 1. Special Camps (Future)
7-day rural immersion camps, village adoption activities, camp allocation, student leadership rosters, multi-session camp service hours, and community impact assessments.

### 2. Achievements & Honors (Future)
Annual Best Volunteer awards, special contribution awards, institutional honors review, nomination workflows, and public recognition portfolio.

### 3. Institutional Certificates & Credentials (Future)
Digitally verified NSS completion certificates, cryptographic verification codes, automated PDF credential generation, and university academic transcript integration.

### 4. Activity Reports (Future)
Standardized post-programme documentation, photo galleries, geo-tagged community service documentation, and University NSS Cell compliance submissions.

### 5. Document Repository (Future)
Categorized repository for Ministry of Youth Affairs circulars, operational guidelines, enrollment forms, medical fitness templates, and version-controlled institutional archives.

### 6. Advanced Institutional Administration (Future)
Fine-grained staff permission tuning, database maintenance console, live Redis cache controls, and comprehensive immutable audit query tooling.
