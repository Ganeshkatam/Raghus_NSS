# Frontend Implementation

## Stack

React, TypeScript, Vite, React Router, and a centralized HTTP client (Fetch/Axios) with authorization interceptors.

## Feature structure

Organize by feature: auth, volunteers, units, events, registrations, attendance, service-hours, announcements, notifications, achievements, certificates, reports and admin.

Shared UI, API client, validation and application-shell code live in common modules.

## Rules

- Use typed API contracts.
- Keep server state separate from local UI state.
- Keep shareable filters and pagination in URL state.
- Provide loading, empty, success, validation, forbidden and server-error states.
- Do not duplicate backend business rules in the client.
- Hide unavailable actions for usability, but rely on the backend for authorization.
- Use accessible forms, labels, keyboard navigation and non-color-only status indicators.

## Critical screens

1. Login
2. Role-specific dashboard
3. Volunteer profile
4. NSS unit management
5. Event list/detail/create/edit
6. Registration management
7. Attendance session/QR workflow
8. Service-hour history
9. Announcements
10. Reports
11. Achievements/certificates
12. Administration/audit
