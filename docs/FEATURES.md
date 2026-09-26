# Feature-Wise Implementation Specification

This specification defines the functional, technical, and operational contracts for all 25 features of the National Service Scheme (NSS) Management Platform.

---

## Module 1: Identity & Access Management (IAM)

### F-01: User Authentication, Token-Family Lifecycle & Enterprise Session Management
- **Purpose**: Authenticates system actors, manages short-lived signed JWT access tokens and hashed rotating refresh tokens, enforces server-side authentication session lifecycle (`auth_sessions`), detects refresh-token reuse with token-family revocation, supports explicit session termination and bulk invalidation, enforces password management policies with session invalidation, prevents brute-force abuse via account lockout, and maintains security audit trails.
- **Authoritative Specification Document**: [NSS_Authentication_Specification_Update.md](file:///e:/REC_NSS/docs/NSS_Authentication_Specification_Update.md).
- **Authorized Actors**:
  - Unauthenticated / Anonymous: `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`.
  - Authenticated (`ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER`, `STUDENT_LEADER`, `VOLUNTEER`): `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`, `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{sessionId}`, `POST /api/v1/auth/change-password`.
- **Architectural Security Invariants**:
  1. **No Raw Refresh-Token Persistence**: Refresh tokens are 256-bit high-entropy strings; only cryptographic SHA-256 hashes are persisted in `auth_sessions`.
  2. **Single-Use Rotation**: Every successful refresh operation rotates the refresh token, marking the prior token hash consumed and issuing a new token pair.
  3. **Reuse Detection & Family Revocation**: Presentation of a previously rotated or revoked refresh token is treated as an active token-compromise event. The entire token family (`token_family_id`) is instantly revoked, and an audit security event is logged.
  4. **Session Ownership Isolation**: Users may inspect and revoke only their own active sessions.
  5. **Status Enforcement**: Inactive (`is_active = false`) or suspended accounts are barred from authenticating or refreshing sessions.
  6. **Password Change & Reset Invalidation**: Successful password change or password reset invalidates all existing active refresh sessions for the affected user account.
  7. **Transient-Error Tolerance**: Client application must not trigger local logout or clear sessions due to transient network failures, timeouts, or 502/503/504 gateway errors.
  8. **Auditability**: All critical authentication and session state transitions emit structured, immutable audit log events.
- **API Endpoints Contract**:
  - `POST /api/v1/auth/login`: Validates credentials against BCrypt hash, checks account status and lockout thresholds, generates access token and refresh token, persists `auth_sessions` record, and returns `AuthResponse` with user dossier and granted capabilities.
  - `POST /api/v1/auth/refresh`: Validates refresh token hash against active session; rotates credential; updates `last_used_at` and stored token hash. Rejection on reuse revokes the whole token family.
  - `POST /api/v1/auth/logout`: Revokes current active session in `auth_sessions` (`revoked_at = NOW()`, `revoke_reason = 'USER_LOGOUT'`) and writes access token signature to Redis revocation store.
  - `POST /api/v1/auth/logout-all`: Revokes all active refreshable sessions for the authenticated user (`revoke_reason = 'LOGOUT_ALL'`).
  - `GET /api/v1/auth/sessions`: Lists active sessions for the current user with safe device/browser label, IP, creation time, and last used time (excluding raw tokens and token hashes).
  - `DELETE /api/v1/auth/sessions/{sessionId}`: Revokes specified session after verifying user ownership.
  - `POST /api/v1/auth/change-password`: Verifies current password, enforces password complexity policy, updates password hash, and invalidates all existing refresh sessions.
  - `POST /api/v1/auth/forgot-password`: Generates single-use, expiring reset token (TTL 15m) without revealing whether the account exists.
  - `POST /api/v1/auth/reset-password`: Validates reset token, applies new password, consumes token, and invalidates existing refresh sessions.
- **Data Entities**: `users`, `auth_sessions`, `roles`, `user_roles`, `permissions`, `role_permissions`, `audit_logs`.
- **Validation Rules**:
  - Passwords: Minimum 8 characters, at least 1 uppercase, 1 lowercase, 1 numeric, 1 special character.
  - Account state must be `is_active = true`; locked accounts (`locked_until > NOW()`) receive `423 Locked`.
  - Failed logins: Tracked in Redis and `users.failed_login_attempts`; locks account for 15 minutes after 5 consecutive failures.
- **Error Contracts**:
  - `400 Bad Request` (`AUTH_PASSWORD_POLICY_VIOLATION`, `AUTH_RESET_TOKEN_INVALID`): Invalid payload or password requirements not satisfied.
  - `401 Unauthorized` (`AUTH_BAD_CREDENTIALS`, `AUTH_TOKEN_INVALID`, `AUTH_SESSION_EXPIRED`, `AUTH_TOKEN_REUSED`): Authentication failure or revoked/reused token.
  - `403 Forbidden` (`AUTH_ACCOUNT_DISABLED`, `AUTH_FORBIDDEN`): Inactive account or unauthorized action.
  - `404 Not Found` (`AUTH_SESSION_NOT_FOUND`): Target session does not exist or does not belong to the user.
  - `423 Locked` (`AUTH_ACCOUNT_LOCKED`): Account temporarily locked due to excessive failed login attempts.
- **Implementation Phases**:
  - `AUTH-1`: Session Foundation (`auth_sessions` schema, entity, repository, login session creation, token hashing).
  - `AUTH-2`: Refresh Security (rotation mechanics, token family tracking, reuse detection, audit events).
  - `AUTH-3`: Session UX (session listing, individual session revocation, logout-all, frontend UI).
  - `AUTH-4`: Credential Lifecycle (password change hardening, forgot/reset password flow, session invalidation).
  - `AUTH-5`: Abuse Protection & Verification (failed-login tracking, account lockout, integration and transient error tests).
- **Verification Criteria**: Unit and integration tests in `AuthServiceTest`, `AuthControllerTest`, and `SessionManagementIntegrationTest` verifying login, rotation, reuse detection, session revocation, lockout, and password change invalidation.

---

### F-02: Role-Based Access Control (RBAC) & Dynamic Capabilities
- **Purpose**: Enforces least-privilege security across endpoints and service boundaries via 19 atomic capabilities mapped dynamically to 5 system roles.
- **Authorized Actors**: `ADMIN` (administrative management); evaluated on all incoming requests.
- **Capability Matrix**:
  - `USERS_VIEW`, `USERS_MANAGE`
  - `UNITS_VIEW`, `UNITS_MANAGE`
  - `VOLUNTEERS_VIEW`, `VOLUNTEERS_MANAGE`
  - `EVENTS_VIEW`, `EVENTS_MANAGE`, `EVENTS_REGISTER`
  - `ATTENDANCE_VIEW`, `ATTENDANCE_MANAGE`, `ATTENDANCE_CHECKIN`
  - `SERVICE_HOURS_VIEW`, `SERVICE_HOURS_MANAGE`, `SERVICE_HOURS_LOG`
  - `ANNOUNCEMENTS_VIEW`, `ANNOUNCEMENTS_MANAGE`
  - `REPORTS_VIEW`, `REPORTS_EXPORT`
- **API Endpoints**:
  - `GET /api/v1/users/roles`: Lists defined system roles and assigned capabilities.
  - `PUT /api/v1/users/{id}/roles`: Assigns security roles to target user accounts.
- **Data Entities**: `roles`, `permissions`, `role_permissions`, `user_roles`.
- **Validation Rules**:
  - `@PreAuthorize` method annotations enforce `hasAuthority('PERMISSION_NAME') or hasRole('ROLE_NAME')`.
  - Roles cannot be modified without `USERS_MANAGE` capability.
- **Error Contracts**:
  - `403 Forbidden` (`AUTH_INSUFFICIENT_PERMISSIONS`): Client lacks the required security authority.

---

## Module 2: Volunteer & Organizational Unit Management

### F-03: Volunteer Profile & Academic Lifecycle Management
- **Purpose**: Manages student volunteer demographic dossiers, institutional enrollment codes, branch affiliations, semester progression, and emergency medical information.
- **Authorized Actors**: `ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER` (full management); `VOLUNTEER` (self profile view and contact update).
- **API Endpoints**:
  - `GET /api/v1/volunteers`: Paginated directory with multi-field search (`name`, `rollNumber`, `department`, `unitId`, `status`).
  - `GET /api/v1/volunteers/{id}`: Detailed volunteer dossier including service hour progress and unit membership.
  - `POST /api/v1/volunteers`: Registers new volunteer profile linked to existing user account.
  - `PUT /api/v1/volunteers/{id}`: Updates contact, branch, semester, or emergency details.
- **Data Entities**: `volunteers`, `users`, `unit_memberships`.
- **Validation Rules**:
  - `roll_number` and `enrollment_number` must be unique across the institution.
  - Semester must be integer between 1 and 8; year of study between 1 and 4.
  - Emergency contact phone must be valid E.164 or 10-digit national number.
- **Error Contracts**:
  - `409 Conflict` (`VOLUNTEER_ALREADY_EXISTS`): Duplicate roll number or user account link.

---

### F-04: NSS Unit Organization & Faculty Leadership Assignment
- **Purpose**: Organizes institutional volunteers into administrative units (e.g. Unit 1, Unit 2), binds faculty Programme Officers, and manages unit lifecycle.
- **Authorized Actors**: `ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER` (`UNITS_MANAGE`).
- **API Endpoints**:
  - `GET /api/v1/units`: Returns all institutional units with active volunteer counts and PO details.
  - `GET /api/v1/units/{id}`: Fetches specific unit profile, assigned leadership, and enrolled lists.
  - `POST /api/v1/units`: Creates a new operational unit with unit number, name, and PO binding.
  - `PUT /api/v1/units/{id}`: Modifies unit title, status, or reassigns Programme Officer.
- **Data Entities**: `nss_units`, `users`, `unit_memberships`.
- **Validation Rules**:
  - `unit_number` must be unique across the active academic year.
  - Programme Officer must hold a valid user account with `PROGRAMME_OFFICER` or `FACULTY_COORDINATOR` role.
- **Error Contracts**:
  - `400 Bad Request` (`UNIT_INVALID_LEADERSHIP`): Assigned Programme Officer ID does not exist or lacks faculty role.

---

### F-05: Unit Membership & Historical Academic Year Assignment
- **Purpose**: Records volunteer enrollment into specific NSS units with role designations (`MEMBER`, `TEAM_LEAD`, `CAMP_COORDINATOR`) across multi-year cycles.
- **Authorized Actors**: `ADMIN`, `PROGRAMME_OFFICER` (`UNITS_MANAGE`, `VOLUNTEERS_MANAGE`).
- **API Endpoints**:
  - `POST /api/v1/units/{unitId}/members`: Enrolls volunteer into unit with academic year tag.
  - `DELETE /api/v1/units/{unitId}/members/{volunteerId}`: Transfers or de-registers volunteer from active unit lists.
- **Data Entities**: `unit_memberships`, `volunteers`, `nss_units`.
- **Validation Rules**:
  - A volunteer cannot have duplicate active memberships within the same academic year (`UNIQUE(volunteer_id, unit_id, academic_year)`).

---

## Module 3: Event Planning & Volunteer Registration

### F-06: Event Lifecycle & State Machine
- **Purpose**: Coordinates community service activities through a formal state machine: `DRAFT` -> `PUBLISHED` -> `OPEN` -> `IN_PROGRESS` -> `COMPLETED` (or `CANCELLED`).
- **Authorized Actors**: `ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER` (`EVENTS_MANAGE`).
- **API Endpoints**:
  - `GET /api/v1/events`: Paginated, filterable event listing (filter by `status`, `category`, `unitId`, `startDate`, `endDate`).
  - `GET /api/v1/events/{id}`: Detailed event dossier with venue, schedule, credit hours, and registration stats.
  - `POST /api/v1/events`: Creates an event in `DRAFT` or `PUBLISHED` state.
  - `PUT /api/v1/events/{id}`: Modifies event parameters (permitted only prior to `IN_PROGRESS`).
  - `PATCH /api/v1/events/{id}/status`: Transitions event across the lifecycle state machine.
- **Data Entities**: `events`, `nss_units`, `users`.
- **Validation Rules**:
  - `end_time` must be chronologically after `start_time`.
  - `allocated_hours` must be positive decimal <= 24.00 per single event.
  - Transitions to `COMPLETED` require all active attendance sessions to be closed.
- **Error Contracts**:
  - `400 Bad Request` (`EVENT_INVALID_STATE_TRANSITION`): Attempting illegal lifecycle jump (e.g. `COMPLETED` -> `DRAFT`).

---

### F-07: Event Self-Registration & Capacity Enforcement
- **Purpose**: Enables eligible volunteers to register for published community events subject to capacity caps and registration deadlines.
- **Authorized Actors**: `VOLUNTEER`, `STUDENT_LEADER` (`EVENTS_REGISTER`).
- **API Endpoints**:
  - `POST /api/v1/events/{eventId}/register`: Self-registers the authenticated volunteer.
  - `DELETE /api/v1/events/{eventId}/register`: Withdraws registration prior to event lock-in.
  - `GET /api/v1/events/{eventId}/registrations`: List of registered participants (supervisors only).
- **Data Entities**: `event_registrations`, `events`, `volunteers`.
- **Validation Rules**:
  - Rejects duplicate registrations (`UNIQUE(event_id, volunteer_id)`).
  - Enforces `max_participants` limit using row-level locking on event counter.
  - Rejects registration if event status is not `OPEN` or `PUBLISHED`.
- **Error Contracts**:
  - `409 Conflict` (`EVENT_REGISTRATION_FULL`): Event capacity ceiling reached.
  - `400 Bad Request` (`EVENT_REGISTRATION_CLOSED`): Event registration window expired.

---

## Module 4: Attendance Verification & Dynamic QR Codes

### F-08: Dynamic Cryptographic QR Attendance Session Generation
- **Purpose**: Generates rolling cryptographic QR codes for attendance sessions to prevent buddy-punching and off-site spoofing.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `FACULTY_COORDINATOR`, `ADMIN` (`ATTENDANCE_MANAGE`).
- **API Endpoints**:
  - `POST /api/v1/attendance/sessions`: Initiates an attendance session for an active event.
  - `GET /api/v1/attendance/sessions/{sessionId}/token`: Generates or rotates the active HMAC-SHA256 QR token (valid for 30-60 seconds).
  - `POST /api/v1/attendance/sessions/{sessionId}/close`: Closes active check-in window.
- **Data Entities**: `attendance_sessions`, `events`.
- **Validation Rules**:
  - Token payload encodes `sessionId`, `eventId`, `timestamp`, and `nonce` signed with institutional secret.
  - Only one session can be actively broadcasting QR per event at a given moment.

---

### F-09: Volunteer Mobile/Web Self-Check-in via Cryptographic QR
- **Purpose**: Allows volunteers physically present at an event to verify attendance by scanning the rolling QR code through the web app.
- **Authorized Actors**: `VOLUNTEER`, `STUDENT_LEADER` (`ATTENDANCE_CHECKIN`).
- **API Endpoints**:
  - `POST /api/v1/attendance/check-in`: Submits scanned QR token payload for validation.
- **Data Entities**: `attendance_records`, `attendance_sessions`, `volunteers`.
- **Validation Rules**:
  - Verifies HMAC signature and ensures payload timestamp has not expired (> 60s skew rejected).
  - Enforces single check-in per volunteer per session (`UNIQUE(session_id, volunteer_id)`).
  - Volunteer must be registered for the parent event (or auto-enrolled if open policy).
- **Error Contracts**:
  - `400 Bad Request` (`ATTENDANCE_TOKEN_EXPIRED`): Scanned QR token has expired.
  - `409 Conflict` (`ATTENDANCE_ALREADY_RECORDED`): Volunteer already checked in for session.

---

### F-10: Supervisory Manual Attendance Override & List Check-in
- **Purpose**: Allows Programme Officers to manually check in volunteers who lack smartphones or experience network connectivity issues in the field.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `FACULTY_COORDINATOR`, `ADMIN` (`ATTENDANCE_MANAGE`).
- **API Endpoints**:
  - `POST /api/v1/attendance/sessions/{sessionId}/manual`: Records check-in with `check_in_method = 'MANUAL'` and supervisor ID.
  - `POST /api/v1/attendance/sessions/{sessionId}/bulk`: Bulk check-in of a list of volunteer IDs.
- **Data Entities**: `attendance_records`, `attendance_sessions`, `volunteers`.
- **Validation Rules**:
  - Target volunteer must be in `ACTIVE` status.
  - Audit record must capture the authorizing supervisor's user ID.

---

### F-11: Attendance Audit & Formal Correction Workflow
- **Purpose**: Facilitates post-event attendance dispute resolution with mandatory audit reasons and supervisor sign-off.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `ADMIN` (`ATTENDANCE_MANAGE`).
- **API Endpoints**:
  - `POST /api/v1/attendance/records/{recordId}/corrections`: Logs an attendance adjustment request or override.
  - `GET /api/v1/attendance/corrections`: Displays pending correction requests.
- **Data Entities**: `attendance_corrections`, `attendance_records`.
- **Validation Rules**:
  - Justification reason is mandatory (minimum 10 characters).
  - Historical check-in status preserved in `previous_status`.

---

## Module 5: Service Hours & 120-Hour Milestone Ledger

### F-12: Service Hour Claim Submission & Transactional Ledger
- **Purpose**: Submits and logs volunteer community service credit entries against verified attendance or approved special assignments.
- **Authorized Actors**: `VOLUNTEER`, `STUDENT_LEADER` (`SERVICE_HOURS_LOG`); auto-generated via Attendance check-in.
- **API Endpoints**:
  - `POST /api/v1/service-hours`: Submits a new service hour claim with activity documentation.
  - `GET /api/v1/service-hours/my`: Returns authenticated volunteer's historical service ledger.
- **Data Entities**: `service_hour_entries`, `events`, `volunteers`.
- **Validation Rules**:
  - Claimed hours cannot exceed maximum allocated hours defined by parent event.
  - Rejects negative or zero hour claims.

---

### F-13: Service Hour Approval Queue & Supervisory Adjudication
- **Purpose**: Provides Programme Officers with a queue to inspect, approve, adjust, or reject pending service hour claims.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `FACULTY_COORDINATOR`, `ADMIN` (`SERVICE_HOURS_MANAGE`).
- **API Endpoints**:
  - `GET /api/v1/service-hours/pending`: Retrieves pending claims filtered by unit.
  - `PATCH /api/v1/service-hours/{id}/status`: Approves or rejects claim (`APPROVED`, `REJECTED`).
  - `POST /api/v1/service-hours/bulk-approve`: Atomically sanctions a batch of claims.
- **Data Entities**: `service_hour_entries`, `volunteers`.
- **Validation Rules**:
  - Approving an entry atomically recalculates and updates `volunteers.total_hours` inside a single database transaction.
  - Rejection requires an explanatory remark returned to the volunteer.

---

### F-14: 120-Hour Completion Milestone Tracking & Progress Analytics
- **Purpose**: Tracks each volunteer's trajectory toward the mandatory 120-hour NSS graduation requirement across regular activities and special camps.
- **Authorized Actors**: All authenticated roles (`SERVICE_HOURS_VIEW`).
- **API Endpoints**:
  - `GET /api/v1/service-hours/progress/{volunteerId}`: Returns total approved hours, regular vs camp breakdown, and percentage to 120h completion.
  - `GET /api/v1/reports/milestone-completion`: Lists all volunteers who have achieved >= 120 approved service hours.
- **Data Entities**: `volunteers`, `service_hour_entries`, `events`.
- **Validation Rules**:
  - Regular activities cap at 100 hours; minimum 20 hours required from approved special camps for full degree certification.

---

## Module 6: Institutional Communication & Notifications

### F-15: Audience-Scoped Announcements & Priority Broadcasts
- **Purpose**: Broadcasts institutional news, emergency drives, and circulars with priority tags (`LOW`, `NORMAL`, `HIGH`, `URGENT`) and unit scoping.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `FACULTY_COORDINATOR`, `ADMIN` (`ANNOUNCEMENTS_MANAGE`).
- **API Endpoints**:
  - `GET /api/v1/announcements`: Retrieves active bulletins scoped to user's unit and audience.
  - `POST /api/v1/announcements`: Publishes new announcement with title, markdown body, and priority.
  - `DELETE /api/v1/announcements/{id}`: Unpublishes or deletes an expired bulletin.
- **Data Entities**: `announcements`, `nss_units`, `users`.
- **Validation Rules**:
  - Unit-level Programme Officers can publish announcements only for their assigned unit or general audience.
  - Urgent priority triggers immediate notification push.

---

### F-16: In-App Notification Engine & Notification Preference
- **Purpose**: In-app notification center notifying volunteers of event registrations, attendance confirmation, hour approvals, and announcements.
- **Authorized Actors**: All authenticated users.
- **API Endpoints**:
  - `GET /api/v1/notifications`: Paginated in-app inbox with unread count.
  - `PATCH /api/v1/notifications/{id}/read`: Marks notification as read.
  - `POST /api/v1/notifications/read-all`: Marks entire user inbox as read.
- **Data Entities**: `notifications`, `users`.
- **Validation Rules**:
  - Users can view and mutate only their own notification records (`user_id = principal.id`).

---

## Module 7: Honors, Certificates & Documentation

### F-17: Volunteer Achievement Recognition & Honors Ledger
- **Purpose**: Catalogs merit honors, state/national camp selections, and Best Volunteer citations.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `ADMIN` (create/award); all users (view).
- **API Endpoints**:
  - `GET /api/v1/achievements`: Public or authenticated list of recognized achievements.
  - `POST /api/v1/achievements`: Awards honor to a volunteer.
- **Data Entities**: `achievements`, `volunteers`.

---

### F-18: Cryptographically Verifiable PDF Certificate Issuance
- **Purpose**: Issues digital NSS completion certificates bearing a unique certificate number and SHA-256 verification hash.
- **Authorized Actors**: `ADMIN`, `PROGRAMME_OFFICER`.
- **API Endpoints**:
  - `POST /api/v1/certificates/generate/{volunteerId}`: Issues certificate upon verification of >= 120 approved service hours.
  - `GET /api/v1/certificates/verify/{hash}`: Public endpoint verifying authenticity and issuing metadata.
- **Data Entities**: `certificates`, `volunteers`.
- **Validation Rules**:
  - Blocked if volunteer has < 120 total approved hours.
  - Verification hash is computed over `certificate_number + volunteer_id + total_hours + secret_salt`.

---

### F-19: Post-Event Activity Report Compilation
- **Purpose**: Compiles standardized documentation after event completion including executive summary, photo gallery links, and community impact indicators.
- **Authorized Actors**: `PROGRAMME_OFFICER`, `STUDENT_LEADER` (`EVENTS_MANAGE`).
- **API Endpoints**:
  - `POST /api/v1/events/{id}/activity-report`: Submits formal post-event documentation.
  - `GET /api/v1/events/{id}/activity-report`: Fetches approved report.
- **Data Entities**: `activity_reports`, `events`.

---

### F-20: Centralized Document & Circular Archive
- **Purpose**: Repository for Ministry of Youth Affairs circulars, university guidelines, medical fitness formats, and enrollment forms.
- **Authorized Actors**: `ADMIN`, `PROGRAMME_OFFICER` (upload); all users (download).
- **API Endpoints**:
  - `GET /api/v1/documents`: Lists downloadable documents categorized by topic.
  - `POST /api/v1/documents`: Uploads document with access classification.
- **Data Entities**: `documents`, `users`.

---

## Module 8: Analytics, Reporting & Administration

### F-21: Multi-Dimensional Operational Analytics & Dashboard KPIs
- **Purpose**: Delivers real-time executive KPIs, unit participation benchmarks, department engagement breakdowns, and gender equity stats.
- **Authorized Actors**: `ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER` (`REPORTS_VIEW`).
- **API Endpoints**:
  - `GET /api/v1/reports/summary`: Aggregate metrics (active volunteers, units, hours logged, events held).
  - `GET /api/v1/reports/unit-performance`: Side-by-side unit comparison matrix.
  - `GET /api/v1/reports/monthly-trends`: Time-series participation curves.
- **Data Entities**: Aggregates from `volunteers`, `events`, `service_hour_entries`, `nss_units`.

---

### F-22: Standardized CSV & PDF Data Export Engine
- **Purpose**: Generates one-click institutional data exports for University NSS Cell reporting.
- **Authorized Actors**: `ADMIN`, `FACULTY_COORDINATOR`, `PROGRAMME_OFFICER` (`REPORTS_EXPORT`).
- **API Endpoints**:
  - `GET /api/v1/reports/export/volunteers`: CSV export of volunteer enrollment lists.
  - `GET /api/v1/reports/export/events`: CSV export of all organized events and attendance counts.
  - `GET /api/v1/reports/export/service-hours`: CSV export of verified service credit ledger.
- **Validation Rules**:
  - Response headers set `Content-Type: text/csv` and `Content-Disposition: attachment; filename="..."`.

---

### F-23: Immutable Supervisory Audit Trail & Activity Logging
- **Purpose**: Captures every administrative action (event status updates, manual attendance overrides, service hour sanctions) in an immutable audit ledger with before/after JSONB snapshots.
- **Authorized Actors**: `ADMIN` (query/review).
- **API Endpoints**:
  - `GET /api/v1/audit/logs`: Filterable audit log viewer with user, action, date range, and entity filters.
- **Data Entities**: `audit_logs`.
- **Validation Rules**:
  - Audit records cannot be updated or deleted through the API (`INSERT`-only pattern).

---

### F-24: System Configuration, Cache Controls & Health Telemetry
- **Purpose**: Provides operational telemetry, Redis cache eviction controls, and Spring Boot Actuator health endpoints.
- **Authorized Actors**: `ADMIN`.
- **API Endpoints**:
  - `GET /actuator/health`: System liveness and database/Redis connection probe.
  - `POST /api/v1/admin/cache/clear`: Clears specific Redis cache regions (e.g. `nss_announcements`, `nss_units`).
- **Validation Rules**:
  - Actuator sensitive details restricted to localhost or authenticated Admin sessions.

---

### F-25: Multi-Year Batch Volunteer Promotion & Data Archival
- **Purpose**: End-of-year administrative workflow to graduate final-year volunteers, advance continuing student semesters, and archive unit lists.
- **Authorized Actors**: `ADMIN`.
- **API Endpoints**:
  - `POST /api/v1/admin/academic-year/rollover`: Executes atomic batch promotion of volunteers and closes old academic year memberships.
- **Data Entities**: `volunteers`, `unit_memberships`, `nss_units`.
- **Validation Rules**:
  - Executes inside a single serializable database transaction.
  - Volunteers with year of study 4 transition to status `COMPLETED`.

