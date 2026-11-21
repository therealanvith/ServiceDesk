# REST API Specification

This document outlines the REST API endpoints, DTO contracts, and Role-Based Access Control (RBAC) rules for ServiceDesk.

Base URL prefix: `/api/v1`

---

## 1. Authentication & User Endpoints (`/api/v1/auth`, `/api/v1/users`)

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/auth/login` | Anonymous | `LoginRequest` | `AuthResponse` | `200 OK`, `401 Unauthorized` | Authenticate with username/password, returns JWT token and user profile. |
| `GET` | `/users/me` | Any Authenticated | *None* | `UserProfileResponse` | `200 OK`, `401 Unauthorized` | Get current logged-in user details, role, and skills. |
| `GET` | `/users/technicians` | `ADMINISTRATOR`, `ROOT` | *None* | `List<TechnicianResponse>` | `200 OK` | List active technicians with skills and current open ticket load. |
| `PUT` | `/users/{id}/skills` | `ADMINISTRATOR`, `ROOT` | `UpdateSkillsRequest` | `TechnicianResponse` | `200 OK`, `404 Not Found` | Update category skills assigned to a technician. |

---

## 2. Incident Endpoints (`/api/v1/incidents`)

Tickets outside the caller's scope return `404 Not Found` (their existence is not revealed); visible tickets the caller may not change return `403 Forbidden`.

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/incidents` | Any authenticated | `CreateIncidentRequest` | `IncidentDetailResponse` | `201 Created`, `400 Bad Request` | Report a new incident. Computes priority, similarity, and runs auto-assignment. |
| `GET` | `/incidents` | Any Authenticated | `IncidentFilterParams` (query params) | `PagedResponse<IncidentSummaryResponse>` | `200 OK` | Filtered incident list. Employees see tickets they reported; technicians see tickets assigned to them or reported by them; administrators and root see all. |
| `GET` | `/incidents/{id}` | Reporter, assignee, `ADMINISTRATOR`, `ROOT` | *None* | `IncidentDetailResponse` | `200 OK`, `404 Not Found` | Retrieve complete details of a specific incident. |
| `POST` | `/incidents/{id}/assign` | `ADMINISTRATOR`, `ROOT` | `AssignIncidentRequest` (optional; no `technicianId` runs the active strategy) | `IncidentDetailResponse` | `200 OK`, `400 Bad Request`, `409 Conflict` | Assign or reassign incident to a technician. `409` when the chosen technician is at their open-ticket limit, or nobody has room (`TechnicianWorkloadExceededException`). |
| `POST` | `/incidents/{id}/acknowledge` | Assigned `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` | *None* | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | `ASSIGNED` to `ACKNOWLEDGED`. Sets `respondedAt` (meets the response target), posts a `PICKUP` update and notifies the reporter. |
| `POST` | `/incidents/{id}/start` | Assigned `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` | *None* | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | `ASSIGNED` or `ACKNOWLEDGED` to `IN_PROGRESS`. Starting straight from `ASSIGNED` also counts as the response and posts the `PICKUP` update. |
| `POST` | `/incidents/{id}/request-info` | Assigned `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` | `RequestInfoRequest` | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | Transition to `PENDING_INFO` and pause SLA resolution timer. |
| `POST` | `/incidents/{id}/provide-info` | Reporter, `ADMINISTRATOR`, `ROOT` | `ProvideInfoRequest` | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | Return to `IN_PROGRESS` and resume SLA resolution timer. |
| `POST` | `/incidents/{id}/resolve` | Assigned `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` | `ResolveIncidentRequest` | `IncidentDetailResponse` | `200 OK`, `400 Bad Request`, `409 Conflict` | Mark ticket as `RESOLVED` with mandatory resolution summary. Every open ticket linked under it is resolved too ("Resolved with INC-…: summary") and its reporter notified. |
| `POST` | `/incidents/{id}/reopen` | Reporter, `ADMINISTRATOR`, `ROOT` | `{ "reason": "Fix did not hold" }` | `IncidentDetailResponse` | `200 OK`, `400 Bad Request`, `404 Not Found`, `409 Conflict` | Reopen a resolved ticket or a ticket closed within 14 days; clears resolution and feedback. |
| `POST` | `/incidents/{id}/close` | Reporter, `ADMINISTRATOR`, `ROOT` | `CloseIncidentRequest` | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | Confirm resolution and move incident to `CLOSED` (reopen allowed within 14 days). |
| `POST` | `/incidents/{id}/cancel` | Reporter (only while `REPORTED`), `ADMINISTRATOR`, `ROOT` | `CancelIncidentRequest` | `IncidentDetailResponse` | `200 OK`, `409 Conflict` | Cancel incident with reason. |
| `POST` | `/incidents/{id}/link` | `ADMINISTRATOR`, `ROOT` | `LinkIncidentRequest` | `IncidentDetailResponse` | `200 OK`, `400 Bad Request`, `403 Forbidden`, `409 Conflict` | Link this ticket under a major incident. One level deep: `400` when linking a ticket to itself, under a ticket that is itself linked, or when this ticket has its own linked tickets; `409` unless both are open. Writes a `LINKED` audit entry and tells the reporter. |
| `POST` | `/incidents/{id}/unlink` | `ADMINISTRATOR`, `ROOT` | *None* | `IncidentDetailResponse` | `200 OK`, `400 Bad Request` | Remove the link (`UNLINKED` audit entry). `400` if the ticket is not linked. |
| `PUT` | `/incidents/{id}/priority` | `ADMINISTRATOR`, `ROOT` | `OverridePriorityRequest` | `IncidentDetailResponse` | `200 OK`, `400 Bad Request` | Manually override priority level with mandatory audit remark. |

