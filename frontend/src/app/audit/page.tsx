"use client";

import { useAuth } from "@/context/AuthContext";
import { type AuditLogItem, api } from "@/lib/apiClient";
import { auditLabel, formatDateTime, humanize, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

export default function AuditPage() {
  const { user, loading: authLoading } = useAuth();
  const [logs, setLogs] = useState<AuditLogItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState("");
  const [query, setQuery] = useState("");

  useEffect(() => {
    if (!isAdmin(user?.role)) return;
    api
      .getRecentAuditLogs(500)
      .then(setLogs)
      .catch((err: Error) => setError(err.message));
  }, [user]);

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
          <p>Every recorded action across all tickets, newest first.</p>
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
        {!logs ? (
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
