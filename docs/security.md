# Security

ServiceDesk handles internal incident data, so it has to authenticate everyone, enforce who can
see and change each ticket, and avoid leaking information through errors.

## 1. Authentication

| Mechanism | Details |
| :--- | :--- |
| Username + password | BCrypt hashes (cost 10). Passwords for new accounts must be 12–128 characters. A failed login always says "Invalid username or password", never which part was wrong. |
| Google sign-in (OAuth 2.0 / OpenID Connect) | The browser gets an ID token from Google Identity Services and posts it to `POST /api/v1/auth/google`. The server verifies it with Spring Security's Nimbus JWT decoder: RS256 signature against Google's published keys, issuer `accounts.google.com`, audience equal to our client id, expiry, and `email_verified = true`. `GOOGLE_ALLOWED_DOMAINS` can restrict sign-in to an organisation's Workspace domain (`hd` claim). |
| Account linking | Accounts are matched by Google's stable subject id, then by verified email. If an email already belongs to a *different* Google account the sign-in is refused, so an account can't be taken over. First-time Google users become employees. |
| Self-registration | `POST /api/v1/auth/register` always creates an EMPLOYEE, whatever the request says. Can be turned off with `app.auth.registration-enabled=false`. |
| Sessions | Stateless HS256 JWTs signed with `JWT_SECRET`, valid 24 hours. The server refuses to start with a secret shorter than 32 bytes, signatures are compared in constant time, and the cloud profile has no default secret. Every request re-loads the user, so a deactivated or deleted account loses access immediately even with a valid token. |

### Rate limiting and lockout

Token buckets (`infrastructure/security/ratelimit`) throttle the API by policy. Over the limit the
API answers **429** with `Retry-After`, `RateLimit-Limit` and `RateLimit-Remaining`, and the
sign-in and sign-up buttons count down until they work again.

| Policy | Applies to | Keyed by | Default |
| :--- | :--- | :--- | :--- |
| `auth` | `POST /auth/login`, `/auth/google`, `/auth/register` | client address | 10 / min |
| `ai` | `/incidents/triage`, `/incidents/self-help`, `/incidents/*/ai/**` | user | 20 / min |
| `write` | other POST / PUT / PATCH / DELETE | user | 120 / min |
| `api` | everything else | user, or address when signed out | 600 / min |

- **Account lockout.** Five wrong passwords for one username within 15 minutes lock it for 15
  minutes, whichever addresses they come from. The answer stays "Invalid username or password"
  (unknown usernames are tracked the same way), so lockouts reveal nothing about which accounts
  exist. Lockouts are logged as security warnings.
- **Client address.** The connecting address is used. `X-Forwarded-For` is trusted only when
  `app.rate-limit.trust-forwarded-for=true`, for running behind a known proxy; otherwise a client
  could pick any address by sending the header itself.
- The health check and the live-updates stream are not limited.

### Assistant (Gemini) and prompt injection

- `GEMINI_API_KEY` lives only on the server; the browser talks to our API, never to Google.
- Ticket text is wrapped in `<ticket>` tags and the system instruction says it is data, never
  instructions. Answers are forced into a JSON schema and checked again on the server: enum values
  must parse, a suggested service must be one we offered, steps are capped at four and all text
  is length-limited and stripped of control characters. Anything else is discarded.
- If Gemini is not configured, times out (8 s) or returns something unusable, the rule-based
  assistant answers instead; each answer says which one produced it.
- Self-help for employees uses only the fix text of similar resolved tickets: never another
  ticket's number, title or reporter. Fix suggestions and summaries are staff-only and only for
  tickets the user may open.
- AI endpoints have their own per-user rate limit (20 / min) because they call a paid API.

### Live updates and email

- The live stream (`GET /api/v1/events`) needs a signed-in user. The browser reads it with
  `fetch` so the JWT travels in the `Authorization` header, never in the URL (where it would end
  up in logs). Only the async dispatch Spring makes when a stream ends is let through without a
  token; it carries no data.
- `incident-updated` events carry only the ticket id, number and status, and go only to the
  reporter, the assignee and administrators. Pages refetch through the scoped endpoints, so the
  stream can't leak anything the user couldn't already open.
