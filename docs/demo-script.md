# Demonstration script: three complete workflows

**Project**: BITS ServiceDesk, Intelligent IT Service Management & Incident Resolution System

The course brief asks for at least three complete workflows in the recorded demo. This file lists the
exact clicks. The 15 to 20 minute video script with speaker parts is in `VIDEO-SCRIPT.md`.

**Setup**: `docker compose up -d --build`, open http://localhost:3000. Demo accounts (password `test`):
`emp` (employee), `tech_net` (network technician), `tech_hw` (hardware technician), `tech_lead` (team
lead), `admin` (administrator). Use separate browser profiles or private windows so several people can
stay signed in. Without a `GEMINI_API_KEY` the assistant answers from the built-in knowledge base, and
the demo works the same way.

---

## Workflow 1: Report, prioritise, assign, notify

**Brief wording**: employee reports, the system prioritises, a technician is assigned, a notification is generated.

1. Sign in as `emp`. Open **Report an issue**.
2. Title `Payment application is unavailable`, description `Users cannot complete transactions at the
   fee counter`. Click out of the description box: a **quick fix** appears by itself.
3. Click **Suggest details**: category, impact, urgency and the affected service fill in.
4. Choose the service *Payment Application* (criticality High). Watch the **Priority** panel: the server
   calculates it from impact, urgency and the service's business criticality.
5. Submit. Open the ticket: it already has a technician (chosen by the active assignment strategy), a
   priority, SLA targets (response and resolution deadlines) and a `CREATED` audit entry.
6. Sign in as the assigned technician. The ticket is in **My queue** and the inbox has an *Incident
   Assigned* notification (the same event also sends an email when `BREVO_API_KEY` is set, and a live
   update to the open page).
7. As `admin`, open **Settings** and switch the assignment strategy (skill match, least loaded, round
   robin), then report another ticket to show a different choice.

**What it proves**: factory-built ticket, strategy-based priority and assignment, observer-based
notification, audit trail.

## Workflow 2: Investigate, monitor the SLA, resolve, notify

1. Sign in as the assigned technician. On **Board** (or the ticket) click **Acknowledge**: the response
   target is met.
2. Open the ticket: the assistant has already written a **summary** and a **suggested fix** (saved, so the
   next visit is instant). Click **Start work**.
3. Click **Request info** and ask a question. The status becomes *Pending info* and the **SLA clock
   pauses**.
4. As `emp`, open the ticket and answer. The status returns to *In progress* and the clock resumes.
5. As the technician click **Resolve** (use the suggested fix if it fits). The reporter gets a
   notification.
6. As `emp`, click **Close** and rate the support (1 to 5). Show the **audit log**: every step with time,
   user, old and new state.

**What it proves**: state pattern (illegal moves are refused: try closing a ticket that was never
resolved), SLA tracking with pause and resume, notifications, feedback.

## Workflow 3: Duplicates, correlation and escalation

1. As `emp`, start a report that looks like an existing open one. The form shows **This may already be
   reported** with the matching ticket and a similarity score.
2. Open **All issues**, expand that issue and click **This affects me too** as a second employee. No new
   ticket is created; the affected count goes up, the issue appears in that employee's My tickets as
   followed, and with 3 or more people affected the **priority is raised** and audited.
3. As `admin`, open **Service status**: a service with several open tickets turns *Degraded* or
   *Outage*. Open the main ticket and link a related ticket under it (a **major incident**). Resolving
   the main ticket resolves every linked ticket.
4. Escalation: leave a P1 ticket without acknowledgement. After the response target the **team lead**
   (`tech_lead`) is notified; later administrators are notified and the ticket is reassigned; a missed
   resolution target on a P1 or P2 notifies the IT manager (root). Each step writes an `ESCALATED` audit
   entry. For a quick demonstration use the `SLA check` button in **Settings**.
5. As `tech_lead`, open **All incidents** and **Reassign** a ticket to another technician. The capacity
   limit is respected: a technician already at their limit is refused with an explanation.
6. As `admin`, open **Reports**: totals, open, critical, SLA violations, technician workload, service
   analysis, trends over 7/30/90 days, and the self-help deflection figure.

**What it proves**: similarity strategy, correlation, chain of responsibility (escalation), decorator
(capacity limits), reporting.

---

## Extras to mention (30 seconds each)

- **Help chat** (round button, bottom right): asks Gemini, falls back to the knowledge base; "where is my
  ticket?" lists only your own tickets.
- **Categories**: an administrator can switch a category off in Settings; it disappears from the report
  form.
- **Roles**: administrators can promote people to administrator (Users page); only root can change an
  administrator afterwards.
- **Security**: role-based access on every endpoint, JWT, login lockout, rate limits, hidden reporter
  identity on the public list.
