# Feature-Wise Specification

## Authentication and authorization

Secure login/session handling, role assignment, account status, least-privilege permissions and audit of privileged changes.

## Volunteer management

Volunteer onboarding, approval, profile, status, academic mapping, unit assignment and historical membership.

## NSS units

Unit creation, coordinator assignment, membership management and scope-aware access.

## Events

Draft, published, open, closed, completed, cancelled and archived states; date/time, venue, category, eligibility, capacity and registration window.

## Registration

Eligible volunteers register for events. Duplicate registration is rejected. Capacity and deadline rules are enforced transactionally.

## Attendance

Authorized attendance session creation, short-lived QR credentials, authenticated check-in, coordinator marking, duplicate prevention and controlled corrections.

## Service hours

Verified attendance produces auditable service-hour entries. Manual changes require authorization and audit history.

## Announcements and notifications

Audience-scoped announcements and notifications for operational events such as publication, registration and attendance.

## Reports

Attendance, participation, service-hour, unit and date-range reports. Large exports may run asynchronously and use expiring download access.

## Achievements and certificates

Track awards, leadership, recognitions and approved certificates. Certificate changes are controlled and auditable.

## Documents

Metadata, secure object-storage keys, type/size validation and permission-aware access. Private documents must not be publicly exposed.

## Audit

Record actor, action, resource, timestamp and safe metadata. Never log passwords, access tokens or unnecessary sensitive payloads.

## Dashboards

Role-specific operational statistics, pending actions, participation and service-hour summaries.
