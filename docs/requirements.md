# Requirements & Business Rules Specification

**Project**: BITS ServiceDesk: Intelligent IT Service Management & Incident Resolution System

---

## 1. Functional Requirements (FR)

### 1.1 User Management & Authentication (FR-USER)
- **FR-USER-01**: The system must support four roles: `EMPLOYEE`, `TECHNICIAN`, `ADMINISTRATOR` and a single `ROOT` superuser.
- **FR-USER-02**: Users must be able to sign in with a username and password or with their Google account (OAuth 2.0 / OpenID Connect), and employees must be able to register themselves. Sessions use JWTs.
- **FR-USER-03**: Administrators must be able to list users, create accounts, change roles between employee and technician, deactivate and reactivate accounts, and edit technician skills. Only root can grant or remove administrator access.
- **FR-USER-04**: The root account is created from environment configuration on first start; there can only ever be one.

### 1.2 Incident Reporting & Management (FR-INC)
- **FR-INC-01**: Employees must be able to report an incident with a title, description, category (`NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`), impact, and urgency.
- **FR-INC-02**: Incidents must possess a unique identifier (UUID and human-readable ticket number, e.g., `INC-1001`).
- **FR-INC-03**: Incidents must support diagnostic comments added by the reporter, assigned technician, or administrator.
- **FR-INC-04**: Incidents must enforce strict lifecycle state transitions via the State Pattern.

### 1.3 Dynamic Priority Calculation (FR-PRIO)
- **FR-PRIO-01**: The system must calculate incident priority dynamically using a pluggable `PriorityCalculationStrategy`.
- **FR-PRIO-02**: Priority is derived from the combination of **Impact** (organizational scope of disruption) and **Urgency** (time criticality of resolution).
- **FR-PRIO-03**: Default calculation maps inputs to 4 priority levels: `P1_CRITICAL`, `P2_HIGH`, `P3_MEDIUM`, and `P4_LOW`.
- **FR-PRIO-04**: Administrators and lead technicians can override computed priority with a documented justification.

### 1.4 Automated Technician Assignment (FR-ASGN)
- **FR-ASGN-01**: The system must support at least two pluggable assignment strategies implementing `AssignmentStrategy`:
  - **RoundRobinAssignmentStrategy**: Distributes incidents sequentially among available technicians.
  - **LeastLoadedAssignmentStrategy**: Assigns incidents to the technician currently holding the fewest active (non-resolved) tickets.
  - **SkillMatchAssignmentStrategy**: Matches incident category with technician skills, falling back to least-loaded technician if ties occur.
- **FR-ASGN-02**: The active assignment strategy must be configurable at runtime by administrators without restarting the application.
- **FR-ASGN-03**: Technicians and administrators may manually reassign an incident.

### 1.5 Service Level Agreement (SLA) & Automated Escalation (FR-SLA)
- **FR-SLA-01**: Every incident must be bound to an SLA target based on its priority level, defining **Response Deadline** and **Resolution Deadline**.
- **FR-SLA-02**: The system must evaluate SLA timers against an injected `Clock` bean to determine SLA status (`WITHIN_SLA`, `WARNING_BREACH_IMMINENT`, `BREACHED`).
- **FR-SLA-03**: When an SLA resolution deadline is breached:
  - An `SlaBreachedEvent` is published.
  - The incident priority is automatically escalated by one tier (e.g., P3 -> P2), unless already P1.
  - An urgent notification is dispatched to the administrator and assigned technician.

### 1.6 Multi-Channel Notification System (FR-NOTIF)
- **FR-NOTIF-01**: The system must support at least two notification delivery channels implementing `NotificationChannel`:
  - **InAppNotificationChannel**: Persists notifications to the database for display in the user's web UI.
  - **EmailNotificationChannel**: Generates formatted email notifications (simulated via console/logger in dev/test, SMTP-ready in production).
- **FR-NOTIF-02**: Notifications must be triggered automatically via domain event observers on:
  - Incident creation (to reporter and assigned technician).
  - Status change (e.g., when technician requests more info or marks resolved).
  - SLA breach or escalation.

### 1.7 Audit Trail & Compliance (FR-AUDIT)
- **FR-AUDIT-01**: Every mutating action on an incident (creation, status update, priority change, reassignment, comment) must generate an immutable audit log entry.
- **FR-AUDIT-02**: Audit records must store: `timestamp`, `incidentId`, `actorId`, `actionType`, `oldValue`, `newValue`, and `remarks`.
- **FR-AUDIT-03**: Audit trails must be read-only and ordered chronologically.

