# Requirements & Business Rules Specification

**Project**: BITS ServiceDesk: Intelligent IT Service Management & Incident Resolution System

---

## 1. Functional Requirements (FR)

### 1.1 User Management & Authentication (FR-USER)
- **FR-USER-01**: The system must support four roles: `EMPLOYEE`, `TECHNICIAN`, `ADMINISTRATOR` and a single `ROOT` superuser.
- **FR-USER-02**: Users must be able to sign in with a username and password or with their Google account (OAuth 2.0 / OpenID Connect), and employees must be able to register themselves. Sessions use JWTs.
- **FR-USER-03**: Administrators must be able to list users, create accounts, change roles between employee, technician and administrator, deactivate and reactivate accounts, and edit technician skills. Administrators can grant administrator access; only root can change or remove it afterwards.
- **FR-USER-04**: The root account is created from environment configuration on first start; there can only ever be one.
- **FR-USER-05**: The sign-up page must offer "Sign up with Google" when Google sign-in is configured; a first Google sign-in creates an employee account.
- **FR-USER-06**: When someone signs up (password or first Google sign-in), every active administrator and root must get a `NEW_USER` notification so they can review the new account's role.
- **FR-USER-07**: Five wrong passwords for one username within 15 minutes must lock that username for 15 minutes, from any address, with the same "Invalid username or password" answer (section 2, NFR-06).
- **FR-USER-08**: Administrators must be able to set or clear a user's notification email address; two accounts can't share one.

