# ServiceDesk

**Intelligent IT Service Management & Incident Resolution System**

Course project for **CS F213 / MAC F212 Object-Oriented Programming**, Semester I, 2026-2027.

ServiceDesk is a web application that manages the whole life of an IT incident: **report, classify,
prioritise, assign, investigate, resolve, verify, close**. A **stateless Spring Boot 3 REST API** serves a
**Next.js** client. To meet the course's object-oriented design criteria, the backend domain layer is
framework-free and checked on every build.

## Team: Group 58

| Name | ID |
| :--- | :--- |
| Anvith N | 2025A7PS0916H |
| Tanushree Bajaj | 2025A7PS0113H |
| Sai Sanvika Mulamreddy | 2025A7PS0108H |
| Gandham Aasritha | 2025A7PS0180H |

---

## Highlights

### What makes it stand out

| | |
| :--- | :--- |
| **Pure-Java domain, enforced** | All business rules live in framework-free Java. An ArchUnit test fails the build if a Spring or JPA class is imported into the domain. |
| **Eight design patterns, each earning its place** | State, Strategy, Observer, Factory, Repository/Ports, Adapter, Chain of Responsibility and Decorator, each solving a concrete problem in the app. |
| **AI with a safety net** | Gemini writes triage, quick fixes, summaries and chat. If it is missing or fails, an on-device knowledge base answers, so no feature depends on an API key. |
| **A real SLA engine** | Deadlines per priority, a clock that pauses while waiting for the reporter, early warnings, breach detection by a background job and a three-level escalation chain. |
| **No duplicate tickets** | Similar reports are flagged as you type. "This affects me too" joins an existing issue, and enough affected people raise its priority automatically. |
| **Live by default** | Server-Sent Events update open pages and raise toasts without a reload. |
| **Privacy and security by design** | Reporter identity is hidden on the public list, every endpoint checks roles on the server, and sign-in is rate limited with lockout. |
| **A disciplined pipeline** | Lefthook pre-commit and pre-push checks, three GitHub Actions jobs, CodeRabbit review on every PR and 136 automated tests. |

### Ticket workflow and SLA

- **Eight-state lifecycle** that refuses illegal moves (a ticket cannot jump from Reported to Closed),
  with an **Acknowledge** step that meets the response target.
- **Request info / provide info loop**: the technician's question is pinned for the reporter, and the SLA
  clock pauses until they reply.
- **Weighted priority** from impact, urgency, service business criticality and the number of affected
  people, with a live priority matrix on the report form and an administrator override that needs a reason.
- **SLA badges** show time left, "at risk", "overdue" and, once finished, "met target" or "missed
  target". A missed target bumps the priority once and remembers the original.
- **Escalation chain**: team lead, then administrators (with automatic reassignment), then the IT manager,
  one step per scheduler run and each step audited.
- **Technician capacity limits** (Decorator): no assignment strategy can overload a technician; manual
  over-assignment is refused with a clear message.
- **Three swappable assignment strategies** (skill match, least loaded, round robin) that an administrator
  changes at runtime.
- **Reopen** a resolved ticket, or a closed one within 14 days, with a reason; **cancel** a ticket
  reported by mistake; **rate the support** when closing.
- **Major incidents**: link tickets under a main one, and resolving it resolves them all, with the
  reporters told.
- **Service status** page: Operational, Degraded or Outage per IT service, shown on the report form as a
  "known issue" banner.

### Intelligence

- **Quick fix before you file**: appears by itself when you finish typing, and the same fix is available on
  your own open tickets.
- **Suggest details**: fills category, impact, urgency and service, and says why. It also flags text that
  is clearly not a technology problem.
- **Help chat** on every page: answers IT questions and knows your own tickets ("where is my ticket?").
- **Staff assistant**: a summary and a suggested fix generated the first time a ticket is opened, then
  saved. Similar resolved tickets are listed, so past fixes are reused.
- **Saved answers**: quick fixes and summaries from Gemini are stored in the database, so the same
  question is never sent twice.
- **On-device knowledge base**: about 28 everyday problems (Wi-Fi, VPN, printers, passwords, email,
  phishing and more) with safe steps, matched by whole-word keywords.
- **Duplicate and related-ticket detection** (Jaccard similarity) and an **explainable category
  suggestion** that shows the words it matched.
