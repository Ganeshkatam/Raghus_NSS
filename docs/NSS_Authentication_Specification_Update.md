# NSS Platform V1 — Authentication & Session Management Update

> This document updates **Section 1: Authentication & Session Management** of the NSS Platform V1 Comprehensive Production Capability Matrix.
>
> The existing V1 terminology and API conventions are retained. This update supersedes the earlier blocklist-only session model and defines the target authentication/session lifecycle for the next authentication implementation phase.

---

## 1. Authentication & Session Management

| Dimension | Specification |
| :--- | :--- |
| **Current Capability** | JWT-based login (`POST /api/v1/auth/login`) with username/email and BCrypt password; access-token authentication; refresh endpoint (`POST /api/v1/auth/refresh`); password change; user-status validation; refresh invalidation on password change. |
| **Target Capabilities** | - Rotating refresh tokens.<br>- Server-side authentication session tracking.<br>- Refresh-token reuse detection and token-family revocation.<br>- Explicit logout and individual session revocation.<br>- Revoke-all-other-sessions capability.<br>- Password change with session invalidation.<br>- Password recovery/reset workflow.<br>- Failed-login protection and temporary account lockout.<br>- Authentication/security audit events.<br>- Session/device visibility for the authenticated user. |
| **Business Rules** | - Passwords must be minimum 8 characters with at least one uppercase, one lowercase, one numeric, and one special character.<br>- Only `ACTIVE` users may authenticate or refresh tokens.<br>- When a user becomes `INACTIVE` or `SUSPENDED`, subsequent authenticated requests and refresh operations must be rejected according to the authentication lifecycle rules.<br>- Every successful refresh rotates the refresh token.<br>- A previously rotated refresh token must never be accepted again.<br>- Detection of refresh-token reuse revokes the associated token family and requires fresh authentication.<br>- Raw refresh tokens must never be persisted; only a cryptographic hash may be stored server-side.<br>- Password change invalidates existing refresh sessions for the affected user.<br>- Password reset invalidates existing refresh sessions for the affected user.<br>- Logout revokes the current authentication session.<br>- Authentication/session revocation must be auditable. |
| **Backend Requirements** | - `POST /api/v1/auth/login`: authenticate credentials and create an authentication session.<br>- `POST /api/v1/auth/refresh`: validate the refresh session, rotate the refresh token, revoke the previous token, and detect reuse.<br>- `POST /api/v1/auth/logout`: revoke the current authentication session.<br>- `POST /api/v1/auth/logout-all`: revoke all authentication sessions belonging to the current user.<br>- `GET /api/v1/auth/sessions`: return the authenticated user's active sessions without exposing refresh-token material.<br>- `DELETE /api/v1/auth/sessions/{sessionId}`: revoke a selected session belonging to the authenticated user.<br>- `POST /api/v1/auth/change-password`: verify the current password, apply the password policy, update the password, and invalidate existing refresh sessions as defined by the session policy.<br>- Password reset endpoints must use single-use, expiring reset credentials and must not reveal whether an account exists.<br>- Authentication services must distinguish invalid credentials/authentication failures from transient infrastructure failures. |
| **Authentication Session Model** | Introduce a persistent `auth_sessions` record for each refreshable login session. The record should contain: `session_id`, `user_id`, `refresh_token_hash`, `token_family_id`, `created_at`, `last_used_at`, `expires_at`, `revoked_at`, `revoke_reason`, `ip_address`, `user_agent`, and optional `device_label`. |
| **Refresh Token Lifecycle** | `LOGIN → access token + refresh token → auth_sessions row → REFRESH → verify stored hash → revoke/rotate previous refresh credential → issue new refresh token → update session`. If a revoked/previous refresh token is presented, treat it as token reuse, revoke the token family, record a security audit event, and require a new login. |
| **Frontend Requirements** | - Preserve clean local credential cleanup on definitive authentication failure.<br>- Do not log the user out solely because of a transient network, timeout, 502, 503, or 504 error during background refresh/API activity.<br>- Provide Change Password and session-management interfaces.<br>- Show active sessions using safe metadata such as device label and last-used time; never expose refresh-token values.<br>- Redirect to `/login` when the server definitively rejects the authentication session or the account is no longer permitted to authenticate. |
| **Authorization** | Login and password-recovery initiation are unauthenticated entry points. Session listing/revocation, change-password, and logout operations require `isAuthenticated()`. Password reset completion is authorized by a valid, single-use reset credential rather than an existing authenticated session. |
| **Database Impact** | - Add the `auth_sessions` table with indexes for `user_id`, `token_family_id`, and active/revoked/expiry lookup.<br>- Store only a hash of each refresh token.<br>- Retain `failed_login_attempts` and `locked_until` on `users` if account-lockout is enabled by the implementation phase.<br>- Authentication audit records use the existing central audit infrastructure. |
| **Security Audit Events** | At minimum: successful login, failed login, logout, token refresh, refresh-token reuse detection, session revocation, logout-all, password change, password-reset request/completion, and account lockout/security-relevant authentication failures. |
| **Test Coverage** | Unit and integration tests must cover successful login; invalid credentials; lockout threshold; inactive/suspended account rejection; successful refresh; refresh-token rotation; replay of a rotated token; token-family revocation after reuse; logout; session revocation; logout-all; password-change session invalidation; password-reset invalidation; expired sessions; session ownership; and transient frontend network/error behavior. |

