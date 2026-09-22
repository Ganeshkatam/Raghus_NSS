# Raghus NSS

NSS management platform for a B.Tech college.

## Stack

- Frontend: React + TypeScript
- Backend: Java 21 + Spring Boot
- Database: PostgreSQL 17
- Cache: Redis 8
- Analytics/reporting: Python 3.13
- Local orchestration: Docker Compose

## Repository layout

- `frontend/` — web client
- `backend/` — Spring Boot REST API
- `analytics/` — Python reporting/analytics service
- `database/` — SQL migrations and seed data
- `scripts/` — development, testing, database, and CI automation scripts
- `docs/` — project documentation
- `infrastructure/` — deployment configuration

## Quick Start & Scripts

### Setup & Development

```bash
# Setup dependencies and environment
./scripts/setup.sh

# Start development stack
./scripts/dev.sh

# Verify health
./scripts/health-check.sh
```

*(On Windows PowerShell, use `.\scripts\setup.ps1`, `.\scripts\dev.ps1`, etc.)*

### Testing & CI

```bash
# Run tests across backend, frontend, and analytics
./scripts/test.sh

# Run linting and code-quality checks
./scripts/lint.sh

# Run local CI pipeline
./scripts/ci.sh
```

### Database Operations

```bash
# Apply pending migrations
./scripts/db-migrate.sh

# Reset development database
./scripts/db-reset.sh

# Load seed data
./scripts/seed.sh
```

## Boot the infrastructure directly

```bash
docker compose up --build
```

The first milestone exposes health endpoints and a PostgreSQL/Redis-backed backend foundation. Feature modules will be added incrementally behind the same API boundary.