### 1.8 Search & Filter Subsystem (FR-SEARCH)
- **FR-SEARCH-01**: Technicians and administrators must be able to filter incidents using at least **5 distinct criteria**:
  1. Status (single or multi-status, e.g., `IN_PROGRESS`, `PENDING_INFO`)
  2. Priority (`P1_CRITICAL`, `P2_HIGH`, `P3_MEDIUM`, `P4_LOW`)
  3. Category (`NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`)
  4. Assigned Technician ID
  5. SLA Status (`WITHIN_SLA`, `BREACHED`)
  6. Free-text search across Title and Description.

### 1.9 Intelligent Features (FR-INTEL)
- **FR-INTEL-01 (Duplicate detection)**: While a ticket is being written, the system compares it with open tickets the reporter may see (token-based Jaccard similarity, `SimilarityStrategy`) and warns when similarity is $\ge 0.3$, linking the likely duplicate.
- **FR-INTEL-02 (Suggested resolution)**: On a ticket, staff see related tickets: open look-alikes marked as possible duplicates, and resolved or closed ones with their recorded fix.
- **FR-INTEL-03 (Category suggestion)**: The report form suggests a category from the title and description using a keyword classifier (`CategorySuggestionStrategy`), showing which words led to the suggestion.
- **FR-INTEL-04 (Business-aware priority)**: Priority combines impact, urgency and the affected service's business criticality (section 4.1).
- **FR-INTEL-05 (Automatic escalation)**: A ticket that gets no response by its response target is escalated to every administrator; an overdue ticket is raised one priority level.
- **FR-INTEL-06 (SLA risk detection)**: Tickets entering the last quarter of their resolution window are flagged "at risk" and the assignee is warned.
- **FR-INTEL-07 (Incident correlation)**: Reports highlight any service with two or more open tickets reported in the last 24 hours as a possible common cause.

---

## 2. Non-Functional Requirements (NFR)

- **NFR-01 (Domain Isolation)**: All core business models and algorithms in `com.servicedesk.domain` must be pure Java 21 with zero dependencies on Spring Boot, JPA, or web annotations.
- **NFR-02 (Performance)**: Common API queries (fetching incident list with filters) must respond within $< 200\text{ ms}$ for datasets up to 10,000 records.
- **NFR-03 (Determinism & Testability)**: All date and time calculations must inject `java.time.Clock` to guarantee 100% reproducible unit and integration tests.
- **NFR-04 (Extensibility)**: New notification channels, priority algorithms, and assignment strategies must be addable by implementing the corresponding interface without modifying existing core classes (Open/Closed Principle).
- **NFR-05 (Security)**: Endpoints must enforce role-based access control (RBAC). Employees can only read their own submitted incidents, while Technicians and Admins have wider access.

---

## 3. Roles & Permissions Matrix

| Capability / Action | Employee | Technician | Administrator | Root |
| :--- | :---: | :---: | :---: | :---: |
| Report New Incident | **Yes** | **Yes** | **Yes** | **Yes** |
| Tickets visible in the list | Own only | Assigned to them | All | All |
| Assign / Reassign Incident | No | No | **Yes** | **Yes** |
| Start Work, Request Info, Resolve | No | **Yes** (assigned only) | **Yes** | **Yes** |
| Provide Info / Resume | **Yes** (own ticket) | Own ticket | **Yes** | **Yes** |
| Close Incident and rate the support | **Yes** (own ticket) | Own ticket | **Yes** | **Yes** |
| Cancel Incident | **Yes** (own, if `REPORTED`) | Own, if `REPORTED` | **Yes** | **Yes** |
| Override Priority | No | No | **Yes** | **Yes** |
| Reports, Audit Log, SLA targets, IT services, assignment rule | No | No | **Yes** | **Yes** |
| Manage employees and technicians | No | No | **Yes** | **Yes** |
| Grant administrator access | No | No | No | **Yes** |

Tickets outside a user's scope respond as "not found", so their existence is not revealed.

