"use client";

import { useAuth } from "@/context/AuthContext";
import { type Role, type UserAccount, api } from "@/lib/apiClient";
import { ROLE_LABELS, formatDateTime, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

const EMPTY_FORM = {
  username: "",
  fullName: "",
  email: "",
  role: "EMPLOYEE" as Role,
  password: "",
};

export default function UsersPage() {
  const { user, loading: authLoading } = useAuth();
  const [accounts, setAccounts] = useState<UserAccount[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  // Accounts with an update in flight; each stays locked until its own request settles.
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());
  const [loadFailed, setLoadFailed] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [query, setQuery] = useState("");

  const isRoot = user?.role === "ROOT";
  // Administrators manage employees and technicians; root can also grant administrator access.
  const grantable: Role[] = isRoot
    ? ["EMPLOYEE", "TECHNICIAN", "ADMINISTRATOR"]
    : ["EMPLOYEE", "TECHNICIAN"];

  const load = useCallback(() => {
    setLoadFailed(false);
    api
      .listUsers()
      .then(setAccounts)
      .catch((err: Error) => {
        setError(err.message);
        setLoadFailed(true);
      });
  }, []);

  useEffect(() => {
    if (isAdmin(user?.role)) load();
  }, [user, load]);

  const canManage = (account: UserAccount) =>
    account.role !== "ROOT" &&
    account.username !== user?.username &&
    (isRoot || !isAdmin(account.role));

  const run = async (id: string, action: () => Promise<UserAccount>, done: string) => {
    setBusyIds((prev) => new Set(prev).add(id));
    setError(null);
    setMessage(null);
    try {
      const updated = await action();
      setAccounts((list) => list?.map((a) => (a.id === updated.id ? updated : a)) ?? null);
      setMessage(done);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Update failed");
    } finally {
      setBusyIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const createAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setError(null);
    setMessage(null);
    try {
      const created = await api.createUser({
        username: form.username.trim().toLowerCase(),
        fullName: form.fullName.trim(),
        email: form.email.trim() || undefined,
        role: form.role,
        password: form.password,
      });
      setForm(EMPTY_FORM);
      setMessage(
        `Created ${created.username}. Share the temporary password with them directly; they'll be asked to change it.`
      );
      // Add the new row in place rather than reloading, so in-flight edits to others survive.
      setAccounts((list) => (list ? [...list, created] : [created]));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Couldn't create the account");
    } finally {
      setCreating(false);
    }
  };

  if (authLoading || !user) return <output className="empty">Loading…</output>;
  if (!isAdmin(user.role)) {
    return (
      <div className="card empty">
        <h2>Administrators only</h2>
        <Link className="btn" href="/">
          Back to tickets
        </Link>
      </div>
    );
  }

  const q = query.trim().toLowerCase();
  const visible = (accounts ?? []).filter(
    (a) =>
      !q || a.username.includes(q) || a.fullName.toLowerCase().includes(q) || a.email?.includes(q)
  );

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Users</h1>
          <p>
            People who sign in with Google start as employees. Change their role here.
            {isRoot ? "" : " Only root can grant administrator access."}
          </p>
        </div>
      </div>

      {(message || error) && (
        <p
          className={`alert ${error ? "" : "alert-success"}`}
          role={error ? "alert" : "status"}
          style={{ marginBottom: 16 }}
        >
          {error ?? message}
        </p>
      )}

      <section className="card">
        <div className="toolbar">
          <label className="search">
            <input
              type="search"
              placeholder="Search people"
              aria-label="Search people"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              style={{ paddingLeft: 10 }}
            />
          </label>
        </div>
        {loadFailed ? (
          <div className="empty" role="alert">
            <h2>Couldn’t load people</h2>
            <button type="button" className="btn" onClick={load}>
              Try again
            </button>
          </div>
        ) : !accounts ? (
          <output className="empty">Loading…</output>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Role</th>
                  <th scope="col" className="hide-sm">
                    Sign-in
                  </th>
                  <th scope="col" className="hide-sm">
                    Joined
                  </th>
                  <th scope="col">Access</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((a) => {
                  const manageable = canManage(a);
                  return (
                    <tr key={a.id} className={a.active ? undefined : "is-inactive"}>
                      <td>
                        {a.fullName}
                        <span className="props-sub">
                          <span className="mono">{a.username}</span>
                          {a.email && ` · ${a.email}`}
                        </span>
                      </td>
                      <td>
                        {manageable ? (
                          <select
                            className="filter-select is-set"
                            aria-label={`Role for ${a.username}`}
                            value={a.role}
                            disabled={busyIds.has(a.id)}
                            onChange={(e) =>
                              run(
                                a.id,
                                () => api.updateUserRole(a.id, e.target.value as Role),
                                `${a.username} is now ${ROLE_LABELS[e.target.value as Role].toLowerCase()}.`
                              )
                            }
                          >
                            {(grantable.includes(a.role) ? grantable : [a.role, ...grantable]).map(
                              (r) => (
                                <option key={r} value={r}>
                                  {ROLE_LABELS[r]}
                                </option>
                              )
                            )}
                          </select>
                        ) : (
                          ROLE_LABELS[a.role]
                        )}
                      </td>
                      <td className="hide-sm muted">
                        {[a.passwordSignIn && "Password", a.googleSignIn && "Google"]
                          .filter(Boolean)
                          .join(", ") || "—"}
                      </td>
                      <td className="hide-sm muted col-nowrap">{formatDateTime(a.createdAt)}</td>
                      <td>
                        {manageable ? (
                          <button
                            type="button"
                            className={`btn btn-sm ${a.active ? "btn-danger" : ""}`}
                            disabled={busyIds.has(a.id)}
                            onClick={() =>
                              run(
                                a.id,
                                () => api.setUserActive(a.id, !a.active),
                                `${a.username} ${a.active ? "deactivated" : "reactivated"}.`
                              )
                            }
                          >
                            {a.active ? "Deactivate" : "Reactivate"}
                          </button>
                        ) : (
                          <span className="muted">{a.active ? "Active" : "Deactivated"}</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="card-header">
          <h2>Add a person</h2>
          <span className="hint">For people who won’t use Google sign-in</span>
        </div>
        <form className="card-body form-inline" onSubmit={createAccount}>
          <label className="field">
            Full name
            <input
              required
              maxLength={100}
              value={form.fullName}
              onChange={(e) => setForm({ ...form, fullName: e.target.value })}
            />
          </label>
          <label className="field">
            Username
            <input
              required
              pattern="[a-z0-9._\-]{3,50}"
              title="3-50 characters: lowercase letters, digits, '.', '_' or '-'"
              autoCapitalize="none"
              spellCheck={false}
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
            />
          </label>
          <label className="field">
            Email <span className="field-hint">(optional)</span>
            <input
              type="email"
              maxLength={100}
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </label>
          <label className="field">
            Role
            <select
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as Role })}
            >
              {grantable.map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABELS[r]}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            Temporary password
            <input
              type="password"
              required
              minLength={12}
              maxLength={128}
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </label>
          <div className="form-inline-actions">
            <button type="submit" className="btn btn-primary" disabled={creating}>
              {creating ? "Adding…" : "Add person"}
            </button>
          </div>
        </form>
      </section>
    </>
  );
}
