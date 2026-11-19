# OOP Design Patterns Catalog

**Project**: BITS ServiceDesk: Intelligent IT Service Management & Incident Resolution System

This document details the Gang of Four (GoF) design patterns applied within ServiceDesk, explaining the architectural problem solved, classes involved, and adherence to SOLID principles.

---

## 1. State Pattern (Behavioral)

### Problem
Incidents transition through a strict, multi-step lifecycle (`REPORTED`, `ASSIGNED`, `IN_PROGRESS`, `PENDING_INFO`, `RESOLVED`, `CLOSED`, `CANCELLED`). Without the State pattern, methods like `assign()`, `startWork()`, or `resolve()` devolve into fragile, deeply nested `if (status == ...)` blocks, making forbidden transitions difficult to enforce and audit.

### Implementation
- **State Interface**: `com.servicedesk.domain.incident.state.IncidentState` defines behavior for all potential transition triggers (`assign()`, `startWork()`, `requestInfo()`, `provideInfo()`, `resolve()`, `close()`, `cancel()`). Default implementations throw `IllegalStateException`.
- **Concrete States**:
  - `ReportedState`: Allows assignment and cancellation.
  - `AssignedState`: Allows starting work, reassignment, and cancellation.
  - `InProgressState`: Allows requesting info, resolution, and reassignment.
  - `PendingInfoState`: Allows providing info (resuming `InProgressState`), pauses SLA resolution timers.
  - `ResolvedState`: Allows closure or reopening.
  - `ClosedState` & `CancelledState`: Terminal states. All mutating operations throw `IllegalStateException`.
- **Context**: `com.servicedesk.domain.incident.Incident` delegates lifecycle actions directly to `this.currentState`.

---

## 2. Strategy Pattern (Behavioral)

### Problem
Triage, assignment, and intelligence algorithms must adapt dynamically based on organization policy, business hours, and incident category without altering domain models or recompiling the codebase.

### Implementations

#### A. Automated Technician Assignment (`AssignmentStrategy`)
- **Strategy Interface**: `com.servicedesk.domain.assignment.AssignmentStrategy`
- **Concrete Strategies**:
  1. `RoundRobinAssignmentStrategy`: Sequential distribution using an atomic counter.
  2. `LeastLoadedAssignmentStrategy`: Evaluates active, non-closed ticket load per technician and assigns to the technician with the lowest burden.
  3. `SkillMatchAssignmentStrategy`: Matches ticket category (`NETWORK`, `HARDWARE`, etc.) against technician skill sets, falling back to least-loaded for ties.
- **Runtime Swapping**: Administrators can switch the active strategy at runtime via `/api/v1/admin/config/assignment`.

#### B. Dynamic Priority Calculation (`PriorityCalculationStrategy`)
- **Strategy Interface**: `com.servicedesk.domain.priority.PriorityCalculationStrategy`
- **Concrete Strategies**:
  - `MatrixPriorityStrategy`: Maps a $3 \times 3$ grid of `Impact` $\times$ `Urgency` to `P1_CRITICAL`, `P2_HIGH`, `P3_MEDIUM`, and `P4_LOW`.
  - `WeightedPriorityStrategy` (active): scores $2 \times$ impact $+$ urgency $+$ the affected service's `BusinessCriticality` adjustment. With a medium-criticality service it reproduces the matrix exactly; critical services push tickets up, low-criticality ones down.
- **Open/closed in practice**: the interface gained a three-argument `calculatePriority(impact, urgency, criticality)` as a *default method* that falls back to the two-argument version, so `MatrixPriorityStrategy` kept working unchanged when business criticality was introduced.

#### C. Intelligent Duplicate & Similarity Detection (`SimilarityStrategy`)
- **Strategy Interface**: `com.servicedesk.domain.intelligence.SimilarityStrategy`
- **Concrete Strategy**:
  - `JaccardSimilarityStrategy`: Tokenizes incident titles and descriptions, computes set intersection over union, and scores similarity from $0.0$ to $1.0$. Used both for duplicate warnings while a ticket is written and for "related tickets" with known fixes.

