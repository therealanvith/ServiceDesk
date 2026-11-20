# Database Schema & Relational Design

## 1. Schema Overview & Relational Model

The database is built on **PostgreSQL 16** with schema evolution strictly managed through versioned **Flyway migrations** (`backend/src/main/resources/db/migration/`). All primary keys use RFC 4122 UUIDs (`gen_random_uuid()` in PostgreSQL and compatible with embedded H2 for testing).

The visual entity-relationship diagram is defined in [er-diagram.mmd](diagrams/er-diagram.mmd).

---

## 2. Table Specifications

### 2.1 `users`
Stores user authentication credentials, profiles, and role assignments.
- `id` (`UUID`, PK, default `gen_random_uuid()`): Unique user ID.
- `username` (`VARCHAR(50)`, UNIQUE, NOT NULL): System login username.
- `password_hash` (`VARCHAR(255)`, NOT NULL): BCrypt hashed password.
- `full_name` (`VARCHAR(100)`, NOT NULL): Display name.
- `email` (`VARCHAR(100)`, UNIQUE, NOT NULL): Corporate or campus email address.
- `role` (`VARCHAR(30)`, NOT NULL): Role check constraint (`'EMPLOYEE', 'TECHNICIAN', 'ADMINISTRATOR'`).
- `is_active` (`BOOLEAN`, NOT NULL, default `true`): Soft activation flag.
- `created_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).
- `updated_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).

### 2.2 `user_skills`
Maps technicians to supported problem categories.
- `id` (`UUID`, PK).
- `user_id` (`UUID`, FK -> `users(id)` ON DELETE CASCADE, NOT NULL).
- `category` (`VARCHAR(50)`, NOT NULL): Category check constraint (`'NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS'`).
- *Constraint*: `UNIQUE(user_id, category)`.

### 2.3 `incidents`
Central table representing reported issues, status, SLA tracking, and assignment.
- `id` (`UUID`, PK).
- `incident_number` (`VARCHAR(20)`, UNIQUE, NOT NULL): Human-readable ticket number (e.g. `INC-1001`).
- `title` (`VARCHAR(200)`, NOT NULL): Incident summary.
- `description` (`TEXT`, NOT NULL): Diagnostic details.
- `category` (`VARCHAR(50)`, NOT NULL): `'NETWORK', 'HARDWARE', 'SOFTWARE', 'ACCESS'`.
- `status` (`VARCHAR(30)`, NOT NULL): State pattern status (`'REPORTED', 'ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'PENDING_INFO', 'RESOLVED', 'CLOSED', 'CANCELLED'`; `ACKNOWLEDGED` added in V15).
- `priority` (`VARCHAR(30)`, NOT NULL): Calculated priority (`'P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW'`).
- `impact` (`VARCHAR(30)`, NOT NULL): `'HIGH', 'MEDIUM', 'LOW'`.
- `urgency` (`VARCHAR(30)`, NOT NULL): `'HIGH', 'MEDIUM', 'LOW'`.
- `reporter_id` (`UUID`, FK -> `users(id)`, NOT NULL): Creator of incident.
- `assignee_id` (`UUID`, FK -> `users(id)`, NULLABLE): Assigned technician.
- `response_deadline` (`TIMESTAMPTZ`, NOT NULL): Target first response time.
- `resolution_deadline` (`TIMESTAMPTZ`, NOT NULL): Target resolution time.
- `paused_at` (`TIMESTAMPTZ`, NULLABLE): Timestamp when ticket entered `PENDING_INFO`.
- `paused_duration_seconds` (`BIGINT`, NOT NULL, default `0`): Cumulative seconds paused during `PENDING_INFO`.
- `sla_status` (`VARCHAR(30)`, NOT NULL, default `'WITHIN_SLA'`): `'WITHIN_SLA', 'WARNING_BREACH_IMMINENT', 'BREACHED'`.
- `resolution_notes` (`TEXT`, NULLABLE): Summary entered by technician upon resolution.
- `resolved_at` (`TIMESTAMPTZ`, NULLABLE).
- `closed_at` (`TIMESTAMPTZ`, NULLABLE).
- `created_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).
- `updated_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).

