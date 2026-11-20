# ServiceDesk: Developer & Stakeholder Handoff Guide

> **Project**: Intelligent IT Service Management & Incident Resolution System  
> **Target**: Academic Object-Oriented Programming (OOP) Showcase & Production-Grade Service Desk  
> **Stack**: Java 21 | Spring Boot 3.3.5 | PostgreSQL 16 / Supabase | Next.js 15 (App Router) | Bun & Biome | Docker  

---

## 1. Executive Summary & Core Essence

**ServiceDesk** is an enterprise-grade IT Service Management (ITSM) and incident triage platform engineered to demonstrate clean architecture, strict Object-Oriented design patterns, and decoupled micro-service principles.

### The Architectural "Essence"
1. **Pure Framework-Free Domain Core**: The business logic inside `com.servicedesk.domain` is implemented in 100% pure Java 21 without any dependencies on Spring Boot, Jakarta/JPA, Hibernate, or web annotations. It is guarded against leakage by automated **ArchUnit** architecture boundary tests.
2. **Hexagonal (Ports & Adapters) Structure**: The application core interacts with databases, notifications, and external frameworks through abstract domain ports (`IncidentRepository`, `UserRepository`, `NotificationChannel`).
3. **Pluggable & Extensible Behavior**: State transitions, ticket assignments, priority calculations, and incident intelligence algorithms are dynamically pluggable through GoF behavioral patterns without recompiling core domain entities (SOLID Open/Closed Principle).
4. **Deterministic Time**: All SLA and lifecycle calculations rely on an injected `java.time.Clock`, ensuring fully reproducible test scenarios, freezeable timestamps, and predictable breach evaluations.

---

## 2. Comprehensive Feature Matrix

### 2.1 User Management & Role-Based Access Control (RBAC)
- **Role Hierarchy**:
  - `EMPLOYEE`: Report incidents, view own submissions, provide requested information, verify and close resolved tickets, add comments.
  - `TECHNICIAN`: View incident worklists, assign/claim tickets, start work (`IN_PROGRESS`), request clarification (`PENDING_INFO`), resolve tickets with diagnostic notes, view similar incident resolutions.
  - `ADMINISTRATOR`: Full control over all tickets, manual priority overrides, runtime assignment strategy switching, technician skill management, and on-demand SLA batch evaluation.
- **Authentication**: Stateless JSON Web Token (JWT) with secure password hashing (`BCrypt`) and React `AuthContext` managing authorization state across the frontend.

### 2.2 Incident Lifecycle & State Machine
Enforced strictly through the **GoF State Pattern** across seven distinct lifecycle states:
```
 [REPORTED] ────► [ASSIGNED] ────► [IN_PROGRESS] ◄────► [PENDING_INFO]
     │                 │                 │                    (SLA Paused)
     ▼                 │                 ▼
[CANCELLED]            └───────────► [RESOLVED]
                                         │
                                         ▼
                                      [CLOSED]
```
- **Lifecycle Guarantees**:
  - Direct or invalid transitions (e.g. `REPORTED` $\to$ `RESOLVED`) are programmatically forbidden and raise `InvalidStateTransitionException`.
  - Stored state in database is cleanly mapped back to domain state instances via factory mapping.
  - Terminal states (`CLOSED`, `CANCELLED`) reject all subsequent modification attempts.

### 2.3 Intelligent Priority Calculation (Matrix Strategy)
- Derives priority automatically from **Impact** (organizational scope: `LOW`, `MEDIUM`, `HIGH`) and **Urgency** (time criticality: `LOW`, `MEDIUM`, `HIGH`).
- Dynamic mapping to standard ITIL priority tiers:
  - `P1_CRITICAL`: High Impact + High/Medium Urgency
  - `P2_HIGH`: High Impact + Low Urgency OR Medium Impact + High Urgency
  - `P3_MEDIUM`: Medium Impact + Medium/Low Urgency OR Low Impact + High Urgency
  - `P4_LOW`: Low Impact + Medium/Low Urgency
- Frontend provides an interactive live 3x3 priority preview matrix as users select impact/urgency options.
- Authorized Lead Technicians and Administrators can perform audited priority overrides with mandatory reason remarks.