- **Safe by construction**: ticket text is treated as data, never as instructions; replies must match a
  strict JSON schema; anything unexpected falls back to the rules. Every answer says which engine wrote it.
- **Self-help deflection** is measured, so reports show how many problems were solved without a ticket.

### Collaboration and visibility

- **All issues**: a read-only list of every issue for everyone, with the reporter and internal notes
  hidden, so people can check whether a problem is already known.
- **This affects me too**: follow an existing issue instead of filing a duplicate; it appears in your
  tickets as followed, you are told when it is fixed, and the technician sees the count grow.
- **One Updates thread per ticket**: comments, questions, answers, pick-ups and resolutions in order, plus
  internal notes that employees never see.
- **Notifications**: in-app inbox with unread count, live toasts and email through Brevo.
- **Kanban board** for staff: drag a card, or use the keyboard "Move to" control, with one-click Acknowledge
  and Start on cards.
- **Team lead role**: sees every ticket and reassigns work without full administrator rights.

### Administration and reporting

- **Reports**: totals, SLA violations, technician workload, service analysis, services with correlated
  tickets, and **7, 30 and 90-day trend charts** (created vs resolved, SLA compliance, time to resolve,
  first response, escalations, self-help deflection).
- **Audit log** of every action with time, user, old and new value and a remark, plus a per-ticket timeline.
- **Settings**: assignment strategy, SLA targets per priority, incident categories (switch on or off), IT
  services with criticality, technician skills, capacity and team lead flag, and a manual SLA check.
- **Users**: create accounts with a temporary password the user must change, change roles (administrators
  can promote people to administrator; only root can change an administrator afterwards), deactivate,
  set notification emails, and a "new this week" filter to review sign-ups.
- **Root account** created from environment configuration on first start, and a warning at startup while
  demo accounts are enabled.

### Security and privacy

- JWT authentication, role-based authorisation on every endpoint, hashed passwords and input validation.
- **Google sign-in and sign-up** with optional organisation-domain restriction; Google users get their
  Google profile picture, password users get their initial.
- **Rate limiting** with token buckets per policy and `Retry-After`, plus a per-username **login
  lockout**; buttons show a countdown instead of failing silently.
- Live-update streams are capped per user and closed when an account is deactivated.
- A **secret scanner** on every commit and `.env` kept out of git.

### User experience

- **Collapsible sidebar** (a slide-in drawer on phones) that remembers your choice.
- **Light and dark themes** applied before first paint, so there is no flash.
- **Keyboard shortcuts** (`/` search, `n` new ticket, `b` board, `?` help) and full keyboard operation of
  the board.
- **Skeleton loaders**, toasts that pause on hover or focus, screen-reader live regions and labelled
  controls.
- Lists refresh live without blanking the table or losing your place.

### Engineering

- **Clean / hexagonal architecture**: API, application, domain and infrastructure layers, with ports and
  adapters.
- **Optimistic locking** on tickets, an **injected `Clock`** so every time-based rule is tested
  deterministically, and **136 automated tests** (110 documented cases plus manual checks).
- **23 versioned Flyway migrations** and **one-command Docker** setup.
- **Quality gates**: Lefthook, GitHub Actions and CodeRabbit (next section).

---

## Features

### For employees
- Register and sign in with a username and password, or with Google.
- **Report an issue** with title, description, category, impact, urgency and affected service. A live
  **priority matrix** shows the priority the server will calculate.
- **Quick fix before you file**: when you finish describing the problem, the assistant suggests steps to
  try, by itself. If it solves the problem, no ticket is needed.
- **Suggest details**: fills category, impact, urgency and service. A notice appears if the text does not
  look like an IT problem.
- **Duplicate warning** while typing, and a known-issue banner for a service that already has problems.
- **My tickets**: track status, SLA, assignee and the full Updates thread; answer the technician's
  questions; read the resolution; rate the support; reopen a resolved ticket, or a closed one within 14
  days.
- **All issues** (read-only list of every issue, reporter hidden): check whether a problem is already
  known, and click **This affects me too** instead of filing a duplicate. You follow the issue, see it in
  My tickets, and are notified when it is fixed.
- **Help chat** on every page: a Gemini-powered assistant that answers IT questions and tells you where
  your own tickets stand.
