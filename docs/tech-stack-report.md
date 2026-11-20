# Technology Selection Report

## Stack at a glance

| Layer | Technology | Version |
| :--- | :--- | :--- |
| Language (backend) | Java | 21 (LTS) |
| Backend framework | Spring Boot (Web, Data JPA, Security, Validation, Actuator, Scheduling) | 3.3.5 |
| Google sign-in verification | Spring Security OAuth2 JOSE (Nimbus JWT) | managed by Spring Boot |
| Database | PostgreSQL | 16 |
| Schema migrations | Flyway | managed by Spring Boot |
| Frontend | Next.js (App Router) + React + TypeScript | 15.0.3 / 18.3.1 / 5.6.3 |
| Lint and format | Biome (frontend), Spotless + google-java-format (backend) | 1.9.4 |
| Testing | JUnit 5, Spring Boot Test, MockMvc, Mockito, AssertJ, H2, ArchUnit | — |
| Packaging | Docker (multi-stage builds) + Docker Compose | — |

## 1. Why Java?

The course is about object-oriented design, and Java makes OOP concepts explicit. Interfaces,
abstract types, access modifiers and checked compile-time types turn the design (Strategy, State,
Factory, Repository and Adapter) into code you can read straight off the class diagram. Java 21 adds
records for immutable value objects (`AuditLog`, `SlaPolicy`, `IncidentFilter`) and switch
expressions, which keep the state and priority logic compact without giving up type safety.

## 2. Why Next.js / React for the frontend?

- **Different screens per role.** Employees, technicians and administrators each get their own
  navigation and pages; React components let these share building blocks (badges, filters,
  dialogs).
- **TypeScript** mirrors the backend DTOs, so a renamed field is a compile error, not a
  runtime bug.
- **App Router** gives one folder per page (`/reports`, `/audit`, `/admin/users`), which keeps
  the codebase easy to navigate.
- The output is a standalone Node server, so it ships as a small container next to the API.

## 3. Why Spring Boot for the backend?

- **Spring Security** provides authentication, role checks (`@PreAuthorize`) and OAuth/JWT
  verification without hand-written crypto.
- **Spring Data JPA** implements the Repository pattern directly; our domain ports
  (`IncidentRepository`, `SlaPolicyProvider`) are implemented by thin adapters.
- **`@Scheduled`** runs the SLA monitor without a separate job system.
- Dependency injection lets strategies and notification channels be swapped by configuration,
  which is exactly the extensibility the brief asks for.

## 4. Why PostgreSQL?

Incidents, users, services, audit records and SLA policies are strongly relational and need
transactions: assigning a ticket, writing its audit entry and queuing notifications must succeed
or fail together. PostgreSQL gives us:
- foreign keys and CHECK constraints that enforce the business rules at the database level;
- partial unique indexes, used to guarantee a single root account;
- sequences, for gap-tolerant, concurrency-safe ticket numbers (`INC-1001`, …);
- good text search (`ILIKE`) for the search box.

It also runs unchanged on managed clouds (Supabase, RDS, Cloud SQL), and the `cloud` profile is
already set up for that.

## 5. Why this architecture?

A **modular monolith with hexagonal (ports and adapters) layering**:

- `domain`: pure Java business rules: lifecycle states, priority, SLA, assignment, similarity
  and category suggestion. It has no Spring imports, and an ArchUnit test fails the build if one
  appears.
- `application`: use cases (report, assign, resolve, reports, notifications), transactions and
  authorization.
- `infrastructure`: JPA adapters, notification channels, security.
- `api`: REST controllers and DTOs.

One deployable keeps a three-to-five-person team productive and the demo simple, while the
boundaries keep each part replaceable. The notification channels, for example, could move to a
message queue without touching the domain.

## 6. Alternatives considered

| Option | Why not chosen |
| :--- | :--- |
| Node.js / Express + MongoDB | Weaker static typing for an OOP-focused course; incidents and audit logs are relational and need transactions. |
| Django / FastAPI | Good productivity, but Python's OOP (no interfaces, duck typing) shows the design patterns less clearly. |
| Microservices | Network calls, distributed transactions and more deployment overhead, with no benefit at this scale. |
| Server-side sessions | JWTs keep the API stateless and easy to scale horizontally. |
| ML model for categorisation | A keyword classifier is explainable, needs no training data, and sits behind a strategy interface so a model can replace it later. |

## 7. Advantages and limitations

**Advantages**
- Strong typing end to end (Java + TypeScript).
- Mature, well-documented frameworks with built-in security.
- The domain core is testable without a database (fast unit tests).
- Everything starts with one `docker compose up`.

**Limitations**
- The JVM's start-up time and memory use are higher than Node's or Go's.
- Reports are computed in the application from all tickets. That's fine for thousands of
  tickets, but SQL aggregates would be needed at larger scale (see §9).
- The JWT in `localStorage` and the lack of login rate limiting are documented in `security.md`.

## 8. How the stack supports object-oriented design

| OOP concept | Where it appears |
| :--- | :--- |
| Encapsulation | `Incident` changes state only through methods (`assign`, `resolve`, …) that check the lifecycle; `SlaWindow` owns its deadline maths. |
| Abstraction | `AssignmentStrategy`, `PriorityCalculationStrategy`, `SimilarityStrategy`, `CategorySuggestionStrategy`, `NotificationChannel`, `SlaPolicyProvider`. |
| Inheritance | Concrete lifecycle states implement `IncidentState` and inherit its "not allowed here" defaults; `SkillMatchAssignmentStrategy` delegates to a fallback strategy. |
| Polymorphism | The service calls `currentAssignmentStrategy.selectTechnician(...)` and `channel.send(...)` without knowing the concrete class. |
| Composition | `Incident` has an `SlaWindow` and an `IncidentState`; `IncidentFactory` is composed of a priority strategy, an SLA policy provider and a number sequence. |
| Association / aggregation | Incident ↔ reporter, assignee and affected service; technician ↔ skills. |
| Exceptions | `InvalidStateTransitionException`, `UnauthorizedOperationException`, `IncidentNotFoundException`, `UserNotFoundException`, mapped to HTTP codes in one place. |
| Collections | `EnumMap` for keyword tables, `Set` for skills, `Map` caches to avoid repeated lookups. |

The design patterns are catalogued in `design-patterns.md`.

## 9. Scaling to many more users

1. **Stateless API, more instances.** JWT sessions mean any instance can serve any request.
   Ticket numbers come from a database sequence, so they stay unique across instances.
2. **Database.** Add indexes for the hot filters (already on status, priority, service and
   creation date). Move list paging and report aggregates into SQL (`GROUP BY`) instead of
   in-memory, and add read replicas for reporting.
3. **Background work.** Run the SLA monitor on one instance (for example with a ShedLock
   database lock) and query only open tickets near their deadline.
4. **Notifications.** Publish events to a queue (RabbitMQ or Kafka) and let workers send email
   or push messages; the `NotificationChannel` interface already isolates this.
5. **Assignment state.** Round-robin and the active strategy are held in memory today. Move them
   to the database or Redis so every instance agrees.
6. **Edge.** Put a CDN in front of the frontend, rate limiting and TLS at the gateway, and
   observability (metrics, traces) through Spring Boot Actuator and OpenTelemetry.
