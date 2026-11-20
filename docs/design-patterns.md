# OOP Design Patterns Catalog

**Project**: BITS ServiceDesk: Intelligent IT Service Management & Incident Resolution System

This document details the Gang of Four (GoF) design patterns applied within ServiceDesk, explaining the architectural problem solved, classes involved, and adherence to SOLID principles.

---

## 1. State Pattern (Behavioral)

### Problem
Incidents transition through a strict, multi-step lifecycle (`REPORTED`, `ASSIGNED`, `ACKNOWLEDGED`, `IN_PROGRESS`, `PENDING_INFO`, `RESOLVED`, `CLOSED`, `CANCELLED`). Without the State pattern, methods like `assign()`, `startWork()`, or `resolve()` devolve into fragile, deeply nested `if (status == ...)` blocks, making forbidden transitions difficult to enforce and audit.

### Implementation
- **State Interface**: `com.servicedesk.domain.incident.state.IncidentState` defines behavior for all potential transition triggers (`assign()`, `acknowledge()`, `startWork()`, `requestInfo()`, `provideInfo()`, `resolve()`, `resolveWithParent()`, `close()`, `cancel()`, `reopen()`). Default implementations throw `InvalidStateTransitionException` (HTTP 409).
- **Concrete States**:
  - `ReportedState`: Allows assignment and cancellation.
  - `AssignedState`: Allows acknowledging, starting work straight away (which also counts as acknowledging), reassignment, and cancellation.
  - `AcknowledgedState`: The assignee has accepted the ticket (this meets the response target) but not started. Allows starting work and cancellation; reassigning moves it back to `AssignedState`, because the new technician has not accepted it yet.
  - `InProgressState`: Allows requesting info, resolution, and reassignment (back to `AssignedState`).
  - `PendingInfoState`: Allows providing info (resuming `InProgressState`), pauses SLA resolution timers.
  - `ResolvedState`: Allows closure or reopening.
  - `ClosedState` & `CancelledState`: Terminal states. All mutating operations throw `InvalidStateTransitionException`.
- **Resolve with parent**: when a major incident is resolved, each linked ticket gets `resolveWithParent(summary)`. Every open state (`Reported`, `Assigned`, `Acknowledged`, `InProgress`, `PendingInfo`) accepts it, so a linked ticket closes out whatever stage it had reached; the shared steps live in the package-private helper `ParentResolution`. `PendingInfoState` first resumes the paused SLA clock so the pause is counted correctly. Resolved and terminal states refuse it.
- **Context**: `com.servicedesk.domain.incident.Incident` delegates lifecycle actions directly to `this.currentState`.
- **Why it paid off**: adding `ACKNOWLEDGED` meant one new class and one new method on the interface (with a refusing default). No existing state had to learn about it except `AssignedState`, which leads into it, and `resolveWithParent` only had to be enabled in the open states.

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

### Live updates in the browser (Server-Sent Events)
- **Subject**: `com.servicedesk.infrastructure.live.LiveEventBroadcaster` keeps, per user, the set of open `SseEmitter` streams. `subscribe(userId)` registers one (opened by `GET /api/v1/events`); `send(userId, event, data)` notifies every stream of that user, so several tabs all update. A stream that fails to write is dropped at once, and a heartbeat comment every 25 s keeps proxies from closing idle streams.
- **Observers**: each open browser tab. Two named events are pushed:
  - `notification`: sent by `LiveNotificationChannelAdapter`, which is just another `NotificationChannel`, so every notification the app already produces reaches open tabs with no change to the code that raises it.
  - `incident-updated`: sent by `IncidentApplicationService.broadcastChange()` to the reporter, the assignee and every administrator after a ticket changes. It carries only the id, number and status; pages refetch through the normal, permission-checked endpoints.
- **After commit**: both run through `NotificationDispatch.runAfterCommit` / `afterCommit`, so nobody is told about a change that was rolled back.
- **Why**: pages (ticket view, worklist, inbox badge, toasts from `LiveUpdates.tsx`) learn about changes without polling, and the sender never knows who is listening.

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
The system needs to dispatch notifications across disparate channels (in-app records, email, open browser tabs) and to ask an external language model for help, using uniform interfaces so the application code never depends on a vendor's API.

### A. Notification channels
- **Target Interface**: `com.servicedesk.domain.notification.NotificationChannel`
- **Adapters**:
  - `InAppNotificationChannelAdapter`: Converts domain `NotificationMessage` into database entity records.
  - `BrevoEmailChannelAdapter`: Translates a `NotificationMessage` into a call to Brevo's transactional API (`POST /v3/smtp/email`): looks up the recipient's email, HTML-escapes the title and body, adds an "Open in ServiceDesk" link (`APP_BASE_URL` + `/incidents/{id}`, or `/notifications`) and a plain-text part. It is a bean only when `BREVO_API_KEY` is set (`@ConditionalOnExpression`), runs `@Async` with 5 s connect / 10 s read timeouts, skips users without an email, and logs (never rethrows) API errors so the in-app notification still arrives.
  - `EmailGatewayAdapter`: The log-only email channel, active only when `BREVO_API_KEY` is blank, so demos need no mail account. Exactly one of the two email adapters exists at a time.
  - `LiveNotificationChannelAdapter`: Pushes the notification to the recipient's open tabs as a `notification` Server-Sent Event (see section 3).