- **Service status** page: each IT service shown as Operational, Degraded or Outage.
- In-app **inbox** and live toasts.

### For technicians
- **My queue** and a **board** (Assigned, Acknowledged, In progress, Waiting on reporter, Resolved) with
  drag-and-drop or keyboard moves, and one-click Acknowledge and Start.
- Acknowledge, start work, add notes (internal or public), **request more information** (pauses the SLA
  clock), resolve with a resolution summary.
- **Assistant on every ticket**: a summary and a suggested fix appear automatically the first time you
  open it, and are saved for next time. Similar resolved tickets are shown too.
- Workload summary and per-technician **capacity limit**.
- **Team lead** technicians can see all tickets and reassign work.

### For administrators and root
- All incidents, with filters on status, priority, category, assignee, reporter, service, SLA status, date
  and free text.
- Assign and reassign, override priority with a reason, link tickets under a **major incident**.
- **Settings**: assignment strategy (skill match, least loaded, round robin, with capacity limits applied
  on top), SLA targets per priority, **incident categories** (switch on or off), IT services with business
  criticality, technician skills, capacity and team lead flag, and a manual SLA check.
- **Users**: create accounts, change roles (administrators can promote people to administrator; only root
  can change an administrator afterwards), activate or deactivate, set notification emails, review new
  sign-ups.
- **Reports**: totals, SLA violations, technician workload, service analysis, correlated services, and
  **7/30/90-day trends** (created vs resolved, SLA compliance, time to resolve, first response,
  escalations, self-help deflection).
- **Audit log** of every action with time, user, old and new state.

### Intelligent and automated features
| Feature | What it does |
| :--- | :--- |
| Weighted priority | Score from impact, urgency, service business criticality and **number of affected users**; P1 Critical to P4 Low. Rationale in `docs/requirements.md`. |
| Technician assignment | Three interchangeable strategies, wrapped by a capacity decorator; switchable at runtime. |
| SLA management | Response and resolution deadlines per priority, pause and resume while waiting on the reporter, warnings before breach, breach detection by a background job. |
| Automatic escalation | Team lead, then administrators (with automatic reassignment), then the IT manager. |
| Duplicate detection | Jaccard similarity while a ticket is written, and for suggested resolutions from past tickets. |
| Category suggestion | Keyword classifier that explains which words matched. |
| Incident correlation | Services with several open tickets are flagged; a main ticket can resolve its linked tickets together. |
| AI assistant (Google Gemini) | Triage, quick fixes, summaries and chat, behind an interface. Ticket text is treated as data, output is schema-checked, and every answer says which engine wrote it. |
| On-device knowledge base | About 28 common problems with safe steps. Answers when Gemini is missing or fails, so every feature still works without a key. |
| Saved answers | Quick fixes and summaries from Gemini are stored in the database, so the same question is not asked twice. |
| IT-relevance check | The assistant flags text that is clearly not a technology problem. |
| Notifications | In-app, email (Brevo, when configured) and live Server-Sent Events. |

### Quality and security
- JWT authentication, role-based authorisation on every endpoint, hashed passwords, input validation,
  rate limiting with login lockout, Google sign-in with optional domain restriction.
- Custom domain exceptions mapped to clear HTTP errors (invalid transition 409, overloaded technician 409,
  unauthorised 403, invalid input 400).
- **136 automated tests** (JUnit 5, Spring MockMvc on H2, Mockito, ArchUnit). 110 documented test cases
  plus manual end-to-end checks in `docs/test-cases.md`.
- Lefthook pre-commit and pre-push hooks, three GitHub Actions jobs and CodeRabbit pull request reviews (see *Engineering workflow* above).

---

## Engineering workflow and quality gates

Every change went through the same pipeline, so nothing reached `main` without being checked several
times.

1. **Branches and pull requests**: no direct pushes to `main`. Work happens on `feat/*`, `fix/*`,
   `docs/*` or `chore/*` branches and is merged through a pull request that needs a peer approval and a
   filled-in checklist (`.github/pull_request_template.md`). Commit messages follow `type: description`.
2. **Pre-commit checks (Lefthook, run in parallel)**:
   - Biome lints and formats staged frontend files and re-stages the fixes.
   - TypeScript type check (`tsc --noEmit`).
   - Spotless checks Java formatting (Google Java Format).
   - A secret scanner blocks commits that contain `.env` files, private keys or hardcoded credentials.
