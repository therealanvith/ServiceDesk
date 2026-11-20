"use client";

import { useAuth } from "@/context/AuthContext";
import { type AuditLogItem, api } from "@/lib/apiClient";
import { auditLabel, formatDateTime, humanize, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

const AUDIT_LIMIT = 500;

export default function AuditPage() {
  const { user, loading: authLoading } = useAuth();
  const [logs, setLogs] = useState<AuditLogItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState("");
  const [query, setQuery] = useState("");

  const [reloadKey, setReloadKey] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    if (!isAdmin(user?.role)) return;
    setError(null);
    api
      .getRecentAuditLogs(AUDIT_LIMIT)
      .then(setLogs)
      .catch((err: Error) => setError(err.message));
  }, [user, reloadKey]);

  const actions = useMemo(() => [...new Set((logs ?? []).map((l) => l.action))].sort(), [logs]);
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (logs ?? []).filter(
      (l) =>
        (!action || l.action === action) &&
        (!q ||
          l.incidentNumber?.toLowerCase().includes(q) ||
          l.actorName.toLowerCase().includes(q) ||
          l.remarks?.toLowerCase().includes(q))
    );
  }, [logs, action, query]);

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

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Audit log</h1>
          <p>
            The most recent {AUDIT_LIMIT} recorded actions across all tickets, newest first. Search
            and filters apply to these; each ticket’s page has its full history.
          </p>
        </div>
      </div>
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      <section className="card">
        <div className="toolbar">
          <label className="search">
            <input
              type="search"
              placeholder="Ticket number, person or remark"
              aria-label="Search the audit log"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              style={{ paddingLeft: 10 }}
            />
          </label>
          <select
            aria-label="Action"
            className={`filter-select ${action ? "is-set" : ""}`}
            value={action}
            onChange={(e) => setAction(e.target.value)}
          >
            <option value="">Action: any</option>
            {actions.map((a) => (
              <option key={a} value={a}>
                Action: {auditLabel(a)}
              </option>
            ))}
          </select>
        </div>
        {error && !logs ? (
          <div className="empty" role="alert">
            <h2>Couldn’t load the audit log</h2>
            <button type="button" className="btn" onClick={() => setReloadKey((k) => k + 1)}>
              Try again
            </button>
          </div>
        ) : !logs ? (
          <output className="empty">Loading…</output>
        ) : visible.length === 0 ? (
          <p className="empty">No matching entries.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Ticket</th>
                  <th scope="col">Who</th>
                  <th scope="col">What</th>
                  <th scope="col" className="hide-sm">
                    Change
                  </th>
                  <th scope="col" className="hide-sm">
                    Remarks
                  </th>
                </tr>
              </thead>
              <tbody>
                {visible.map((log) => (
                  <tr key={log.id}>
                    <td className="col-nowrap muted">{formatDateTime(log.timestamp)}</td>
                    <td className="col-id">
                      <Link href={`/incidents/${log.incidentId}`} className="link">
                        {log.incidentNumber ?? "—"}
                      </Link>
                    </td>
                    <td className="col-nowrap">{log.actorName}</td>
                    <td>{auditLabel(log.action)}</td>
                    <td className="hide-sm col-nowrap">
                      {log.oldValue && (
                        <span className="timeline-old">{humanize(log.oldValue)}</span>
                      )}
                      {log.oldValue && log.newValue && " → "}
                      {log.newValue && humanize(log.newValue)}
                    </td>
                    <td className="hide-sm audit-remark">{log.remarks}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