### 2.4 Automated Technician Assignment Engine
Pluggable technician allocation strategies implementing `AssignmentStrategy`:
1. **Round Robin (`RoundRobinAssignmentStrategy`)**: Sequential rotation among active technicians using an atomic counter.
2. **Least Loaded (`LeastLoadedAssignmentStrategy`)**: Evaluates active, non-closed ticket load across technicians and automatically assigns the incident to the technician with the lowest queue depth.
3. **Skill Match (`SkillMatchAssignmentStrategy`)**: Matches incident category (`NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`) with verified technician skill profiles, falling back to least-loaded technician if ties occur.
- **Hot-Swapping**: The active strategy can be swapped at runtime by an administrator without application restart via `/api/v1/admin/config/assignment`.

### 2.5 Intelligent Duplicate & Similarity Detection
- Utilizes the **GoF Strategy Pattern** (`SimilarityStrategy`) featuring **Jaccard Similarity (`JaccardSimilarityStrategy`)**.
- Tokenizes incoming ticket title and description, computes intersection-over-union coefficient against closed historical tickets.
- Flags similarity matches ($\ge 0.60$), surfacing previous incidents along with their verified resolution notes directly to the triage technician.

### 2.6 SLA Management & Clock-Pausing "Needs-Info" Loop
- **Dynamic SLA Windows**:
  - `P1_CRITICAL`: 15-minute response deadline, 2-hour resolution deadline.
  - `P2_HIGH`: 30-minute response deadline, 8-hour resolution deadline.
  - `P3_MEDIUM`: 2-hour response deadline, 24-hour resolution deadline.
  - `P4_LOW`: 4-hour response deadline, 72-hour resolution deadline.
- **Smart Pause Mechanism**: Moving an incident to `PENDING_INFO` pauses the resolution SLA timer. When the employee responds, SLA timing resumes without penalizing technician resolution metrics for customer wait time.
- **Automated Escalation**: When resolution deadline breaches, background scheduler dispatches `SlaBreachedEvent`, bumping priority up one tier (e.g. P3 $\to$ P2, P2 $\to$ P1) and alerting administration.

### 2.7 Multi-Channel Notifications & Observer Dispatch
- Driven by `DomainEventPublisher` with decoupled observers:
  - `IncidentCreatedEvent`, `IncidentAssignedEvent`, `IncidentStatusChangedEvent`, `SlaBreachedEvent`.
- Channel adapters:
  - **In-App Channel**: Writes unread notifications into `notifications` table, delivered to UI navbar badge.
  - **Email Gateway**: Formats and logs outbound notifications (SMTP production-ready).

### 2.8 Comprehensive Audit Trail & Collaboration
- **Immutable Audit History**: Every state change, priority override, assignment, and cancellation writes an immutable row into `incident_audit_logs` storing `actor_id`, `action`, `old_value`, `new_value`, `remarks`, and exact timestamp.
- **Diagnostic Threading**: Threaded discussion on incidents between employees and support engineers.

### 2.9 Search, Worklists & Administration Console
- **Worklist Filters**: Multi-attribute filtering by Status, Priority, Category, Assigned Technician, and SLA Breach condition, plus free-text search.
- **Admin Dashboard (`/admin`)**:
  - Real-time operational KPI counters (Active incidents, breaches, resolved count).
  - Runtime Assignment Strategy toggle.
  - Technician Skill Management (Add/remove skills across categories, toggle technician active state).
  - Trigger instant SLA batch evaluation.

---

## 3. Gang of Four (GoF) & Architectural Patterns Summary