3. **Pre-push checks (Lefthook)**: the full backend test suite (run with local `.env` keys removed, so
   tests never touch Gemini or Brevo), plus frontend lint and type check.
4. **GitHub Actions CI** on every push and pull request, in three jobs:
   - *Backend*: Spotless check, then `mvn verify` (unit and integration tests and the ArchUnit
     domain-isolation rule).
   - *Frontend*: install from the frozen lockfile, Biome, TypeScript check and a production Next.js build.
   - *Docker*: builds the backend and frontend images, so a broken Dockerfile is caught early.
5. **Automated code review by CodeRabbit** on every pull request into `main` (`.coderabbit.yaml`): it
   posts a summary and inline comments, with extra instructions per area. In `domain/` it flags framework
   imports, weak encapsulation and illegal state transitions; in `api/` it checks DTO use, validation and
   authorisation; in `infrastructure/` it checks SQL injection and secret handling. Its findings are
   advisory: a teammate still reviews and approves, and CI remains the hard gate.

---

## Tech Stack

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3.5 | Stateless REST API (`/api/v1`), Spring Data JPA, Spring Security |
| **Domain Layer** | Pure Java 21 | Framework-free OOP model guarded by ArchUnit |
| **Database** | PostgreSQL 16 | Local via Docker Compose; cloud via Supabase Postgres |
| **Migrations** | Flyway | `V1` to `V23` (see `docs/database.md`) |
| **Frontend** | Next.js 15, React 18, TypeScript | Strict typing, App Router, sidebar layout, light and dark themes |
| **Frontend tooling** | Bun 1.x, Biome 1.9.4 | Package manager, linter and formatter |
| **AI** | Google Gemini API | Optional; rule-based fallback built in |
| **Email** | Brevo API | Optional; otherwise logged only |
| **Git hooks** | Lefthook | Lint, type check, secret scan, tests |
| **DevOps** | Docker, Docker Compose, GitHub Actions | Multi-stage builds and CI |
| **Testing** | JUnit 5, Mockito, ArchUnit | In-memory H2 tests and architecture rules |

---

## Prerequisites