---

## 3. Comments, Audit & Intelligence Endpoints

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/incidents/{id}/comments` | Any Authorized | `AddCommentRequest` | `CommentResponse` | `201 Created` | Add a `COMMENT` to the Updates thread (or an internal note; employees can't). |
| `GET` | `/incidents/{id}/comments` | Any Authorized | *None* | `List<CommentResponse>` | `200 OK` | The Updates thread, oldest first. Each entry has a `kind` (`COMMENT`, `QUESTION`, `ANSWER`, `PICKUP`, `RESOLUTION`, `STATUS`) and `authorRole`; lifecycle actions write their own entries. Employees do not see `isInternal=true` entries. |
| `GET` | `/incidents/{id}/audit-logs` | `TECHNICIAN`, `ADMINISTRATOR` | *None* | `List<AuditLogResponse>` | `200 OK` | Retrieve chronological, immutable audit history for the incident. |
| `GET` | `/incidents/{id}/similar` | `TECHNICIAN`, `ADMINISTRATOR` | *None* | `List<SimilarIncidentResponse>` | `200 OK` | Intelligent feature: returns historically resolved tickets with $\ge 0.6$ similarity. |

### 3.1 Assistant (`/api/v1/incidents`)

Gemini answers when `GEMINI_API_KEY` is set; otherwise, or when it fails, the rule-based assistant does. Every response has `source`: `"gemini"` or `"rules"`. Triage, self-help and the `/ai/` endpoints share the `ai` rate limit (20 per user per minute).

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/incidents/triage` | Any authenticated | `TriageRequest` | `TriageResponse` | `200 OK`, `400 Bad Request`, `429` | Suggested category, impact, urgency and service for a ticket being written, the priority that would give, and a one-line reason. |
| `POST` | `/incidents/self-help` | Any authenticated | `TriageRequest` | `AdviceResponse` | `200 OK`, `400 Bad Request`, `429` | Up to four steps to try before filing. Built from the fix text of similar resolved tickets only, never their number, title or reporter. |
| `POST` | `/incidents/self-help/feedback` | Any authenticated | `SelfHelpFeedbackRequest` | *None* | `204 No Content`, `400 Bad Request` | Records whether the steps solved the problem (`self_help_feedback`; feeds the deflection trend). |
| `POST` | `/incidents/{id}/ai/suggest-fix` | `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` (ticket must be visible to them) | *None* | `AdviceResponse` | `200 OK`, `403 Forbidden`, `404 Not Found`, `429` | Draft fix from the ticket, its thread and similar resolved tickets. |
| `POST` | `/incidents/{id}/ai/summary` | `TECHNICIAN`, `ADMINISTRATOR`, `ROOT` (ticket must be visible to them) | *None* | `SummaryResponse` | `200 OK`, `403 Forbidden`, `404 Not Found`, `429` | Short summary of the ticket and its history. |