### B. Help desk assistant (Gemini)
- **Target Interface (domain port)**: `com.servicedesk.domain.intelligence.IncidentAssistant` with `triage()`, `selfHelp()`, `suggestFix()`, `summarize()` and `source()`. Its records (`TicketDraft`, `PastFix`, `TicketContext`, `TriageSuggestion`, `AssistantAdvice`) are pure Java, so the domain knows nothing about HTTP or JSON.
- **Adapter**: `com.servicedesk.infrastructure.ai.GeminiAssistantAdapter` turns each call into a Gemini `models/{model}:generateContent` request with a system instruction, the ticket text wrapped in `<ticket>` tags and a JSON response schema, then validates the answer (enum values must parse, a service must be one we offered, at most four steps, lengths capped, control characters stripped). Anything unusable, a timeout (8 s) or a network error becomes `AssistantUnavailableException`.
- **Fallback implementation**: `com.servicedesk.domain.intelligence.RuleBasedIncidentAssistant` implements the same port with keyword rules and fixes from similar resolved tickets.
- **Client**: `AssistantApplicationService.ask()` uses Gemini when `isEnabled()` (a `GEMINI_API_KEY` is set) and falls back to the rules on any `RuntimeException`; every answer carries `source` = `gemini` or `rules`.
- **Why**: the vendor can be swapped (another model, or none) without touching controllers, and the app behaves the same with or without an API key, which also keeps the tests offline.

### C. Rate limiter
- `com.servicedesk.infrastructure.security.ratelimit.RateLimiter` is a port with one method, `tryConsume(key, policy)`. `InMemoryRateLimiter` implements it with token buckets; a Redis-backed implementation could replace it for several instances without changing `RateLimitFilter`.

---

## 7. Chain of Responsibility (Behavioral)

### Problem
A ticket that nobody picks up, or that blows its resolution target, has to be raised through the support hierarchy one level at a time, and each level has its own rule. A single method with an `if` per level would mix every rule together and make it hard to add or reorder a level.

### Implementation (`com.servicedesk.domain.escalation`)
- **Handler**: abstract `EscalationHandler` with `linkWith(next)` and `handle(incident, now)`. A handler answers with an `EscalationDecision(level, reason)` when its level is above the ticket's current `EscalationLevel` and its `applies()` rule holds; otherwise it passes the ticket on. Because a handler at or below the ticket's level always passes, each evaluation raises a ticket by at most one step.
- **Concrete handlers**, built in order by `EscalationChain.standard()`:
  1. `TeamLeadEscalationHandler` (L1, `TEAM_LEAD`): the response deadline passed and nobody acknowledged the ticket (`Incident.isResponseOverdue`).
  2. `AdministratorEscalationHandler` (L2, `ADMINISTRATOR`): still not acknowledged one more response target after the deadline (twice the target since it was reported).
  3. `ItManagerEscalationHandler` (L3, `IT_MANAGER`): an open P1 or P2 ticket whose SLA status is `BREACHED`.
- **Client**: `SlaScheduledEvaluator` runs the chain for every open ticket on each scheduled run and records the result with `Incident.escalateTo(level, now)` (the first escalation also stamps `escalatedAt`). It writes an `ESCALATED` audit entry (old and new level) and notifies the level's recipients: team leads (technicians flagged as team lead) for L1, administrators for L2, root (the IT manager) for L3. If nobody holds that role it falls back to administrators or root. The previous assignee is always told.
- **Administrator level reassigns**: at L2 a ticket still `REPORTED` or `ASSIGNED` is handed to another available technician, chosen by `CapacityAwareAssignmentStrategy(LeastLoadedAssignmentStrategy)`, with an `ASSIGNED` audit entry and a notification to the new assignee.
- **Why**: each rule is one small class, the order lives in one place, and a new level (for example a vendor escalation) is a new handler linked into the chain. The handlers are pure domain code and are unit-tested without Spring.

---

## 8. Decorator (Structural)

### Problem
Every assignment strategy (round robin, least loaded, skill match) must stop overloading people once technicians have a per-person open-ticket limit. Copying the same check into each strategy would duplicate it and make new strategies easy to get wrong.

### Implementation
- **Component**: `com.servicedesk.domain.assignment.AssignmentStrategy`.
- **Decorator**: `CapacityAwareAssignmentStrategy` wraps any strategy. It removes technicians for whom `User.hasCapacity()` is false (their open tickets, not counting `RESOLVED`, `CLOSED` and `CANCELLED`, already reach `maxActiveTickets`) and only then lets the wrapped strategy choose; if nobody has room it returns empty.
- **Where it is applied**: `IncidentApplicationService` wraps whichever strategy the administrator selects at runtime (`new CapacityAwareAssignmentStrategy(new SkillMatchAssignmentStrategy())` and so on), and `SlaScheduledEvaluator` wraps least-loaded for escalation reassignment.
- **Manual assignment**: an administrator choosing a technician who is already at their limit gets `TechnicianWorkloadExceededException`, mapped to **409 Conflict** by `GlobalExceptionHandler`. When nobody has room, a new ticket simply stays `REPORTED`, and an explicit auto-assign request fails with the same 409.
- **Why**: the strategies stay unchanged and unaware of capacity, and the rule is applied the same way everywhere (Open/Closed Principle).
