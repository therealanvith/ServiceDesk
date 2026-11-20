# ServiceDesk

Intelligent IT Service Management & Incident Resolution System. A production-grade base template for a 5-person student team building an Object-Oriented Programming (OOP) course project.

The system features a **stateless Spring Boot 3 REST API** coupled to a **Next.js (App Router)** client. To satisfy strict OOP grading criteria, the backend domain layer is completely decoupled and framework-free.

---

## Tech Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3.5 | Stateless REST API (`/api/v1`), Spring Data JPA, Security, Actuator |
| **Domain Layer** | Pure Java 21 | Framework-free OOP domain model guarded by ArchUnit |
| **Database** | PostgreSQL 16 / Supabase | Local via Docker Compose; cloud via Supabase Postgres |
| **Migrations** | Flyway | Versioned database schema evolution (`V1__baseline.sql`) |
| **Frontend** | Next.js 15, React 18, TypeScript | Strict typing (`strict`, `noUncheckedIndexedAccess`), App Router |
| **Frontend Tooling** | Bun 1.x & Biome 1.9.4 | Single fast runtime/package manager & unified linter/formatter |
| **Git Hooks** | Lefthook | Parallel pre-commit lint/typecheck/secret scanning & pre-push verification |
| **DevOps** | Docker, Docker Compose, GitHub Actions | Multi-stage production container builds, CI pipelines |
| **Testing** | JUnit 5, Mockito, ArchUnit | Fast in-memory H2 tests, architectural boundary verification |

---

## Prerequisites