---

## Authentication Security Invariants

1. **No raw refresh-token persistence:** refresh-token values must not be stored in plaintext or logs.
2. **Single-use rotation:** a refresh token accepted successfully must not remain reusable after rotation.
3. **Reuse detection:** presentation of a rotated/revoked refresh token is a security event.
4. **Family revocation:** refresh-token reuse revokes the affected token family.
5. **Session ownership:** users may inspect/revoke only their own sessions unless an explicit administrative capability is later introduced.
6. **Status enforcement:** `INACTIVE` and `SUSPENDED` users cannot regain access through existing refresh credentials.
7. **Password-change invalidation:** successful password change invalidates existing refresh sessions according to policy.
8. **Password-reset invalidation:** successful password reset invalidates existing refresh sessions.
9. **Transient-error tolerance:** temporary infrastructure/network failure must not be treated as proof that authentication expired.
10. **Auditability:** security-sensitive authentication/session transitions must be represented in the immutable audit trail.

---

## Authentication Session API Contract

### `POST /api/v1/auth/login`

Authenticates a user and establishes an authentication session.

**Success:** access token, refresh token, and authenticated user profile.

**Requirements:** validate account status, apply failed-login/lockout policy, create a server-side session, and never expose session-internal secrets.

### `POST /api/v1/auth/refresh`

Rotates the refresh token for an existing session.

**Success:** previous refresh credential is invalidated, a new refresh credential is issued, and `last_used_at` is updated.

**Security failure:** reuse of a rotated/revoked credential triggers token-family revocation and security auditing.

### `POST /api/v1/auth/logout`

Revokes the current authentication session.

### `POST /api/v1/auth/logout-all`

Revokes all refreshable authentication sessions belonging to the authenticated user.

### `GET /api/v1/auth/sessions`

Returns active sessions for the authenticated user. Responses must exclude raw refresh tokens, refresh-token hashes, signing secrets, and other credential material.

### `DELETE /api/v1/auth/sessions/{sessionId}`

Revokes one session after verifying ownership.

### `POST /api/v1/auth/change-password`

Request:

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-password"
}
```

The new password must satisfy the documented password policy. Successful password change invalidates existing refresh sessions according to the security policy.

### Password Recovery / Reset

The recovery flow must:
- accept a recovery request without the existing password;
- return a non-enumerating response;
- issue a single-use, expiring reset credential;
- invalidate the credential after successful use;
- invalidate existing refresh sessions after successful reset;
- record the security event.

---

## Authentication Implementation Order

### AUTH-1 — Session Foundation
1. `auth_sessions` schema and repository
2. Refresh-token hashing
3. Session creation during login
4. Session lookup and expiry
5. Session revocation

### AUTH-2 — Refresh Security
6. Refresh-token rotation
7. Token-family identifiers
8. Replay/reuse detection
9. Family-wide revocation
10. Authentication security audit events

### AUTH-3 — Session UX
11. Session listing
12. Individual session revocation
13. Logout-all
14. Frontend session-management UI

### AUTH-4 — Credential Lifecycle
15. Password-change hardening
16. Password-change session invalidation
17. Password recovery
18. Password reset
19. Password-reset session invalidation

### AUTH-5 — Abuse Protection & Verification
20. Failed-login tracking
21. Temporary account lockout
22. Authentication failure auditing
23. Comprehensive authentication integration tests
24. Frontend transient-error and session-expiry verification

---

## Documentation Status

This update changes the authentication documentation from a **basic JWT + blocklist model** to a **server-side refresh-session and rotating-token model**.

The authentication implementation should be considered complete only when the documented security invariants and corresponding integration tests are satisfied.
