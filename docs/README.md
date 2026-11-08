# NSS College Management Application — Documentation

This directory contains the project documentation baseline for the B.Tech college NSS management application.

## Documents

- SRS.md — Software Requirements Specification
- ARCHITECTURE.md — System architecture
- IMPLEMENTATION_PLAN.md — phased implementation plan
- FRONTEND.md — frontend implementation
- BACKEND.md — backend implementation
- FEATURES.md — feature-wise specification
- DATABASE.md — database and ER-model contract
- TECHNICAL_RECOMMENDATIONS.md — technical evaluation and architectural recommendations
- ANALYTICS_MICROSERVICE.md — target analytics service architecture, security, API, deployment, and rollout contract
- COMMIT_CONVENTIONS.md — commit message guidelines and verification standards

## Core workflow

Users → Volunteers → Unit Memberships → NSS Units → Events → Registrations → Attendance → Service-Hour Ledger

## Technology baseline

- Frontend: React + TypeScript + Vite
- Backend: Java 21 + Spring Boot
- Database: PostgreSQL
- Cache: Redis
- Analytics (current): offline Python utility using Pandas
- Analytics (target): private FastAPI service with PostgreSQL read-only reporting access and asynchronous report jobs
- API contract: OpenAPI
- Testing: JUnit/Mockito, Testcontainers, Playwright
- Deployment: Docker
- CI/CD: GitHub Actions
