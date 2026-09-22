# Commit Message Guidelines

To ensure maximum traceability, maintainability, and review efficiency, all commits in this repository must follow a detailed, structured format.

## Format Specification

```text
<type>(<scope>): <short imperative summary>

### Context
Why this change is needed. What problem or requirement does it address?

### Changes
- Itemized summary of modifications made across modules.
- Architectural, functional, or configuration details.

### Verification
- Detailed list of verification commands executed locally.
- Test and build results confirming zero regression or error.

### Impact / Notes
- Potential side-effects, dependencies updated, or follow-up tasks required.
```

## Types

* `feat`: New user-facing feature or domain capability.
* `fix`: Bug fix or error resolution.
* `docs`: Documentation creation or updates.
* `refactor`: Code restructuring without functional or contract changes.
* `test`: Adding or updating test suites.
* `chore`: Build scripts, dependencies, CI configuration, or tooling.
* `perf`: Performance optimization.

## Scopes

`auth`, `volunteers`, `units`, `events`, `registration`, `attendance`, `service-hours`, `reports`, `frontend`, `backend`, `database`, `analytics`, `scripts`, `infra`.

## Rules

1. **Never commit broken code**: Every commit must be fully verified (compile, lint, tests pass) prior to committing.
2. **Explicit verification record**: List actual commands executed in the commit body.
3. **No emojis**: Never use emojis in commit headers or bodies.
4. **Imperative header**: Use "add", "fix", "update", not "added", "fixing", "updated".
