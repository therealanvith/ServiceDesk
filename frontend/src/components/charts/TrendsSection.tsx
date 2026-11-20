"use client";

import { type TrendRange, type Trends, api } from "@/lib/apiClient";
import { CATEGORY_LABELS, formatMinutes } from "@/lib/labels";
import { useEffect, useState } from "react";
import { BarChart } from "./BarChart";
import { ChartCard } from "./ChartCard";
import { LineChart } from "./LineChart";
import { shortDate } from "./chartUtils";

const RANGES: TrendRange[] = [7, 30, 90];

const ESCALATION_SHORT: Record<Trends["escalationsByLevel"][number]["level"], string> = {
  TEAM_LEAD: "Escalated to team lead",
  ADMINISTRATOR: "Escalated to administrators",
  IT_MANAGER: "Escalated to IT manager",
};

const count = (v: number) => String(Math.round(v));
const percent = (v: number) => `${v}%`;
const hours = (v: number) => (v < 1 ? `${Math.round(v * 60)} min` : `${v} h`);

/** Created vs resolved, resolve time and SLA compliance over a chosen range. */
export function TrendsSection() {
  const [days, setDays] = useState<TrendRange>(30);
  const [trends, setTrends] = useState<Trends | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError(null);
    api
      .getTrends(days)
      .then((data) => current && setTrends(data))
      .catch((err: Error) => current && setError(err.message))
      .finally(() => current && setLoading(false));
    return () => {
      current = false;
    };
  }, [days, reloadKey]);

  const t = trends;
  const answered = t ? t.selfHelp.helped + t.selfHelp.notHelped : 0;
  const tiles = t
    ? [
        {
          label: "First response, average",
          value:
            t.averageFirstResponseMinutes == null
              ? "—"
              : t.averageFirstResponseMinutes < 1
                ? "Under 1 min"
                : formatMinutes(Math.round(t.averageFirstResponseMinutes)),
          detail: `${t.respondedCount} ticket${t.respondedCount === 1 ? "" : "s"} responded to`,
        },
        ...t.escalationsByLevel.map((e) => ({
          label: ESCALATION_SHORT[e.level],
          value: String(e.count),
          detail: e.count === 1 ? "1 escalation" : `${e.count} escalations`,
        })),
        {
          label: "Solved by self-help",
          value: answered ? `${Math.round((100 * t.selfHelp.helped) / answered)}%` : "—",
          detail: answered
            ? `${t.selfHelp.helped} of ${answered} said the steps fixed it`
            : "No answers yet",
        },
      ]
    : [];

  const noResolves = !t || t.resolutionByCategory.length === 0;

  return (
    <section className="trends" aria-labelledby="trends-title">
      <div className="trends-header">
        <h2 id="trends-title">Trends</h2>
        {t && (
          <p className="muted">
            {shortDate(t.from)} to {shortDate(t.to)}, UTC days
          </p>
        )}
      </div>

      <fieldset className="range-control">
        <legend className="sr-only">Time range</legend>
        {RANGES.map((r) => (
          <button
            key={r}
            type="button"
            className="btn btn-sm"
            aria-pressed={days === r}
            onClick={() => setDays(r)}
          >
            Last {r} days
          </button>
        ))}
      </fieldset>

      {error && (
        <div className="callout callout-warn" role="alert">
          <p className="callout-title">Couldn’t load trends</p>
          <p>{error}</p>
          <button type="button" className="btn btn-sm" onClick={() => setReloadKey((k) => k + 1)}>
            Try again
          </button>
        </div>
      )}

      {!t && !error && <output className="empty">Loading trends…</output>}

      {t && (
        <div className={`trends-body${loading ? " is-refetching" : ""}`} aria-busy={loading}>
          <dl className="stat-grid">
            {tiles.map((tile) => (
              <div key={tile.label} className="card stat-tile">
                <dt>{tile.label}</dt>
                <dd>{tile.value}</dd>
                <dd className="stat-detail">{tile.detail}</dd>
              </div>
            ))}
          </dl>

          <div className="chart-grid">
            <ChartCard
              wide
              title="Created vs resolved"
              hint="Tickets per day"
              rows={t.daily}
              rowKey={(d) => d.date}
              columns={[
                { header: "Day", cell: (d) => shortDate(d.date) },
                { header: "Created", cell: (d) => d.created, numeric: true },
                { header: "Resolved", cell: (d) => d.resolved, numeric: true },
              ]}
            >
              <LineChart
                ariaLabel="Tickets created and resolved per day"
                labels={t.daily.map((d) => shortDate(d.date))}
                minStep={1}
                formatValue={count}
                series={[
                  {
                    key: "created",
                    label: "Created",
                    color: "var(--chart-1)",
                    values: t.daily.map((d) => d.created),
                  },
                  {
                    key: "resolved",
                    label: "Resolved",
                    color: "var(--chart-2)",
                    values: t.daily.map((d) => d.resolved),
                  },
                ]}
              />
            </ChartCard>

            <ChartCard
              title="Mean time to resolve"
              hint="By category, report to resolve"
              rows={t.resolutionByCategory}
              rowKey={(c) => c.category}
              empty={noResolves ? "No tickets resolved in this range." : undefined}
              columns={[
                { header: "Category", cell: (c) => CATEGORY_LABELS[c.category] ?? c.category },
                { header: "Mean", cell: (c) => hours(c.meanHours), numeric: true },
                { header: "Resolved", cell: (c) => c.resolvedCount, numeric: true },
              ]}
            >
              <BarChart
                ariaLabel="Mean time to resolve by category"
                color="var(--chart-1)"
                formatValue={hours}
                rows={t.resolutionByCategory.map((c) => ({
                  key: c.category,
                  label: CATEGORY_LABELS[c.category] ?? c.category,
                  value: c.meanHours,
                  detail: `${c.resolvedCount} resolved`,
                }))}
              />
            </ChartCard>

            <ChartCard
              title="Resolved within SLA"
              hint="Share of each week’s resolved tickets"
              rows={t.weeklyCompliance}
              rowKey={(w) => w.weekStart}
              empty={noResolves ? "No tickets resolved in this range." : undefined}
              columns={[
                { header: "Week of", cell: (w) => shortDate(w.weekStart) },
                { header: "Resolved", cell: (w) => w.resolvedCount, numeric: true },
                { header: "Within SLA", cell: (w) => w.withinSlaCount, numeric: true },
                {
                  header: "Compliance",
                  cell: (w) => (w.compliancePercent == null ? "—" : percent(w.compliancePercent)),
                  numeric: true,
                },
              ]}
            >
              <LineChart
                ariaLabel="Weekly share of resolved tickets that met their resolution target"
                labels={t.weeklyCompliance.map((w) => shortDate(w.weekStart))}
                tooltipLabels={t.weeklyCompliance.map((w) => `Week of ${shortDate(w.weekStart)}`)}
                yMax={100}
                markers
                formatValue={percent}
                series={[
                  {
                    key: "compliance",
                    label: "Within SLA",
                    color: "var(--chart-1)",
                    values: t.weeklyCompliance.map((w) => w.compliancePercent),
                  },
                ]}
              />
            </ChartCard>
          </div>
        </div>
      )}
    </section>
  );
}
