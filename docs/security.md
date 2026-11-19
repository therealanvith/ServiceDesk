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

## 2. Roles and permissions

Authorization is enforced on the server in three layers: URL rules (`SecurityConfig`), method
annotations (`@PreAuthorize`), and per-ticket checks in `IncidentApplicationService`. The UI hides
what a role can't do, but never relies on that.

| Capability | Employee | Technician | Administrator | Root |
| :--- | :---: | :---: | :---: | :---: |
| Report an issue, comment, reply to questions on own tickets | ✓ | ✓ | ✓ | ✓ |
| See tickets in the list | Own | Assigned to them | All | All |
| Open a ticket | Own | Assigned or own | All | All |
| Start work, request info, resolve | — | Assigned only | ✓ | ✓ |
| Confirm and close (with rating) | Own | Own | ✓ | ✓ |
| Cancel | Own, before assignment | Own, before assignment | ✓ | ✓ |
| Internal notes | — | ✓ | ✓ | ✓ |
| Assign / reassign, override priority | — | — | ✓ | ✓ |
| Reports, audit log, SLA targets, services, assignment rule, skills | — | — | ✓ | ✓ |
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
- Secrets (`JWT_SECRET`, `ROOT_PASSWORD`, database password, `GOOGLE_CLIENT_ID`) come from the
  environment. Locally they live in `.env`, which is gitignored; `.env.example` documents them
  without values.
- The backend container runs as a non-root user.
- Production should sit behind HTTPS (TLS termination at the load balancer or reverse proxy), and
  the Google client id's authorised JavaScript origins must list only the real frontend URL.

## 6. Known limitations

- JWTs are kept in `localStorage`, which an XSS bug could read. React escapes all rendered text
  and the app renders no user HTML, which limits the risk. An HttpOnly cookie session would
  remove it.
- There is no login rate limiting or account lockout yet. In production this belongs at the
  gateway (for example, a limit per IP and per username).
- Pre-seeded demo accounts share a known password. Deactivate them in any real deployment from the
  Users page.