### 2.4 `incident_comments`
Discussion and diagnostic thread entries.
- `id` (`UUID`, PK).
- `incident_id` (`UUID`, FK -> `incidents(id)` ON DELETE CASCADE, NOT NULL).
- `author_id` (`UUID`, FK -> `users(id)`, NOT NULL).
- `comment_text` (`TEXT`, NOT NULL).
- `is_internal` (`BOOLEAN`, NOT NULL, default `false`): Internal technician-only notes.
- `kind` (`VARCHAR(20)`, NOT NULL, default `'COMMENT'`, V14): What the entry in the Updates thread is. CHECK `chk_comment_kind`: `'COMMENT'` (typed by a person), `'QUESTION'` (request for info), `'ANSWER'` (reporter's reply), `'PICKUP'` (acknowledged or started), `'RESOLUTION'` (the fix), `'STATUS'` (closed, cancelled, linked and similar). Only `COMMENT` rows are typed by people; the others are written by the lifecycle actions.
- `created_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).

### 2.5 `incident_audit_logs`
Append-only historical ledger capturing all mutations.
- `id` (`UUID`, PK).
- `incident_id` (`UUID`, FK -> `incidents(id)` ON DELETE CASCADE, NOT NULL).
- `actor_id` (`UUID`, FK -> `users(id)`, NULLABLE): User ID or NULL for system daemon.
- `action` (`VARCHAR(50)`, NOT NULL): Action identifier (`'CREATED', 'STATUS_CHANGED', 'ASSIGNED', 'PRIORITY_OVERRIDDEN', 'SLA_BREACHED', 'ESCALATED', 'COMMENTED', 'LINKED', 'UNLINKED'`). For `ESCALATED`, `old_value` / `new_value` hold the previous and new escalation level; for `LINKED` / `UNLINKED`, the main ticket's number.
- `old_value` (`VARCHAR(100)`, NULLABLE).
- `new_value` (`VARCHAR(100)`, NULLABLE).
- `remarks` (`TEXT`, NULLABLE).
- `created_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).

### 2.6 `notifications`
In-app notification records for users.
- `id` (`UUID`, PK).
- `recipient_id` (`UUID`, FK -> `users(id)` ON DELETE CASCADE, NOT NULL).
- `incident_id` (`UUID`, FK -> `incidents(id)` ON DELETE SET NULL, NULLABLE).
- `type` (`VARCHAR(50)`, NOT NULL): Event type descriptor.
- `title` (`VARCHAR(255)`, NOT NULL).
- `message` (`TEXT`, NOT NULL).
- `is_read` (`BOOLEAN`, NOT NULL, default `false`).
- `created_at` (`TIMESTAMPTZ`, NOT NULL, default `now()`).

---

