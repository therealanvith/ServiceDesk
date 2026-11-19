"use client";

import { useAuth } from "@/context/AuthContext";
import { type Report, api } from "@/lib/apiClient";
import { CATEGORY_LABELS, PRIORITY_LABELS, STATUS_LABELS, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { useEffect, useState } from "react";

/** One-series horizontal bars: the value is always printed, the bar only adds shape. */
function BarList({
  title,
  rows,
}: {
  title: string;
  rows: { label: string; value: number; detail?: string }[];
}) {
  const max = Math.max(1, ...rows.map((r) => r.value));
  return (
    <section className="card">
      <div className="card-header">
        <h2>{title}</h2>
      </div>
      {rows.length === 0 ? (
        <p className="card-body muted">No tickets yet.</p>
      ) : (
        <ul className="bar-list">
          {rows.map((row) => (
            <li
              key={row.label}
              title={`${row.label}: ${row.value}${row.detail ? ` (${row.detail})` : ""}`}
            >
              <span className="bar-label">{row.label}</span>
              <span className="bar-track" aria-hidden="true">
                <span className="bar-fill" style={{ width: `${(row.value / max) * 100}%` }} />
              </span>
              <span className="bar-value">
                {row.value}
                {row.detail && <span className="muted"> · {row.detail}</span>}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function entries(map: Record<string, number>, labels: Record<string, string>) {
  return Object.entries(labels).flatMap(([key, label]) => {
    const value = map[key];
    return value ? [{ label, value }] : [];
  });
}

export default function ReportsPage() {
  const { user, loading: authLoading } = useAuth();
  const [report, setReport] = useState<Report | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin(user?.role)) return;
    api
      .getReport()
      .then(setReport)
      .catch((err: Error) => setError(err.message));
  }, [user]);

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
  if (error) {
    return (
      <p className="alert" role="alert">
        {error}
      </p>
    );
  }
  if (!report) return <output className="empty">Loading reports…</output>;

  const o = report.overview;
  const tiles = [
    { label: "Total tickets", value: o.total },
    { label: "Open", value: o.open },
    { label: "Critical and open", value: o.criticalOpen, tone: o.criticalOpen ? "text-red" : "" },
    { label: "Resolved or closed", value: o.resolved },
    { label: "Missed SLA", value: o.slaBreached, tone: o.slaBreached ? "text-red" : "" },
    { label: "At risk now", value: o.slaAtRisk, tone: o.slaAtRisk ? "text-amber" : "" },
    { label: "Escalated", value: o.escalated },
    {
      label: "Response on time",
      value: o.responseSlaMetPercent == null ? "—" : `${o.responseSlaMetPercent}%`,
    },
    {
      label: `Satisfaction${o.ratedCount ? ` (${o.ratedCount} rated)` : ""}`,
      value: o.averageRating == null ? "—" : `${o.averageRating} / 5`,
    },
  ];

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Reports</h1>
          <p>Service health across every ticket.</p>
        </div>
      </div>

      {report.correlatedServices.length > 0 && (
        <output className="callout callout-warn" style={{ marginBottom: 16 }}>
          <p className="callout-title">Possible common cause</p>
          <ul className="plain-list">
            {report.correlatedServices.map((s) => (
              <li key={s.service}>
                <strong>{s.service}</strong> has {s.open} open tickets reported in the last 24
                hours.
              </li>
            ))}
          </ul>
        </output>
      )}

      <dl className="stat-grid">
        {tiles.map((t) => (
          <div key={t.label} className="card stat-tile">
            <dt>{t.label}</dt>
            <dd className={t.tone || undefined}>{t.value}</dd>
          </div>
        ))}
      </dl>

      <div className="report-grid">
        <BarList title="By status" rows={entries(report.byStatus, STATUS_LABELS)} />
        <BarList title="By priority" rows={entries(report.byPriority, PRIORITY_LABELS)} />
        <BarList title="By category" rows={entries(report.byCategory, CATEGORY_LABELS)} />
        <BarList
          title="By service"
          rows={report.byService.map((s) => ({
            label: s.service,
            value: s.total,
            detail: `${s.open} open`,
          }))}
        />
      </div>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="card-header">
          <h2>Technician workload</h2>
        </div>
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Technician</th>
                <th scope="col">Open</th>
                <th scope="col">Overdue</th>
                <th scope="col">Resolved</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {report.technicianWorkload.map((t) => (
                <tr key={t.username}>
                  <td>
                    {t.fullName}
                    <span className="props-sub mono">{t.username}</span>
                  </td>
                  <td className="load">{t.active}</td>
                  <td className={`load ${t.breached ? "text-red" : ""}`}>{t.breached}</td>
                  <td className="load">{t.resolved}</td>
                  <td>{t.available ? "Active" : <span className="muted">Deactivated</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}