1. **JDK 21** (for example [Eclipse Temurin 21](https://adoptium.net/)).
2. **Bun** >= 1.1 ([bun.sh](https://bun.sh/)), used for the frontend and Git hooks.
3. **Docker and Docker Compose** ([Docker Desktop](https://www.docker.com/)).
4. Apache Maven is optional; the repository includes the Maven wrapper.

---

## Quickstart (full stack via Docker)

1. **Clone and enter the repository**:
   ```bash
   git clone <repo-url>
   cd servicedesk
   ```

2. **Configure environment variables**:
   ```bash
   cp .env.example .env
   ```

3. **Install frontend dependencies and activate Git hooks**:
   ```bash
   cd frontend && bun install && cd ..
   ```

4. **Build and start everything**:
   ```bash
   docker compose up --build
   ```

5. **Open the app**:
   - Frontend: [http://localhost:3000](http://localhost:3000)
   - Backend health: [http://localhost:8080/api/v1/health](http://localhost:8080/api/v1/health)
   - Actuator: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
   - PostgreSQL: `localhost:5432` (`servicedesk` / `servicedesk_user` / `servicedesk_dev_password`)

### Demo accounts

All passwords are `test`. They are for local use only; deactivate them from **Users** in a real deployment.

| Username | Role | Name | Focus |
| :--- | :--- | :--- | :--- |
| `emp` | `EMPLOYEE` | Employee | Reports and follows tickets |
| `tech_net` | `TECHNICIAN` | Network Technician | `NETWORK` |
| `tech_hw` | `TECHNICIAN` | Hardware Technician | `HARDWARE` |
| `tech_lead` | `TECHNICIAN` (team lead) | Lead Technician | `NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`; sees and reassigns all tickets |
| `admin` | `ADMINISTRATOR` | Administrator | Settings, assignment rules, priority overrides, reports |

**Root account**: created on first start from `ROOT_USERNAME` / `ROOT_PASSWORD` in `.env` (at least 12
characters). Root can do everything an administrator can, and is the only role that can change or demote
an administrator.

### Optional integrations (all configured in `.env`)

- **Google sign-in**: create an OAuth 2.0 *Web application* client in Google Cloud Console, add
  `http://localhost:3000` to its *Authorized JavaScript origins*, set `GOOGLE_CLIENT_ID`, and restart the
  backend. "Continue with Google" then appears on the sign-in and sign-up pages. First-time Google users
  become employees and use their Google profile picture; password users show the first letter of their
  name. `GOOGLE_ALLOWED_DOMAINS` restricts sign-in to your organisation's domain. Every new account sends
  administrators and root a "New user" notification.
- **Assistant**: set `GEMINI_API_KEY` (Google AI Studio) to have Gemini answer. Without a key the built-in
  knowledge base answers, so every feature still works; each answer says which one produced it.
- **Email**: set `BREVO_API_KEY` and a verified `MAIL_FROM_ADDRESS` to send notification emails.
  `APP_BASE_URL` is used for links in the emails. Without a key, emails are only logged.

### Who sees what

Employees see the tickets they reported (plus issues they follow), technicians the tickets assigned to
them, team leads, administrators and root everything. Everyone can browse **All issues** read-only,
without reporter names or internal notes. Full matrix in [`docs/security.md`](docs/security.md).

> **Existing database from before Flyway `V12`?** Apply the demo password with:
> ```bash
> docker compose exec -T postgres psql -U servicedesk_user -d servicedesk < scripts/seed-demo-users.sql
> ```
> or run `docker compose restart backend` and let Flyway apply `V12__demo_password_test.sql`.

---

## Architecture and implementation overview

A clean / hexagonal architecture: **API → Application → Domain**, with **Infrastructure** adapters
plugged into domain interfaces (ports).

- **Domain (pure Java, no Spring or JPA)**: `Incident` and its states, strategies, domain events, SLA
  windows, escalation handlers and the assistant interface. `DomainIsolationTest` (ArchUnit) fails the
  build if a framework class is imported.
- **Application**: use-case services (`IncidentApplicationService`, `AssistantApplicationService`,
  `CatalogApplicationService`, `CategoryApplicationService`, `UserApplicationService`,
  `IssueBoardService`, `ReportApplicationService`, `SlaScheduledEvaluator`).
- **Infrastructure**: JPA repositories and adapters, JWT security and rate limiting, Gemini adapter,
  Brevo email, in-app notifications, live-event broadcaster.
- **API**: REST controllers, DTO records, global exception handler.

### Design patterns (8)

| Pattern | Where |
| :--- | :--- |
| State | Incident lifecycle: `ReportedState`, `AssignedState`, `AcknowledgedState`, `InProgressState`, `PendingInfoState`, `ResolvedState`, `ClosedState`, `CancelledState` |
| Strategy | Technician assignment, priority calculation, duplicate similarity, category suggestion |
| Observer | Domain events drive notifications, audit entries and live updates |
| Factory | `IncidentFactory` builds valid tickets with priority, SLA and number |
| Repository / Ports | Domain repository interfaces implemented by JPA adapters |
| Adapter | `IncidentAssistant` (Gemini and rule-based), notification channels |
| Chain of Responsibility | Escalation: team lead, administrators, IT manager |
| Decorator | `CapacityAwareAssignmentStrategy` wraps any assignment strategy |

Details: [`docs/design-patterns.md`](docs/design-patterns.md) and [`docs/architecture.md`](docs/architecture.md).

### Incident lifecycle

`REPORTED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS ⇄ PENDING_INFO → RESOLVED → CLOSED`, or `CANCELLED`.
Illegal moves (for example `REPORTED → CLOSED`) throw `InvalidStateTransitionException` and return HTTP
409. How this maps to the course brief's lifecycle is in `docs/requirements.md`.

### Default SLA targets

| Priority | Response | Resolution |
| :--- | :--- | :--- |
| P1 Critical | 5 min | 30 min |
| P2 High | 15 min | 2 h |
| P3 Medium | 1 h | 8 h |
| P4 Low | 4 h | 24 h |

Administrators can change them in **Settings**; edits apply to new tickets.

---

## Documentation

| Document | Contents |
| :--- | :--- |
| [`docs/problem-analysis.md`](docs/problem-analysis.md) | Problem, stakeholders, challenges, proposed solution |
| [`docs/requirements.md`](docs/requirements.md) | Functional and non-functional requirements, roles, business rules, lifecycle comparison |
| [`docs/architecture.md`](docs/architecture.md) | Layers, packages and runtime flows |
| [`docs/design-patterns.md`](docs/design-patterns.md) | The eight patterns, with classes |
| [`docs/tech-stack-report.md`](docs/tech-stack-report.md) | Technology choices and alternatives |
| [`docs/database.md`](docs/database.md) | Schema, constraints, migrations |
| [`docs/api-spec.md`](docs/api-spec.md) | REST endpoints and DTOs |
| [`docs/security.md`](docs/security.md) | Authentication, authorisation, rate limiting |
| [`docs/test-cases.md`](docs/test-cases.md) | Documented test cases and manual checks |
| [`docs/demo-script.md`](docs/demo-script.md) | The three demonstration workflows |
| [`docs/diagrams/`](docs/diagrams) | Use case, class, state, sequence and ER diagrams (Mermaid) |

---

## Git hooks and quality checks (Lefthook)

- **`pre-commit`**: formats staged frontend files with Biome, checks Java formatting with Spotless (Google
  Java Format), runs `tsc --noEmit`, and blocks committed secrets.
- **`pre-push`**: runs the backend tests and the frontend build checks.

```bash
# Frontend
cd frontend && bun run lint            # Biome
cd frontend && bun run lint:fix        # auto-fix
cd frontend && bun run typecheck       # strict TypeScript

# Backend formatting and secrets
bun ./scripts/run-backend-lint.ts
bun ./scripts/check-staged-secrets.ts

# Run the hook suites by hand
bun --cwd frontend run lefthook run pre-commit
bun --cwd frontend run lefthook run pre-push
```

### Backend tests

From `backend/`:
```bash
./mvnw spotless:check     # formatting
./mvnw spotless:apply     # fix formatting
./mvnw -B verify          # unit, integration and ArchUnit tests
```
Or from the repository root, with local `.env` keys kept out of the test run:
```bash
bun ./scripts/run-backend-tests.ts
```
Tests run on an embedded H2 database, need no Docker, and never call Gemini or Brevo.

### Frontend

```bash
cd frontend
bun install
bun run dev          # local dev server
bun run build        # production bundle
```

---

## Repository structure

```text
servicedesk/
├── backend/                       # Spring Boot 3 REST API
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/servicedesk/
│       │   ├── domain/            # Pure Java: no Spring or JPA imports
│       │   ├── application/       # Use-case services
│       │   ├── infrastructure/    # Persistence, security, AI, email, notifications
│       │   └── api/               # Controllers, DTOs, exception handling
│       ├── main/resources/        # application*.yml and Flyway migrations (V1 to V23)
│       └── test/                  # JUnit 5, MockMvc and ArchUnit tests
├── frontend/                      # Next.js App Router client
│   └── src/                       # Pages, components (sidebar, chat, board, charts) and typed API client
├── docs/                          # Requirements, architecture, design, tests, diagrams
├── scripts/                       # Hook and test helper scripts
├── lefthook.yml                   # Git hook configuration
├── .github/workflows/ci.yml       # GitHub Actions pipeline
├── docker-compose.yml             # Postgres, backend and frontend
├── CONTRIBUTING.md                # Workflow and domain-isolation rules
└── README.md
```

---

## Deployment notes

- **Backend (Render)**: root directory `backend`, Docker environment, health check `/api/v1/health`,
  environment variables as in `.env.example`.
- **Frontend (Vercel)**: root directory `frontend`, Next.js preset, Bun, `NEXT_PUBLIC_API_BASE_URL`
  pointing at the deployed backend.
- **Database (Supabase)**: direct connection on port 5432 (session mode, not the 6543 pooler) with
  `SPRING_PROFILES_ACTIVE=cloud`, which requires SSL.

---

## Troubleshooting

- **Frontend lint or type errors**: `bun run lint:fix`, then `bun run typecheck`.
- **`DomainIsolationTest` fails**: a class in `com.servicedesk.domain` imports Spring, JPA, or
  `infrastructure` / `api` code. Remove the import.
- **Port conflicts**: change `POSTGRES_PORT` or the backend port in your local `.env`.
- **Assistant says "built-in rules"**: no `GEMINI_API_KEY` is set (or Gemini failed). Everything still works.