### 2.7 `it_services`
The IT service catalogue that incidents are reported against.

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | UUID | Primary key |
| `name` | VARCHAR(100) | Unique, e.g. "Payment Application" |
| `description` | TEXT | Shown to reporters |
| `criticality` | VARCHAR(20) | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`; feeds the priority score |
| `is_active` | BOOLEAN | Retired services keep their history but leave the report form |
| `created_at` | TIMESTAMPTZ | |

### 2.8 `sla_policies`
Administrator-editable targets, one row per priority.

| Column | Type | Notes |
| :--- | :--- | :--- |
| `priority` | VARCHAR(30) | Primary key: `P1_CRITICAL` … `P4_LOW` |
| `response_minutes` | INTEGER | > 0 |
| `resolution_minutes` | INTEGER | ≥ response_minutes (CHECK) |
| `updated_at` | TIMESTAMPTZ | |

### 2.9 Later additions to existing tables
- `incidents.service_id` (FK → `it_services`), `responded_at` (first technician action, measured against the response target), `escalated_at`, `feedback_rating` (SMALLINT 1–5, CHECK) and `feedback_comment`.
- `incidents.version`: optimistic locking counter.
- `users.password_hash` is nullable (Google-only accounts); `users.email` is nullable; `users.google_sub` holds the stable Google account id (unique when present).
- `users.role` also allows `ROOT`; a partial unique index (`WHERE role = 'ROOT'`) guarantees a single root account.
- Categories now include `DATABASE`, `CLOUD`, `SECURITY`, `APPLICATION` and `STORAGE` (CHECK constraints on `incidents.category` and `user_skills.category`).
- `users.must_change_password` (BOOLEAN, default `false`, V13): set for accounts an administrator creates with a temporary password; the API refuses other requests until it is changed.
- `incident_comments.kind` (V14): see 2.4.
- `incidents.escalation_level` (VARCHAR(20), NOT NULL, default `'NONE'`, V15): `NONE`, `TEAM_LEAD`, `ADMINISTRATOR` or `IT_MANAGER` (CHECK `chk_incident_escalation_level`). Tickets escalated under the old single-step rule (`escalated_at` set) were migrated to `ADMINISTRATOR`.
- `users.max_active_tickets` (INT, NOT NULL, default 5, CHECK 1–50, V15): a technician's open-ticket limit, respected by automatic and manual assignment. Open means not `RESOLVED`, `CLOSED` or `CANCELLED`.
- `users.is_team_lead` (BOOLEAN, NOT NULL, default `false`, V15): receives first-level escalations. The seeded `tech_lead` account is set to `true`.
- `incidents.parent_incident_id` (UUID, FK → `incidents(id)` ON DELETE SET NULL, nullable, V17): the major incident a ticket is linked under. CHECK `chk_incident_not_own_parent` stops self-links; index `idx_incidents_parent` serves "linked tickets" lookups. The application keeps links one level deep.

### 2.10 `self_help_feedback` (V16)
Whether the assistant's "Try this first" steps solved a problem without a ticket being filed. Feeds the self-help deflection figure in the trends report.

| Column | Type | Notes |
| :--- | :--- | :--- |
| `id` | UUID | Primary key |
| `user_id` | UUID | FK → `users(id)` ON DELETE CASCADE, NOT NULL |
| `helped` | BOOLEAN | NOT NULL |
| `created_at` | TIMESTAMPTZ | NOT NULL, default `CURRENT_TIMESTAMP`; indexed (`idx_self_help_feedback_time`) for time-window reports |

---

## 3. Indexing & Query Optimization Strategy

To satisfy **NFR-02** ($< 200\text{ ms}$ query response), the following indexes are specified:

1. **`idx_incidents_status_priority`**: `CREATE INDEX idx_incidents_status_prio ON incidents(status, priority);`
   - Accelerates triage queues and technician dashboards.
2. **`idx_incidents_assignee`**: `CREATE INDEX idx_incidents_assignee ON incidents(assignee_id) WHERE status NOT IN ('CLOSED', 'CANCELLED');`
   - Partial index optimizing `LeastLoadedAssignmentStrategy` lookups for active workloads.
3. **`idx_incidents_reporter`**: `CREATE INDEX idx_incidents_reporter ON incidents(reporter_id);`
   - Speeds up employee self-service "My Incidents" queries.
4. **`idx_incidents_sla_monitoring`**: `CREATE INDEX idx_incidents_sla ON incidents(sla_status, resolution_deadline) WHERE status NOT IN ('RESOLVED', 'CLOSED', 'CANCELLED');`
   - Used by the scheduled background task evaluating imminent SLA breaches.
5. **`idx_audit_incident_time`**: `CREATE INDEX idx_audit_incident_time ON incident_audit_logs(incident_id, created_at ASC);`
   - Ensures instantaneous retrieval of chronological ticket audit histories.

---

## 4. Flyway Migration Plan

- **`V1__baseline.sql`**: Initial empty schema verification table.
- **`V2__create_users_and_skills.sql`**: Core user tables, password hashes, and skill mappings.
- **`V3__create_incidents_and_audit.sql`**: Incidents, SLA columns, comments, and audit logging tables.
- **`V4__create_notifications.sql`**: In-app notifications table.
- **`V5__seed_demo_data.sql`**: Seed data with sample employees, specialized technicians, and active demo incidents.
- **`V6__fix_demo_user_passwords.sql`**: Corrects the demo account password hashes.
- **`V7__persistent_incident_numbers.sql`**: Database sequence for `INC-` numbers, safe across restarts and instances.
- **`V8__incident_optimistic_locking.sql`**: `version` column for concurrent-edit protection.
- **`V9__role_based_demo_accounts.sql`**: Demo accounts named by role (`emp`, `tech_net`, `tech_hw`, `tech_lead`, `admin`); email made optional.
- **`V10__root_role_and_google_sign_in.sql`**: `ROOT` role, single-root index, Google account id, optional password.
- **`V11__services_sla_policies_feedback.sql`**: IT services, SLA policies, wider categories, response/escalation tracking and feedback.
- **`V12__demo_password_test.sql`**: Demo accounts use the password `test` (root untouched).
- **`V13__must_change_password.sql`**: `users.must_change_password` for administrator-created accounts.
- **`V14__ticket_update_kinds.sql`**: `incident_comments.kind` for the Updates thread.
- **`V15__acknowledge_capacity_escalation.sql`**: `ACKNOWLEDGED` status, `incidents.escalation_level`, `users.max_active_tickets` and `users.is_team_lead`.
- **`V16__self_help_feedback.sql`**: `self_help_feedback` table.
- **`V17__major_incidents.sql`**: `incidents.parent_incident_id`, self-link check and index.
