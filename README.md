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
- `docs/` — project documentation
- `infrastructure/` — deployment configuration

## Boot the infrastructure

```bash
docker compose up --build
```

The first milestone exposes health endpoints and a PostgreSQL/Redis-backed backend foundation. Feature modules will be added incrementally behind the same API boundary.