#### D. Category Suggestion (`CategorySuggestionStrategy`)
- **Strategy Interface**: `com.servicedesk.domain.intelligence.CategorySuggestionStrategy`
- **Concrete Strategy**:
  - `KeywordCategoryClassifier`: scores each category by domain keywords in the text (title words count double) and returns the category, a confidence and the matched words, so the suggestion is explainable. A trained model could replace it behind the same interface.

#### E. SLA Targets (`SlaPolicyProvider`)
- **Port / Strategy**: `com.servicedesk.domain.sla.SlaPolicyProvider` supplies the `SlaPolicy` (response and resolution targets) for a priority.
- **Implementations**: `SlaPolicyProvider.defaults()` (built-in targets, used in unit tests) and `JpaSlaPolicyProvider` (administrator-configured targets from the `sla_policies` table). `IncidentFactory` depends only on the interface.

---

## 3. Observer Pattern (Behavioral)

### Problem
When an incident is created, transitions states, or breaches SLA deadlines, multiple independent downstream side effects must occur (audit logging, email dispatch, web push notification) without coupling the core domain model to messaging infrastructure or persistence.

### Implementation
- **Event Contracts**: `DomainEvent` with concrete events:
  - `IncidentCreatedEvent`
  - `IncidentStatusChangedEvent`
  - `IncidentAssignedEvent`
  - `SlaBreachedEvent`
- **Publisher**: `com.servicedesk.domain.event.DomainEventPublisher`
- **Observers / Listeners**:
  - `AuditLogEventListener`: Captures every domain event and appends an immutable record to `AuditLogRepository`.
  - `NotificationEventListener`: Fans out the event to all registered `NotificationChannel` implementations.

---

## 4. Factory Pattern (Creational)

### Problem
Instantiating an `Incident` involves computing initial priority from impact/urgency, calculating response/resolution deadlines from SLA policy, generating sequential ticket numbers (`INC-XXXX`), setting the initial state to `ReportedState`, and generating creation events. Exposing this complex construction to callers risks inconsistent aggregate state.

### Implementation
- **Factory Class**: `com.servicedesk.domain.incident.IncidentFactory`
- **Collaborators**: Injected with `PriorityCalculationStrategy` and `java.time.Clock`.
- **Factory Method**:
  ```java
  public Incident createIncident(
      String title,
      String description,
      Category category,
      Impact impact,
      Urgency urgency,
      UserId reporterId) { ... }
  ```
- **Benefit**: Guarantees that every new `Incident` begins with an initialized `SlaWindow`, valid `ReportedState`, calculated `Priority`, and consistent timestamps.

---

## 5. Repository Pattern & Hexagonal Ports (Architectural / Structural)

### Problem
The domain layer must remain pure Java 21, completely independent of Spring Data JPA, Hibernate, or SQL queries, while still accessing persistent storage.

### Implementation
- **Domain Ports (Interfaces in `com.servicedesk.domain`)**:
  - `IncidentRepository`
  - `UserRepository`
  - `AuditLogRepository`
- **Infrastructure Adapters (Classes in `com.servicedesk.infrastructure.persistence.adapter`)**:
  - `JpaIncidentRepositoryAdapter implements IncidentRepository`
  - `JpaUserRepositoryAdapter implements UserRepository`
  - `JpaAuditLogRepositoryAdapter implements AuditLogRepository`
- **Benefit**: ArchUnit verifies that domain classes never reference JPA entities or Spring Data interfaces.

---

## 6. Adapter Pattern (Structural)

### Problem
The system needs to dispatch notifications across disparate channels (In-App Database records, SMTP Email servers, Console logs) using a uniform interface.

### Implementation
- **Target Interface**: `com.servicedesk.domain.notification.NotificationChannel`
- **Adapters**:
  - `InAppNotificationChannelAdapter`: Converts domain `NotificationMessage` into database entity records.
  - `EmailGatewayAdapter`: Formats domain `NotificationMessage` into MIME emails (or console logs in dev/test profiles).