`POST /incidents` also accepts two optional fields: `aiSuggestionAccepted` (the reporter kept the assistant's classification; noted in the audit) and `relatedIncidentId` (from "This affects me too": the new ticket is linked under that issue if it is still open and is itself a main ticket).

### 3.2 All issues, read-only (`/api/v1/issues`)

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/issues` | Any authenticated | Query: `status`, `category`, `serviceId`, `searchText`, `createdFrom`, `createdTo` (ISO dates), `page`, `size` (max 100) | `PagedResponse<PublicIssueResponse>` | `200 OK` | Every issue, so people can check whether a problem is already known. `updates` is empty in the list; `updateCount` counts public entries. |
| `GET` | `/issues/{id}` | Any authenticated | *None* | `PublicIssueResponse` | `200 OK`, `404 Not Found` | One issue with its public updates. The reporter appears only as `"Reporter"`; no internal notes, emails or audit history. Changing the ticket still goes through the scoped `/incidents` endpoints. |

### 3.3 Live updates (`/api/v1/events`)

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/events` | Any authenticated (`Authorization: Bearer` header) | *None* | `text/event-stream` | `200 OK`, `401 Unauthorized` | Server-Sent Events for the signed-in user: `ready` on connect, `notification` (`type`, `title`, `message`, `incidentId`, `createdAt`) for each new inbox item, and `incident-updated` (`incidentId`, `incidentNumber`, `status`) when a ticket they reported or are assigned changes (administrators get it for every ticket). Heartbeat comments every 25 s; a stream ends after 30 minutes and the client reconnects. Not rate limited. |

### 3.4 Catalog, user and report additions

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/catalog/service-status` | Any authenticated | *None* | `List<ServiceStatusResponse>` | `200 OK` | Health of each active service from its open (not yet resolved) tickets: `OUTAGE` if one of them is a major incident with open linked tickets, or two or more are P1; else `DEGRADED` if any is P1/P2 or there are two or more; else `OPERATIONAL`. Counts only, no ticket details. |
| `PUT` | `/users/{id}/capacity` | `ADMINISTRATOR`, `ROOT` | `UpdateCapacityRequest` | `TechnicianResponse` | `200 OK`, `400 Bad Request`, `403 Forbidden` | Set a technician's open-ticket limit (1 to 50, default 5) and whether they are a team lead (receives first-level escalations). `400` for non-technicians. |
| `PUT` | `/users/{id}/email` | `ADMINISTRATOR`, `ROOT` | `UpdateEmailRequest` | `UserAccountResponse` | `200 OK`, `400 Bad Request`, `403 Forbidden` | Set or clear (blank) the address notification emails go to. `400` if another account already uses it (case-insensitive). |
| `GET` | `/reports/trends?days=7\|30\|90` | `ADMINISTRATOR`, `ROOT` | *None* | Trends report | `200 OK`, `400 Bad Request` | Over the chosen window: daily tickets created vs resolved, weekly SLA compliance, mean time to resolve by category, first-response minutes, escalations by level and self-help deflection. |

`TechnicianResponse` also carries `maxActiveTickets` and `teamLead`; its `activeTicketsCount` no longer counts `RESOLVED` tickets.

---

## 4. System Administration & Configuration Endpoints (`/api/v1/admin`)

| Method | Endpoint | Allowed Roles | Request DTO | Response DTO | HTTP Status | Description |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/admin/config/assignment` | `ADMINISTRATOR` | *None* | `AssignmentConfigResponse` | `200 OK` | Returns current active strategy (`ROUND_ROBIN`, `LEAST_LOADED`, `SKILL_MATCH`). |
| `PUT` | `/admin/config/assignment` | `ADMINISTRATOR` | `UpdateAssignmentConfigRequest` | `AssignmentConfigResponse` | `200 OK`, `400 Bad Request` | Dynamically updates the active assignment strategy at runtime. |
| `POST` | `/admin/sla/evaluate` | `ADMINISTRATOR` | *None* | `SlaEvaluationSummaryResponse` | `200 OK` | Manually triggers SLA check (also executed periodically by background scheduler). |

---

## 5. DTO Data Contract Shapes

### `CreateIncidentRequest`
```json
{
  "title": "Lab 304 Workstations cannot reach local gateway",
  "description": "All 20 desktops in Computer Lab 304 show IP autoconfiguration 169.254.x.x and cannot reach the default gateway after power maintenance.",
  "category": "NETWORK",
  "impact": "HIGH",
  "urgency": "HIGH"
}
```

### `IncidentDetailResponse`
```json
{
  "id": "e7b0a880-92a0-4b89-a5e4-cb911a3b3701",
  "incidentNumber": "INC-1001",
  "title": "Lab 304 Workstations cannot reach local gateway",
  "description": "All 20 desktops in Computer Lab 304 show IP autoconfiguration 169.254.x.x...",
  "category": "NETWORK",
  "status": "ASSIGNED",
  "priority": "P1_CRITICAL",
  "impact": "HIGH",
  "urgency": "HIGH",
  "reporter": {
    "id": "11111111-1111-1111-1111-111111111111",
    "fullName": "Employee",
    "email": null
  },
  "assignee": {
    "id": "22222222-2222-2222-2222-222222222222",
    "fullName": "Network Technician",
    "email": null
  },
  "slaWindow": {
    "responseDeadline": "2026-11-08T01:30:00Z",
    "resolutionDeadline": "2026-11-08T03:15:00Z",
    "slaStatus": "WITHIN_SLA",
    "pausedSeconds": 0
  },
  "resolutionNotes": null,
  "createdAt": "2026-11-08T01:15:00Z",
  "updatedAt": "2026-11-08T01:15:02Z",
  "escalationLevel": "NONE",
  "parentIncident": null,
  "childIncidents": [
    { "id": "5c1d…", "incidentNumber": "INC-1004", "title": "No network in Lab 304", "status": "ASSIGNED" }
  ]
}
```

`escalationLevel` is `NONE`, `TEAM_LEAD`, `ADMINISTRATOR` or `IT_MANAGER`. `parentIncident` is the major incident this ticket is linked under (same shape as a `childIncidents` entry); `childIncidents` lists the tickets linked under this one.

### `TriageRequest` / `TriageResponse`
```json
{ "title": "Wi-Fi outage", "description": "The whole floor is offline, urgent" }
```
```json
{
  "category": "NETWORK",
  "impact": "HIGH",
  "urgency": "HIGH",
  "serviceId": "0b6f…",
  "serviceName": "Campus Wi-Fi",
  "predictedPriority": "P1_CRITICAL",
  "reason": "Several people are affected and work is blocked.",
  "source": "rules"
}
```

### `AdviceResponse`, `SummaryResponse`, `SelfHelpFeedbackRequest`
```json
{ "summary": "Try these first", "steps": ["Restart your computer and try again."], "source": "gemini" }
```
```json
{ "summary": "Lab 304 lost network after maintenance; switch port being checked.", "source": "rules" }
```
```json
{ "helped": true }
```

### `PublicIssueResponse`
```json
{
  "id": "e7b0a880-92a0-4b89-a5e4-cb911a3b3701",
  "incidentNumber": "INC-1001",
  "title": "Lab 304 Workstations cannot reach local gateway",
  "description": "All 20 desktops in Computer Lab 304 show IP autoconfiguration 169.254.x.x...",
  "status": "IN_PROGRESS",
  "priority": "P1_CRITICAL",
  "category": "NETWORK",
  "serviceName": "Campus Wi-Fi",
  "handledBy": "Network Technician",
  "createdAt": "2026-11-08T01:15:00Z",
  "updatedAt": "2026-11-08T01:40:00Z",
  "updateCount": 2,
  "updates": [
    { "id": "…", "kind": "PICKUP", "author": "Network Technician", "authorRole": "TECHNICIAN", "text": "Network Technician picked this up.", "createdAt": "2026-11-08T01:20:00Z" },
    { "id": "…", "kind": "COMMENT", "author": "Reporter", "authorRole": null, "text": "Still happening", "createdAt": "2026-11-08T01:40:00Z" }
  ]
}
```

### `LinkIncidentRequest`, `UpdateCapacityRequest`, `UpdateEmailRequest`
```json
{ "parentId": "e7b0a880-92a0-4b89-a5e4-cb911a3b3701" }
```
```json
{ "maxActiveTickets": 8, "teamLead": true }
```
```json
{ "email": "tech.net@example.edu" }
```

### `ServiceStatusResponse`
```json
{
  "serviceId": "0b6f…",
  "name": "Campus Wi-Fi",
  "criticality": "HIGH",
  "status": "OUTAGE",
  "openTickets": 3,
  "openMajorIncidents": 1
}
```

### Rate limiting
Any endpoint except the health check and `/events` can answer `429 Too Many Requests` with `Retry-After`, `RateLimit-Limit` and `RateLimit-Remaining` headers and an `ErrorResponse` whose message starts "Too many requests". The policies are listed in [security.md](security.md#rate-limiting-and-lockout).

### `SimilarIncidentResponse`
```json
{
  "incidentNumber": "INC-0842",
  "title": "Lab 304 switch port trunking issue after reboot",
  "similarityScore": 0.82,
  "status": "CLOSED",
  "resolutionSummary": "VLAN 40 configuration re-applied on switch SW-C304 port 24 and DHCP relay restarted."
}
```