- Emails are sent only to the address on the recipient's own account. Title and body are
  HTML-escaped before they go into the message, and `BREVO_API_KEY` is never logged, even when
  the API call fails.
- New sign-ups (password or first Google sign-in) always start as employees, and every
  administrator and root is notified so the account can be reviewed.

## 2. Roles and permissions

Authorization is enforced on the server in three layers: URL rules (`SecurityConfig`), method
annotations (`@PreAuthorize`), and per-ticket checks in `IncidentApplicationService`. The UI hides
what a role can't do, but never relies on that.

| Capability | Employee | Technician | Administrator | Root |
| :--- | :---: | :---: | :---: | :---: |
| Report an issue, comment, reply to questions on own tickets | ✓ | ✓ | ✓ | ✓ |
| See tickets in the list | Own | Assigned to them | All | All |
| Open a ticket | Own | Assigned or own | All | All |
| All issues, read-only (no reporter identity, internal notes or history) | ✓ | ✓ | ✓ | ✓ |
| Acknowledge, start work, request info, resolve | — | Assigned only | ✓ | ✓ |
| Confirm and close (with rating) | Own | Own | ✓ | ✓ |
| Cancel | Own, before assignment | Own, before assignment | ✓ | ✓ |
| Internal notes | — | ✓ | ✓ | ✓ |
| Assign / reassign, override priority, link major incidents | — | — | ✓ | ✓ |
| Reports and trends, audit log, SLA targets, services, assignment rule, skills, capacity | — | — | ✓ | ✓ |
| Manage employees and technicians | — | — | ✓ | ✓ |
| Grant or remove administrator access | — | — | — | ✓ |

- A ticket outside someone's scope returns **404**, not 403, so ticket ids can't be probed.
- Duplicate checks and "related tickets" only consider tickets the requester may already see.
- There is exactly one root account, enforced by a partial unique index. It is created at first
  start from `ROOT_USERNAME` / `ROOT_PASSWORD`, read only while no root exists, so the
  password is never in source control and later restarts can't overwrite it. Root can't be
  demoted or deactivated through the API, and nobody can change their own role.

## 3. Input validation and error handling

- Every request body is validated with Bean Validation: required fields, enum values, text
  lengths (titles 200, descriptions and comments 10,000 characters), username format, email
  format, rating 1–5 and SLA minute ranges.
- `GlobalExceptionHandler` turns domain errors into consistent JSON with the right status:
  400 for invalid input, 401 for bad credentials, 403 for forbidden actions, 404 for missing or
  out-of-scope tickets, 409 for invalid state transitions and concurrent edits. Unexpected errors
  are logged with a stack trace but return a generic 500 message.
- All queries go through JPA with bound parameters; no SQL is built from user input.

## 4. Data integrity

- **Optimistic locking** (`@Version` on incidents): if two people change the same ticket at once,
  the second gets "This ticket was just changed by someone else" instead of silently overwriting.
- The SLA background job evaluates each ticket in its own short transaction against a fresh copy,
  so it never overwrites a user's change.
- Notifications are sent only after the business transaction commits, so a failing channel can't
  roll back the user's action and nobody is notified about a change that didn't happen.
- The audit log is append-only from the application's point of view.

## 5. Transport and deployment

- CORS allows only the origins in `CORS_ALLOWED_ORIGINS`.
- Secrets (`JWT_SECRET`, `ROOT_PASSWORD`, database password, `GOOGLE_CLIENT_ID`,
  `GEMINI_API_KEY`, `BREVO_API_KEY`) come from the environment. Locally they live in `.env`, which is gitignored; `.env.example` documents them
  without values.
- The backend container runs as a non-root user.
- Production should sit behind HTTPS (TLS termination at the load balancer or reverse proxy), and
  the Google client id's authorised JavaScript origins must list only the real frontend URL.

## 6. Known limitations

- JWTs are kept in `localStorage`, which an XSS bug could read. React escapes all rendered text
  and the app renders no user HTML, which limits the risk. An HttpOnly cookie session would
  remove it.
- Rate limits and lockouts are kept in memory, so they apply per instance and reset on restart.
  Several instances would share them through Redis behind the same `RateLimiter` interface.
- Pre-seeded demo accounts share a known password. Deactivate them in any real deployment from the
  Users page.