Ensure the following tools are installed on your workstation:
1. **Java Development Kit (JDK) 21**: Recommended distribution: [Eclipse Temurin 21](https://adoptium.net/).
2. **Bun** (>= 1.1): [bun.sh](https://bun.sh/) (used exclusively for frontend package management, script execution, and Git hooks).
3. **Docker & Docker Compose**: [Docker Desktop](https://www.docker.com/).
4. **Apache Maven** (Optional if running via Docker/CI).

---

## Quickstart (Full Stack via Docker)

1. **Clone and navigate to repository root**:
   ```bash
   git clone <repo-url>
   cd servicedesk
   ```

2. **Configure environment variables**:
   ```bash
   cp .env.example .env
   ```

3. **Install frontend dependencies & activate Git hooks**:
   ```bash
   cd frontend && bun install && cd ..
   ```

4. **Build and launch all services**:
   ```bash
   docker compose up --build
   ```

5. **Access services & Seed Demo Accounts**:
   - Frontend UI: [http://localhost:3000](http://localhost:3000)
   - Backend Health API: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
   - Spring Boot Actuator: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - PostgreSQL Database: `localhost:5432` (`servicedesk` / `servicedesk_user` / `servicedesk_dev_password`)

   **Pre-seeded Demo Accounts** (all passwords: `test`):
   | Username | Role | Full Name | Domain Focus / Skills |
   | :--- | :--- | :--- | :--- |
   | `emp` | `EMPLOYEE` | Employee | Reports and follows their own tickets |
   | `tech_net` | `TECHNICIAN` | Network Technician | `NETWORK` |
   | `tech_hw` | `TECHNICIAN` | Hardware Technician | `HARDWARE` |
   | `tech_lead` | `TECHNICIAN` | Lead Technician | `NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS` |
   | `admin` | `ADMINISTRATOR` | Administrator | Settings, assignment rules, priority overrides |

   Demo accounts are for local use only; deactivate them from **Users** in a real deployment.

   **Root account**: created on first start from `ROOT_USERNAME` / `ROOT_PASSWORD` in `.env`
   (see `.env.example`; at least 12 characters). Root can do everything an administrator can,
   and is the only role that can grant or remove administrator access.

   **Google sign-in**: create an OAuth 2.0 *Web application* client in Google Cloud Console,
   add `http://localhost:3000` to its *Authorized JavaScript origins*, put the client id in
   `GOOGLE_CLIENT_ID` in `.env`, and restart the backend. The "Continue with Google" button
   appears automatically. First-time Google users become employees; promote them from **Users**.
   `GOOGLE_ALLOWED_DOMAINS` optionally restricts sign-in to your organisation's domain.

   The sign-up page offers "Sign up with Google" as well; every new account (either way) sends
   administrators and root a "New user" notification so they can review its role.

   **Assistant (optional)**: put a Google AI Studio key in `GEMINI_API_KEY` to have Gemini
   suggest ticket classification, self-help steps, fixes and summaries. Without a key the
   built-in rules answer, so every feature still works; each answer says which one produced it.

   **Email (optional)**: put a Brevo API key in `BREVO_API_KEY` and a verified sender in
   `MAIL_FROM_ADDRESS` to send notification emails. Without a key emails are only logged.
   `APP_BASE_URL` is used for the links in emails.

   **Who sees what**: employees see the tickets they reported, technicians the tickets assigned
   to them, administrators and root everything. Everyone can browse **All issues** read-only,
   without reporter names or internal notes. Full matrix in [`docs/security.md`](docs/security.md).

   > **Note on Existing Databases**: If you previously launched Docker containers before Flyway migration `V12`, you can immediately apply the `test` password to your running database:
   > ```bash
   > docker compose exec -T postgres psql -U servicedesk_user -d servicedesk < scripts/seed-demo-users.sql
   > ```
   > Or restart the backend service to let Flyway execute `V12__demo_password_test.sql` automatically:
   > ```bash
   > docker compose restart backend
   > ```

---

## Architecture & Implementation Overview

The system is built across four phases following strict Clean/Hexagonal Architecture:

1. **Phase 1: Pure OOP Domain Core (Framework-Free)**
   - **GoF State Pattern**: Encapsulates incident lifecycle (`ReportedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `PendingInfoState`, `ResolvedState`, `ClosedState`, `CancelledState`). Invalid transitions throw domain exceptions (HTTP 409).
   - **GoF Strategy Pattern**: Dynamic automated technician dispatching with interchangeable strategies (`SkillMatchingStrategy`, `LeastLoadedStrategy`, `RoundRobinStrategy`).
   - **GoF Observer Pattern**: Incident event dispatcher decouples side-effects (`AuditLoggerListener`, `NotificationListener`).
   - **SLA & Priority Matrices**: Urgency $\times$ Impact matrix calculates priority ($P1$–$P4$); response and resolution deadlines pause dynamically in `PENDING_INFO` and recalculate on resumption.
   - **Domain Isolation**: 100% pure Java without Spring or JPA annotations, guarded continuously by **ArchUnit** tests.

2. **Phase 2: Spring Boot 3 Backend & Persistence**
   - **Persistence Adapters**: Spring Data JPA repositories with domain entity $\leftrightarrow$ JPA entity mappers.
   - **Database Migrations**: Flyway migrations (V1 baseline through V17; see `docs/database.md`) managing PostgreSQL tables, foreign keys, and indexes.
   - **Security & RBAC**: Stateless JWT authentication with role-based access control (`EMPLOYEE`, `TECHNICIAN`, `ADMINISTRATOR`).
   - **REST API (`/api/v1`)**: Controllers for Auth, Incidents, Comments, Admin Strategy management, and SLA evaluation workers.

3. **Phase 3: Next.js 15 Client & Worklists**
   - **App Router & Strict TypeScript**: Modern Next.js UI styled with clean CSS design tokens.
   - **Role-Based Worklists**: Dynamic filtering by Status, Priority, Category, Assignee, Reporter, SLA status, and full-text search.
   - **Interactive Priority Matrix**: Real-time visual impact/urgency calculator for incident submission.
   - **Incident Lifecycle Actions**: Interactive modals for Request Info (pauses SLA), Provide Info (resumes SLA), Resolve, Reassign, Override Priority, and Close.
   - **Audit Timeline & Similar Tickets**: Real-time audit history and automated lexical similarity detection for recurring incidents.
   - **Admin Strategy Console**: Dynamic runtime switching of GoF technician assignment algorithms and capacity monitoring.

4. **Phase 4: Service desk workflow, assistant and live updates**
   - **Updates thread**: one chronological thread per ticket; comments plus entries the system writes for questions, answers, pick-up, resolution and status changes (`UpdateKind`).
   - **All issues** (`/issues`): every signed-in user can search all issues read-only, with the reporter hidden and no internal notes.
   - **Acknowledge step**: `ASSIGNED` → `ACKNOWLEDGED` → `IN_PROGRESS`; acknowledging meets the response target and tells the reporter.
   - **Technician capacity** (Decorator): `CapacityAwareAssignmentStrategy` keeps every assignment strategy within each technician's open-ticket limit; manual over-assignment is refused with 409.
   - **Escalation chain** (Chain of Responsibility): team lead, then administrators (with automatic reassignment), then the IT manager, one level per scheduler run.
   - **Rate limiting**: token buckets per policy (sign-in, assistant, writes, reads) with 429 + `Retry-After`, and a per-username sign-in lockout.
   - **Help desk assistant** (Adapter): Gemini behind the `IncidentAssistant` port for triage, self-help, fix suggestions and summaries, with a rule-based fallback.
   - **Email and live updates**: Brevo email when configured; Server-Sent Events (`/api/v1/events`) refresh open pages and show toasts.
   - **Major incidents**: link tickets under a main one; resolving it resolves them all. Service status (Operational / Degraded / Outage) from open tickets.
   - **Board** (`/board`): drag-and-drop (and keyboard "Move to") columns Assigned, Acknowledged, In progress, Waiting on reporter, Resolved (last 7 days).
   - **Trends**: 7 / 30 / 90-day charts of created vs resolved, SLA compliance, time to resolve, first response, escalations and self-help deflection.

   Patterns, endpoints and schema: [`docs/design-patterns.md`](docs/design-patterns.md), [`docs/api-spec.md`](docs/api-spec.md), [`docs/database.md`](docs/database.md).

---

## Git Hooks & Quality Checks (Lefthook)

Lefthook automatically validates your work before it enters git history:
- **`pre-commit`**: Formats staged frontend files with Biome, validates Java formatting with Spotless (Google Java Format), verifies TypeScript types (`tsc --noEmit`), and blocks any committed secrets (`.env` files, private keys, hardcoded passwords).
- **`pre-push`**: Runs backend tests (`mvn -B -q test` or `./mvnw`) and full frontend build checks before code is sent to the remote.

### Running Quality Checks Manually

You can run individual quality checks or invoke Lefthook directly:

```bash
# 1. Frontend code formatting & linting (Biome)
cd frontend && bun run lint

# Automatically fix formatting issues
cd frontend && bun run lint:fix

# 2. Frontend TypeScript type verification
cd frontend && bun run typecheck

# 3. Backend Java formatting check (Google Java Format via Spotless)
bun ./scripts/run-backend-lint.ts

# 4. Staged secrets and credentials scanner
bun ./scripts/check-staged-secrets.ts

# 5. Run full Lefthook hook suites
bun --cwd frontend run lefthook run pre-commit
bun --cwd frontend run lefthook run pre-push
```

---

## Local Development & Quality Checks

### Backend Checks
From the `backend/` directory (using `./mvnw` or system `mvn`):
```bash
# Check Java code formatting (Google Java Format)
./mvnw spotless:check

# Automatically format Java code
./mvnw spotless:apply

# Run unit, integration, and ArchUnit architecture tests
./mvnw -B verify
```
*Note*: Tests execute against an embedded H2 database and do not require Docker.

### Frontend Checks
From the `frontend/` directory (use **Bun** exclusively):
```bash
# Install dependencies
bun install

# Check formatting, lint rules, and import ordering
bun run lint

# Auto-fix formatting and safe lint problems
bun run lint:fix

# Run strict TypeScript compiler verification
bun run typecheck

# Build Next.js standalone bundle
bun run build

# Start Next.js local development server
bun run dev
```

---

## Repository Structure

```text
servicedesk/
├── backend/                       # Spring Boot 3 REST API
│   ├── Dockerfile                 # Multi-stage JRE 21 runtime container
│   ├── pom.xml                    # Maven build configuration
│   └── src/
│       ├── main/java/com/servicedesk/
│       │   ├── ServiceDeskApplication.java
│       │   ├── domain/            # PURE JAVA ONLY: No Spring/JPA imports allowed
│       │   ├── application/       # Use-case orchestrators and application services
│       │   ├── infrastructure/    # DB persistence, Security, and external adapters
│       │   └── api/               # REST endpoints, DTOs, and global exception advice
│       ├── main/resources/        # application.yml, local, cloud, and Flyway migrations
│       └── test/                  # JUnit 5, MockMvc, and ArchUnit tests
├── frontend/                      # Next.js App Router client
│   ├── Dockerfile                 # Multi-stage Bun build + Node 22 Alpine runtime
│   ├── biome.json                 # Biome formatting and linting rules
│   ├── bun.lock                   # Committed Bun lockfile
│   ├── package.json               # Frontend dependencies and scripts
│   ├── tsconfig.json              # Strict TypeScript configuration
│   └── src/                       # UI components, layout, and typed API client
├── docs/                          # Architecture, requirements, and design documentation
├── mobile/                        # Optional mobile client stretch goal
├── scripts/                       # Developer & Git hook scripts
├── lefthook.yml                   # Git hook configuration
├── .github/workflows/ci.yml       # GitHub Actions CI pipeline
├── docker-compose.yml             # Postgres, backend, and frontend service composition
├── CONTRIBUTING.md                # Team workflow, PR rules, and domain isolation invariants
└── README.md
```

---

## Deployment Notes (Configuration Targets)

- **Backend (Render)**:
  - Root Directory: `backend`
  - Environment: Docker
  - Health check path: `/api/v1/health`
  - Provide environment variables matching `.env.example`.
- **Frontend (Vercel)**:
  - Root Directory: `frontend`
  - Framework Preset: Next.js
  - Package Manager: Bun
  - Environment variable: `NEXT_PUBLIC_API_BASE_URL` (pointing to deployed backend API).
- **Database (Supabase)**:
  - Connect using Direct Connection on port 5432 (Session mode, not the port 6543 transaction pooler).
  - Use `SPRING_PROFILES_ACTIVE=cloud` which enforces SSL encryption (`sslmode=require`).

---

## Troubleshooting

- **Frontend lint or type errors**: Run `bun run lint:fix` to auto-format files. Inspect any remaining TypeScript errors with `bun run typecheck`.
- **Domain layer ArchUnit failure**: If `DomainIsolationTest` fails, ensure no classes inside `com.servicedesk.domain` import Spring (`org.springframework.*`), JPA (`jakarta.persistence.*`), or classes from `infrastructure` or `api`.
- **Docker Compose port conflicts**: If port `5432` or `8080` is in use, modify `POSTGRES_PORT` or backend ports in your local `.env`.