### 1.2 Incident Reporting & Management (FR-INC)
- **FR-INC-01**: Employees must be able to report an incident with a title, description, category (`NETWORK`, `HARDWARE`, `SOFTWARE`, `ACCESS`), impact, and urgency.
- **FR-INC-02**: Incidents must possess a unique identifier (UUID and human-readable ticket number, e.g., `INC-1001`).
- **FR-INC-03**: Incidents must support diagnostic comments added by the reporter, assigned technician, or administrator.
- **FR-INC-04**: Incidents must enforce strict lifecycle state transitions via the State Pattern.
- **FR-INC-05 (Updates thread)**: Each ticket must have one chronological Updates thread. Besides typed comments (`COMMENT`), the system must add an entry for each question (`QUESTION`), reply (`ANSWER`), pick-up (`PICKUP`), resolution (`RESOLUTION`) and other lifecycle change such as close, cancel or link (`STATUS`). Internal notes stay hidden from employees.
- **FR-INC-06 (All issues)**: Every signed-in user must be able to browse and search all issues read-only (title, description, status, priority, category, service, who is handling it and public updates), without the reporter's identity, internal notes, emails or audit history.
- **FR-INC-07 (Acknowledge)**: The assigned technician must be able to acknowledge a ticket (`ASSIGNED` → `ACKNOWLEDGED`) before starting work; this meets the response target, adds a `PICKUP` update and tells the reporter. Starting work straight from `ASSIGNED` must also be allowed and counts as acknowledging.
- **FR-INC-08 (Major incidents)**: Administrators must be able to link an open ticket under another open ticket (the major incident) and unlink it, one level deep. "This affects me too" on All issues must create a ticket already linked under that issue. Resolving the major incident must resolve every open linked ticket with the same fix and notify their reporters.
- **FR-INC-09 (Service status)**: Everyone must be able to see each active service as Operational, Degraded or Outage, computed from its open tickets (section 4.5), with counts only.
- **FR-INC-11 (Affects me too)**: Instead of filing a duplicate, a signed-in user can click "This affects me too" on an open issue in All issues. They are counted once on the main ticket, notified when it is fixed, and see it in My tickets marked as followed (the reporter's name stays hidden). The assignee is told when more people join.
- **FR-INC-10 (Board)**: Staff must have a board (`/board`) with columns Assigned, Acknowledged, In progress, Waiting on reporter and Resolved (last 7 days). Cards move by drag and drop and, for keyboard users, by a "Move to" control; a move runs the matching lifecycle action.

### 1.3 Dynamic Priority Calculation (FR-PRIO)
- **FR-PRIO-01**: The system must calculate incident priority dynamically using a pluggable `PriorityCalculationStrategy`.
- **FR-PRIO-02**: Priority is derived from the combination of **Impact** (organizational scope of disruption) and **Urgency** (time criticality of resolution).
- **FR-PRIO-03**: Default calculation maps inputs to 4 priority levels: `P1_CRITICAL`, `P2_HIGH`, `P3_MEDIUM`, and `P4_LOW`.
- **FR-PRIO-05 (Affected users)**: The number of affected users (the reporter, linked tickets and people who clicked "This affects me too") feeds the weighted score: 3 or more add 1, 10 or more add 2. When the count grows, the priority is recalculated and only ever raised; the change is audited and the reporter and assignee are notified.
- **FR-PRIO-04**: Administrators and lead technicians can override computed priority with a documented justification.

### 1.4 Automated Technician Assignment (FR-ASGN)
- **FR-ASGN-01**: The system must support at least two pluggable assignment strategies implementing `AssignmentStrategy`:
  - **RoundRobinAssignmentStrategy**: Distributes incidents sequentially among available technicians.
  - **LeastLoadedAssignmentStrategy**: Assigns incidents to the technician currently holding the fewest active (non-resolved) tickets.
  - **SkillMatchAssignmentStrategy**: Matches incident category with technician skills, falling back to least-loaded technician if ties occur.
- **FR-ASGN-02**: The active assignment strategy must be configurable at runtime by administrators without restarting the application.
- **FR-ASGN-03**: Technicians and administrators may manually reassign an incident.
- **FR-ASGN-05 (Team lead)**: A technician marked as team lead can see every ticket, load the technician list, and assign or reassign any ticket, with the same capacity checks as administrators. Other technicians cannot.
- **FR-ASGN-04 (Capacity)**: Each technician must have an open-ticket limit (default 5, 1–50, set by administrators). Automatic assignment must skip technicians at their limit, whatever strategy is active (`CapacityAwareAssignmentStrategy`); a new ticket stays unassigned when nobody has room. Manually assigning a technician who is at their limit must be refused with 409 Conflict. Resolved, closed and cancelled tickets don't count towards the limit.

### 1.5 Service Level Agreement (SLA) & Automated Escalation (FR-SLA)
- **FR-SLA-01**: Every incident must be bound to an SLA target based on its priority level, defining **Response Deadline** and **Resolution Deadline**.
- **FR-SLA-02**: The system must evaluate SLA timers against an injected `Clock` bean to determine SLA status (`WITHIN_SLA`, `WARNING_BREACH_IMMINENT`, `BREACHED`).
- **FR-SLA-03**: When an SLA resolution deadline is breached:
  - An `SlaBreachedEvent` is published.
  - The incident priority is automatically escalated by one tier (e.g., P3 -> P2), unless already P1.
  - An urgent notification is dispatched to the administrator and assigned technician.
- **FR-SLA-04 (Escalation chain)**: Tickets that miss their targets must be escalated one level per scheduler run (section 4.4): to team leads when not acknowledged by the response deadline, to administrators (with automatic reassignment to a technician who has room) when still not acknowledged one response target later, and to the IT manager (root) when a P1 or P2 ticket breaches its resolution target.

### 1.6 Multi-Channel Notification System (FR-NOTIF)
- **FR-NOTIF-01**: The system must support at least two notification delivery channels implementing `NotificationChannel`:
  - **InAppNotificationChannel**: Persists notifications to the database for display in the user's web UI.
  - **Email**: Sent through Brevo's transactional API (`BrevoEmailChannelAdapter`) when `BREVO_API_KEY` is set, with a link back to the ticket; otherwise logged only (`EmailGatewayAdapter`). Users without an email address get in-app notifications only.
  - **Live**: Pushed to the recipient's open browser tabs over Server-Sent Events (`LiveNotificationChannelAdapter`) and shown as a toast.
- **FR-NOTIF-02**: Notifications must be triggered automatically via domain event observers on:
  - Incident creation (to reporter and assigned technician).
  - Status change (e.g., when technician requests more info or marks resolved).
  - SLA breach or escalation.
- **FR-NOTIF-03 (Live updates)**: Open pages must update without a reload: a ticket page, the worklists and the inbox badge refresh when a relevant ticket changes (`incident-updated` event), and new notifications appear as toasts (`notification` event). The stream reconnects on its own after a drop.
- **FR-NOTIF-04**: Email delivery must never delay or undo the user's action: it runs after the transaction commits, off the request thread, and failures are logged only.

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
- **FR-INTEL-05 (Automatic escalation)**: A ticket that gets no response by its response target climbs the escalation chain (FR-SLA-04); an overdue ticket is raised one priority level.
- **FR-INTEL-06 (SLA risk detection)**: Tickets entering the last quarter of their resolution window are flagged "at risk" and the assignee is warned.
- **FR-INTEL-07 (Incident correlation)**: Reports highlight any service with two or more open tickets reported in the last 24 hours as a possible common cause.
- **FR-INTEL-08 (Assistant triage)**: While a ticket is written, the assistant suggests category, impact, urgency and affected service with a one-line reason and the resulting priority; the reporter can keep or change it.
- **FR-INTEL-09 (Self-help)**: Before filing, the assistant offers up to four steps to try, drawn from how similar tickets were fixed (never their number, title or reporter), and asks whether they helped. The answer is stored to measure deflection.
- **FR-INTEL-10 (Staff assistant)**: On a ticket, technicians and administrators can ask for a suggested fix and for a summary of the ticket and its history.
- **FR-INTEL-11 (Fallback)**: The assistant uses Gemini when an API key is configured and falls back to built-in rules when it is missing, slow (8 s) or returns something unusable. Every answer says which one produced it.

- **FR-INTEL-12 (Automatic quick fix)**: When a reporter leaves the description box, the assistant offers a quick fix without being asked; reporters also see one on their own open tickets. Technicians see an automatic summary and suggested fix when they open a ticket. Gemini answers are stored in `ai_advice_cache`, so the same question is not sent twice.
- **FR-INTEL-13 (IT check)**: If the text is clearly not about technology the report form says so (a warning; the ticket can still be submitted). The check is deliberately lenient: anything involving a system, portal, app, account, device or network counts as IT.
- **FR-INTEL-14 (Help chat)**: A chatbot, available on every page, answers IT questions and tells the user where their own tickets stand. It uses Gemini and falls back to the on-device knowledge base (about 28 common problems) when Gemini is unavailable.
- **FR-USER-09 (Profile picture)**: Google sign-in uses the Google profile picture; password accounts show the first letter of the name.
- **FR-CAT-01 (Incident categories)**: Administrators can switch each category on or off in Settings. Off categories are hidden from the report form and refused by the API; tickets already filed keep theirs, and at least one category must stay on. A new category needs one enum value, one migration row and keywords for the classifier, and no change to unrelated code.

### 1.10 Reporting (FR-REP)
- **FR-REP-01 (Trends)**: Administrators can view trends over 7, 30 or 90 days (`GET /api/v1/reports/trends?days=`): tickets created vs resolved per day, SLA compliance per week, mean time to resolve by category, first-response time in minutes, escalations by level, and self-help deflection.

---

## 2. Non-Functional Requirements (NFR)

- **NFR-01 (Domain Isolation)**: All core business models and algorithms in `com.servicedesk.domain` must be pure Java 21 with zero dependencies on Spring Boot, JPA, or web annotations.
- **NFR-02 (Performance)**: Common API queries (fetching incident list with filters) must respond within $< 200\text{ ms}$ for datasets up to 10,000 records.
- **NFR-03 (Determinism & Testability)**: All date and time calculations must inject `java.time.Clock` to guarantee 100% reproducible unit and integration tests.
- **NFR-04 (Extensibility)**: New notification channels, priority algorithms, and assignment strategies must be addable by implementing the corresponding interface without modifying existing core classes (Open/Closed Principle).
- **NFR-05 (Security)**: Endpoints must enforce role-based access control (RBAC). Employees can only read their own submitted incidents, while Technicians and Admins have wider access.
- **NFR-06 (Rate limiting)**: The API must throttle requests with token buckets and answer 429 with `Retry-After` when a limit is hit: sign-in and sign-up 10 per minute per address, assistant calls 20 per minute per user, other writes 120 and everything else 600 per minute per user. Details in `security.md`.
- **NFR-07 (Graceful degradation)**: Missing Gemini or Brevo keys must not break any feature: the rule-based assistant and log-only email take over.

---

## 3. Roles & Permissions Matrix

| Capability / Action | Employee | Technician | Administrator | Root |
| :--- | :---: | :---: | :---: | :---: |
| Report New Incident | **Yes** | **Yes** | **Yes** | **Yes** |
| Tickets visible in the list | Own only | Assigned to them | All | All |
| All issues (read-only: description, status, public updates; reporter hidden) | **Yes** | **Yes** | **Yes** | **Yes** |
| Assign / Reassign Incident, link or unlink a major incident | No | No | **Yes** | **Yes** |
| Acknowledge, Start Work, Request Info, Resolve | No | **Yes** (assigned only) | **Yes** | **Yes** |
| Provide Info / Resume | **Yes** (own ticket) | Own ticket | **Yes** | **Yes** |
| Close Incident and rate the support | **Yes** (own ticket) | Own ticket | **Yes** | **Yes** |
| Cancel Incident | **Yes** (own, if `REPORTED`) | Own, if `REPORTED` | **Yes** | **Yes** |
| Override Priority | No | No | **Yes** | **Yes** |
| Reports, Audit Log, SLA targets, IT services, assignment rule | No | No | **Yes** | **Yes** |
| Manage employees and technicians | No | No | **Yes** | **Yes** |
| Grant administrator access | No | No | No | **Yes** |

Tickets outside a user's scope respond as "not found", so their existence is not revealed. The "All issues" list (`GET /api/v1/issues`) is a separate read-only view: it never includes the reporter's identity, internal notes, emails or the audit history, and acting on a ticket still goes through the scoped `/incidents` endpoints.

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
- **Escalation**: if nobody acknowledges or starts the ticket before the response target, it climbs the escalation chain one level per scheduler run (section 4.4).
- **Pause**: the clock stops while the ticket waits on the reporter (`PENDING_INFO`); the deadline moves out by the paused time.

### 4.3 Incident Lifecycle State Transitions

```
[REPORTED] ──assign──> [ASSIGNED] ──acknowledge──> [ACKNOWLEDGED]
    │                    │    │                          │
    │ cancel      cancel │    └───────start_work───┐     │ start_work
    ▼                    ▼                         ▼     ▼
[CANCELLED]         [CANCELLED]                  [IN_PROGRESS] <──provide_info── [PENDING_INFO]
                                                   │      └────request_info────────────▲
                                                   │ resolve
                                                   ▼
                        [CLOSED] <──close── [RESOLVED] ──reopen──> [IN_PROGRESS]
```

`ACKNOWLEDGED` can also be cancelled (administrators only). Reassigning from `ASSIGNED`, `ACKNOWLEDGED` or `IN_PROGRESS` returns the ticket to `ASSIGNED`. A ticket linked under a major incident goes straight to `RESOLVED` from any open state when the major incident is resolved.

- **Acknowledge**: the assignee accepts the ticket, which records the first response (meets the response target) and stops it escalating for response. Starting work from `ASSIGNED` counts as acknowledging.
- **Needs-Info Loop**:
  - A technician transitions an incident from `IN_PROGRESS` to `PENDING_INFO` when clarification or user action is needed.
  - While in `PENDING_INFO`, the SLA resolution timer is **paused** to prevent penalizing support teams for customer response latency.
  - When the employee submits the requested info, the state returns to `IN_PROGRESS` and SLA timing resumes.
- **Resolution & Closure**:
  - Technicians mark tickets as `RESOLVED` with mandatory resolution notes and root-cause classification.
  - The employee confirms resolution to move it to `CLOSED`. If unconfirmed after 48 hours, the system auto-closes it.
- **Strict Invariants & Forbidden Transitions**:
  - The reporter or an administrator can reopen a `RESOLVED` ticket, or a `CLOSED` ticket up to and including 14 days after closure, with a required reason. It returns to `IN_PROGRESS`; resolution, closure and feedback fields are cleared.
  - `CANCELLED` rejects all transitions. `CLOSED` rejects transitions other than reopening within that window.
  - An incident cannot transition directly from `REPORTED` to `RESOLVED` or `CLOSED` without passing through `IN_PROGRESS`; the only exception is resolving together with its major incident.
  - Reassignment is prohibited once an incident is `RESOLVED` or `CLOSED`.

### 4.4 Escalation Chain
Evaluated by the SLA scheduler for every open ticket; one level per run, never skipping a level.

| Level | Raised when | Who is told | Extra action |
| :--- | :--- | :--- | :--- |
| L1 `TEAM_LEAD` | Response deadline passed, not acknowledged | Team leads (else administrators, else root) and the assignee | — |
| L2 `ADMINISTRATOR` | Still not acknowledged one more response target later | Administrators (else root) and the previous assignee | If still `REPORTED` or `ASSIGNED`, reassigned to the least-loaded technician with room |
| L3 `IT_MANAGER` | Open P1 or P2 ticket breached its resolution target | Root (else administrators) and the assignee | — |

Each step writes an `ESCALATED` audit entry with the old and new level.

### 4.5 Service Status
Computed from a service's open tickets (not resolved, closed or cancelled):

| Status | Rule |
| :--- | :--- |
| **Outage** | An open major incident (a ticket with open linked tickets), or two or more open P1 tickets |
| **Degraded** | Any open P1 or P2 ticket, or two or more open tickets |
| **Operational** | Otherwise |

## 4. Incident lifecycle compared with the course brief

The brief suggests NEW → CLASSIFIED → ASSIGNED → ACKNOWLEDGED → IN_PROGRESS → RESOLVED → VERIFIED → CLOSED and allows the team to adapt it.

| Brief state | In ServiceDesk | Why |
| :--- | :--- | :--- |
| NEW, CLASSIFIED | `REPORTED` | Classification (category, impact, urgency, priority, SLA) happens automatically when the ticket is created, so there is no separate waiting state. |
| ASSIGNED | `ASSIGNED` | Same. |
| ACKNOWLEDGED | `ACKNOWLEDGED` | Same; it meets the response target. |
| IN_PROGRESS | `IN_PROGRESS`, plus `PENDING_INFO` | The SLA clock pauses while waiting for the reporter. |
| RESOLVED | `RESOLVED` | The reporter can reopen it ("It's still not fixed"). |
| VERIFIED, CLOSED | `CLOSED` | The reporter (or an administrator) verifies the fix by closing the ticket and rating the support. A closed ticket can still be reopened within 14 days. |
| (cancellation) | `CANCELLED` | A ticket reported by mistake. |

Illegal moves (for example REPORTED → CLOSED) throw `InvalidStateTransitionException` and return HTTP 409.
