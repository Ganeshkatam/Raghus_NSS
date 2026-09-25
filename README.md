# National Service Scheme (NSS) Management Platform

A high-reliability, cloud-native enterprise management platform engineered for National Service Scheme (NSS) operations within B.Tech engineering colleges.

---

## 1. System Overview

The platform digitizes, automates, and audits end-to-end NSS institutional operations for higher education institutions:
- **Volunteer Life Cycle**: Centralized demographic dossiers, academic roll number mapping, branch progression, emergency contacts, and active status tracking.
- **Unit Organization**: Multi-unit management (e.g. Unit 1, Unit 2) with assigned faculty Programme Officers and annual enrollment lists.
- **Community Events**: Event lifecycle management (`DRAFT` -> `PUBLISHED` -> `OPEN` -> `IN_PROGRESS` -> `COMPLETED`) with capacity enforcement and self-registration.
- **Cryptographic QR Attendance**: High-concurrency, anti-buddy-punching attendance terminals powered by time-rotating HMAC-SHA256 tokens and manual supervisor overrides.
- **Service Hour Ledger**: Append-only transaction ledger with supervisory approval queues and automated tracking toward the mandatory 120-hour graduation milestone.
- **Communication & Bullets**: Scoped bulletin boards with priority levels (`NORMAL`, `HIGH`, `URGENT`) and real-time in-app notification center.
- **Institutional Reporting**: Multi-dimensional participation metrics and one-click CSV data exports for University NSS Cell compliance.

---

## 2. Technology Stack & Deployment Topology

| Tier | Component | Technology / Runtime | Responsibility |
| :--- | :--- | :--- | :--- |
| **Frontend** | Single Page Application (SPA) | React 18, TypeScript 5, Vite, React Router 6, Tailwind CSS | Client UI, role-tailored workspaces, QR scanning terminal, accessible forms |
| **Backend** | Microservice REST API | Java 21, Spring Boot 3.3.4, Spring Security, Hibernate 6 | Business logic, JWT auth, RBAC method security, transactional boundaries |
| **Database** | Relational Persistence | PostgreSQL 17 (Supabase Managed) | 3NF normalized relational schema, Flyway migrations, ACID transactions |
| **Cache & Revocation** | In-Memory Store | Redis 8 (Alpine) | JWT blocklist, short-lived QR attendance tokens, bulletin caching |
| **Analytics Engine** | Data Processing Service | Python 3.13, Pandas, ReportLab | Offline institutional report compilation and batch data reconciliation |
| **Orchestration** | Container Runtime | Docker, Docker Compose | Local multi-container development and CI verification |

---

## 3. Role-Based Access Control (RBAC) & Capability Matrix

The platform enforces a five-tier hierarchical security model across 19 atomic capabilities:

| Role Name | Scope & Responsibility | Assigned Capabilities Count |
| :--- | :--- | :--- |
| **`ADMIN`** | System Administrator, Dean, NSS Chairman | 19 / 19 capabilities (Full system administration) |
| **`FACULTY_COORDINATOR`** | College NSS Advisory Committee Staff | 17 / 19 capabilities (Institution-wide view & approvals) |
| **`PROGRAMME_OFFICER`** | Faculty in charge of a specific NSS Unit | 16 / 19 capabilities (Unit management, events, attendance, hour approvals) |
| **`STUDENT_LEADER`** | Appointed Senior Volunteer Lead | 11 / 19 capabilities (Event help, manual check-in assistance, broadcasts) |
| **`VOLUNTEER`** | Enrolled Student Volunteer | 9 / 19 capabilities (Self-profile, registration, QR check-in, hour claims) |

---

## 4. Comprehensive Documentation Index

All architectural specifications, database schemas, feature requirements, and UI/UX design tokens derived from institutional engineering reports are catalogued in `docs/`:

- [Software Requirements Specification (SRS)](file:///e:/REC_NSS/docs/SRS.md): Complete institutional requirements catalog covering 30 Functional Requirements (FR-001 to FR-030), 12 Non-Functional Requirements (NFR-001 to NFR-012), actor taxonomies, and traceability matrix.
- [System Architecture Report](file:///e:/REC_NSS/docs/ARCHITECTURE.md): Multi-tier deployment topology, component interactions, security envelopes, data consistency boundaries, fault tolerance, and trade-off analyses.
- [Backend Implementation Specification](file:///e:/REC_NSS/docs/BACKEND.md): Spring Boot 3 modular design, RESTful API contracts, global exception handling, database transaction management, and automated test strategies.
- [Database & ER Model Specification](file:///e:/REC_NSS/docs/DATABASE.md): Third Normal Form (3NF) relational schema, Mermaid ER diagrams, column specifications, UUID primary key design, performance indexes, and Flyway migration catalogue.
- [Feature-Wise Implementation Specification](file:///e:/REC_NSS/docs/FEATURES.md): Technical specifications for all 25 features across IAM, Volunteer Management, Events, Attendance, Service Hours, Communications, and Administration.
- [Frontend Architecture & UI/UX Specification](file:///e:/REC_NSS/docs/FRONTEND.md): React/TypeScript client architecture, design system tokens (typography, color palettes, spacing), component patterns, URL state management, and WCAG 2.1 AA accessibility guidelines.

---

## 5. Repository Layout

```text
REC_NSS/
├── backend/            # Spring Boot 3.3.4 REST API (Java 21)
│   ├── src/main/java/  # Layered architecture (domain, repository, service, web, security)
│   ├── src/main/resources/db/migration/ # Versioned Flyway SQL migrations (V1 to V12)
│   └── src/test/       # Unit and integration test suites
├── frontend/           # React 18 + TypeScript + Vite SPA
│   ├── src/components/ # Reusable UI component library (AppLayout, Navbar, etc.)
│   ├── src/context/    # AuthContext, dynamic capability guards (can, hasCapability)
│   ├── src/pages/      # Route pages (Dashboard, Volunteers, Units, Events, Attendance, etc.)
│   └── src/services/   # Centralized API clients with bearer token injection
├── analytics/          # Python 3.13 reporting and data processing service
├── docs/               # In-depth architectural specifications and diagrams
│   └── architecture/   # Source institutional PDF reports and reference material
├── scripts/            # Cross-platform development and deployment automation
└── docker-compose.yml  # Local multi-service development environment
```

---

## 6. Quick Start & Developer Workflows

### Prerequisites
- Java 21 JDK
- Node.js 20+ and npm
- Docker and Docker Compose (for local PostgreSQL/Redis)

### Local Environment Setup
```bash
# Clone and setup development dependencies
./scripts/setup.sh

# Spin up local infrastructure (PostgreSQL 17, Redis 8)
docker compose up -d postgres redis

# Run Spring Boot backend (from backend directory)
cd backend
./gradlew bootRun

# Run React frontend (from frontend directory)
cd frontend
npm install
npm run dev
```

### Verification & Testing
```bash
# Execute backend test suite (30 unit & integration tests)
cd backend
./gradlew test

# Execute frontend type-checking and production build
cd frontend
npm run build
```

---

## 7. Production Health & Observability

- **API Base URL**: `https://api-nss.onrender.com`
- **Frontend URL**: `https://recnss.vercel.app`
- **Health Telemetry**: `GET /actuator/health`