| Pattern | Type | Project Implementation | Key Classes |
| :--- | :--- | :--- | :--- |
| **State** | Behavioral | Strict incident lifecycle management | `IncidentState`, `ReportedState`, `InProgressState`, `PendingInfoState`, `ResolvedState`, etc. |
| **Strategy** | Behavioral | Pluggable assignment, priority calculation, similarity scoring | `AssignmentStrategy`, `PriorityCalculationStrategy`, `SimilarityStrategy`, `JaccardSimilarityStrategy` |
| **Observer** | Behavioral | Decoupled side effects for audit logging & notifications | `DomainEventPublisher`, `DomainEventListener`, `DomainEvent` |
| **Factory** | Creational | Encapsulated aggregate construction with SLAs & state | `IncidentFactory` |
| **Adapter (Ports & Adapters)** | Structural | Database persistence & multi-channel notification decoupling | `JpaIncidentRepositoryAdapter`, `InAppNotificationChannelAdapter`, `EmailGatewayAdapter` |
| **Repository** | Architectural | Domain persistence abstraction | `IncidentRepository`, `UserRepository`, `AuditLogRepository` |

---

## 4. Pre-Seeded Evaluation Accounts

All accounts use the default password: **`test`**

| Username | Role | Full Name | Skills / Purpose |
| :--- | :--- | :--- | :--- |
| `emp` | `EMPLOYEE` | Employee | Reports and follows their own tickets |
| `tech_net` | `TECHNICIAN` | Network Technician | `NETWORK` |
| `tech_hw` | `TECHNICIAN` | Hardware Technician | `HARDWARE` |
| `tech_lead` | `TECHNICIAN` | Lead Technician | `NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS` |
| `admin` | `ADMINISTRATOR` | Administrator | Settings, assignment rules, priority overrides |

---

## 5. Quickstart Guide

### 5.1 Using Docker Compose (Full Stack)
```bash
# 1. Clone & copy environment template
cp .env.example .env

# 2. Launch PostgreSQL, Spring Boot backend, and Next.js frontend
docker compose up --build
```
- Frontend UI: `http://localhost:3000`
- Backend REST API: `http://localhost:8080/api/v1`
- Spring Actuator: `http://localhost:8080/actuator/health`

### 5.2 Standalone Local Development
```bash
# Backend (Java 21 + Maven)
cd backend
./mvnw clean spring-boot:run

# Frontend (Bun 1.x)
cd frontend
bun install
bun run dev
```

### 5.3 Quality & Verification Commands
- **Seed / Reset Demo Passwords**: `docker compose exec -T postgres psql -U servicedesk_user -d servicedesk < scripts/seed-demo-users.sql` (sets all demo accounts to `test`)
- **Backend Architecture & Unit Tests**: `cd backend && ./mvnw test verify` (runs ArchUnit domain boundary checks and unit tests).
- **Backend Code Formatting**: `cd backend && ./mvnw spotless:apply`
- **Frontend Linter & Formatter**: `cd frontend && bun run lint:fix`
- **Frontend Strict TypeScript Check**: `cd frontend && bun run typecheck`
- **Run Git Pre-commit Hooks**: `bunx lefthook run pre-commit`

---

## 6. Directory Layout Reference

```text
oops/
├── backend/                       # Spring Boot 3 REST API
│   ├── src/main/java/com/servicedesk/
│   │   ├── domain/                # PURE JAVA: State, Strategy, Observer, Factory
│   │   │   ├── assignment/        # Assignment strategies (RoundRobin, LeastLoaded, SkillMatch)
│   │   │   ├── incident/          # Incident aggregate, Factory, and State pattern classes
│   │   │   ├── intelligence/      # Jaccard similarity implementation
│   │   │   ├── priority/          # Matrix priority strategy
│   │   │   └── repository/        # Abstract domain ports (interfaces)
│   │   ├── application/           # Application use-case orchestrators & SLA scheduler
│   │   ├── infrastructure/        # Spring Data JPA adapters, Security JWT, DB Entities
│   │   └── api/                   # REST Controllers, DTOs, and Exception Advice
│   └── src/test/                  # ArchUnit architecture tests & JUnit 5 tests
├── frontend/                      # Next.js 15 App Router Frontend
│   └── src/
│       ├── app/                   # App Router pages (/, /login, /incidents, /admin)
│       ├── components/            # Priority matrix, State actions, Similar incidents, Timeline
│       ├── context/               # AuthContext (JWT session management)
│       └── lib/                   # Typed API client
├── docs/                          # Architecture specs, UML diagrams, evaluation demo scripts
└── docker-compose.yml             # Local production stack configuration
```