--- | :---: | :---: | :---: |
| Report New Incident | **Yes** | **Yes** | **Yes** |
| View Own Incidents | **Yes** | **Yes** | **Yes** |
| View All Active Incidents | No | **Yes** | **Yes** |
| Assign / Reassign Incident | No | **Yes** (within tier) | **Yes** (unrestricted) |
| Start Work (`IN_PROGRESS`) | No | **Yes** (if assigned) | **Yes** |
| Request More Info (`PENDING_INFO`) | No | **Yes** | **Yes** |
| Provide Info / Resume | **Yes** (own ticket) | **Yes** | **Yes** |
| Mark Resolved | No | **Yes** | **Yes** |
| Close Incident (Verification) | **Yes** (own ticket) | No | **Yes** |
| Cancel Incident | **Yes** (if `REPORTED`) | No | **Yes** |
| Configure Assignment Strategy | No | No | **Yes** |
| View Full Audit Trail | No | **Yes** | **Yes** |

---

## 4. Business Rules & Policies

### 4.1 Priority Determination (weighted score)
Priority combines **Impact** (how many people are affected), **Urgency** (how soon they need a fix)
and the **business criticality** of the affected IT service:

$$\text{score} = 2 \times \text{impact} + \text{urgency} + \text{criticality adjustment}$$

- Impact and urgency: `HIGH` = 3, `MEDIUM` = 2, `LOW` = 1. Impact counts double because it measures breadth.
- Criticality adjustment: `CRITICAL` +2, `HIGH` +1, `MEDIUM` 0, `LOW` −1 (no service selected = `MEDIUM`).
- Score ≥ 8 → **P1**, 7 → **P2**, 5–6 → **P3**, ≤ 4 → **P4**.

For a medium-criticality service this reproduces the classic matrix exactly:

| Impact \ Urgency | `HIGH` | `MEDIUM` | `LOW` |
| :--- | :---: | :---: | :---: |
| **`HIGH`** | **P1** | **P1** | **P2** |
| **`MEDIUM`** | **P2** | **P3** | **P3** |
| **`LOW`** | **P3** | **P4** | **P4** |

Example: "Payment application unavailable" with medium impact and urgency on the critical
Payment Application scores 2·2 + 2 + 2 = 8 → **P1**.

### 4.2 Service Level Agreement (SLA) Targets
Defaults below; administrators can change them per priority in Settings (new tickets use the new targets).

| Priority | First response | Resolution |
| :--- | :---: | :---: |
| **P1 - CRITICAL** | 5 minutes | 30 minutes |
| **P2 - HIGH** | 15 minutes | 2 hours |
| **P3 - MEDIUM** | 1 hour | 8 hours |
| **P4 - LOW** | 4 hours | 24 hours |

- **Warning**: a ticket becomes *at risk* in the last quarter of its resolution window; the assignee is notified.
- **Breach**: once overdue, the ticket is flagged, raised one priority level (once) and the assignee is notified.
- **Escalation**: if nobody starts work before the response target, the ticket is escalated and every administrator is notified.
- **Pause**: the clock stops while the ticket waits on the reporter (`PENDING_INFO`); the deadline moves out by the paused time.

### 4.3 Incident Lifecycle State Transitions

```
[REPORTED] ────assign────> [ASSIGNED] ────start_work────> [IN_PROGRESS]
    │                          │                               │    ▲
    │                          │                               │    │
    │ cancel                   │ cancel           request_info │    │ provide_info
    ▼                          ▼                               ▼    │
[CANCELLED]                [CANCELLED]                 [PENDING_INFO]
                                                               │
                                                               │ resolve
                                                               ▼
[CLOSED] <──────verify_and_close─────── [RESOLVED]
```

- **Needs-Info Loop**:
  - A technician transitions an incident from `IN_PROGRESS` to `PENDING_INFO` when clarification or user action is needed.
  - While in `PENDING_INFO`, the SLA resolution timer is **paused** to prevent penalizing support teams for customer response latency.
  - When the employee submits the requested info, the state returns to `IN_PROGRESS` and SLA timing resumes.
- **Resolution & Closure**:
  - Technicians mark tickets as `RESOLVED` with mandatory resolution notes and root-cause classification.
  - The employee confirms resolution to move it to `CLOSED`. If unconfirmed after 48 hours, the system auto-closes it.
- **Strict Invariants & Forbidden Transitions**:
  - `CLOSED` and `CANCELLED` are terminal states; no further transitions are allowed.
  - An incident cannot transition directly from `REPORTED` to `RESOLVED` or `CLOSED` without passing through `IN_PROGRESS`.
  - Reassignment is prohibited once an incident is `RESOLVED` or `CLOSED`.
