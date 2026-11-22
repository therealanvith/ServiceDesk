"use client";

import { Icon } from "@/components/Icon";
import { showToast } from "@/components/LiveUpdates";
import { PriorityBadge } from "@/components/PriorityBadge";
import { EMPTY_FILTERS, type FilterState, SearchFilters } from "@/components/SearchFilters";
import { SkeletonBlock } from "@/components/Skeleton";
import { SlaBadge } from "@/components/SlaBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { useAuth } from "@/context/AuthContext";
import {
  type HealthResponse,
  type IncidentSummary,
  type ItService,
  type TechnicianItem,
  type Workload,
  api,
  checkBackendHealth,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, isAdmin, timeAgo } from "@/lib/labels";
import { type Move, allowedMoves, performMove } from "@/lib/lifecycle";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

const PAGE_SIZE = 15;

export default function IncidentsDashboardPage() {
  const { user, loading: authLoading } = useAuth();

  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [services, setServices] = useState<ItService[]>([]);
  const [technicians, setTechnicians] = useState<TechnicianItem[] | undefined>();
  const [workload, setWorkload] = useState<Workload | null>(null);
  const [lookupFailed, setLookupFailed] = useState(false);
  const silentReload = useRef(false);
  const [lookupKey, setLookupKey] = useState(0);
  const [rowBusy, setRowBusy] = useState<string | null>(null);
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);

  const role = user?.role;
  const admin = isAdmin(role);

  useEffect(() => {
    checkBackendHealth()
      .then(setHealth)
      .catch(() => {});
  }, []);

  // Lookup data for the filters, and a technician's own workload summary.
  // biome-ignore lint/correctness/useExhaustiveDependencies: lookupKey re-runs the load on refresh
  useEffect(() => {
    if (!role) return;
    setLookupFailed(false);
    api
      .listServices(true)
      .then(setServices)
      .catch(() => setLookupFailed(true));
    if (role === "ADMINISTRATOR" || role === "ROOT") {
      api
        .listTechnicians()
        .then(setTechnicians)
        .catch(() => setLookupFailed(true));
    }
    if (role === "TECHNICIAN") {
      api
        .getMyWorkload()
        .then(setWorkload)
        .catch(() => setLookupFailed(true));
    }
  }, [role, lookupKey]);

  useEffect(() => {
    let active = true;
    // A live refresh replaces rows in place: no skeleton, so focus and row messages survive.
    const silent = silentReload.current;
    silentReload.current = false;
    if (!silent) setError(null);
    if (authLoading || !user) {
      setLoading(authLoading);
      return;
    }
    if (!silent) setLoading(true);
    const loadIncidents = async () => {
      try {
        const res = await api.listIncidents({
          status: filters.status || undefined,
          priority: filters.priority || undefined,
          category: filters.category || undefined,
          slaStatus: filters.slaStatus || undefined,
          serviceId: filters.serviceId || undefined,
          assigneeId: filters.assigneeId || undefined,
          createdFrom: filters.createdFrom || undefined,
          createdTo: filters.createdTo || undefined,
          searchText: filters.searchText.trim() || undefined,
          page,
          size: PAGE_SIZE,
        });
        if (!active) return;
        // The list can shrink (filters, closures) while you're on a later page: jump back.
        if (res.content.length === 0 && page > 0 && res.totalElements > 0) {
          setPage(Math.max(0, (res.totalPages || 1) - 1));
          return;
        }
        setIncidents(res.content);
        setTotalPages(res.totalPages || 1);
        setTotalElements(res.totalElements);
      } catch (err: unknown) {
        if (!active || silent) return; // keep the rows on screen; the next event retries
        setError(err instanceof Error ? err.message : "Unable to load incidents");
        setIncidents([]);
        setTotalPages(1);
        setTotalElements(0);
      } finally {
        if (active) setLoading(false);
      }
    };
    const pending = window.setTimeout(loadIncidents, filters.searchText ? 220 : 0);
    return () => {
      active = false;
      window.clearTimeout(pending);
    };
  }, [filters, page, user, authLoading]);

  // A ticket changed (ours or someone else's): refresh quietly with the same filters.
  useLiveEvent((e) => {
    if (e.event !== "incident-updated") return;
    silentReload.current = true;
    setFilters((current) => ({ ...current }));
  });

  const handleFilterChange = (updated: FilterState) => {
    setFilters(updated);
    setPage(0);
  };

  const handleResetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setPage(0);
  };

  const reloadQueue = () => {
    setFilters((current) => ({ ...current }));
    void checkBackendHealth().then(setHealth);
    setLookupKey((k) => k + 1);
  };

  // The one-click step for the person working a ticket: acknowledge it, or start work on it.
  // Technicians also see tickets they reported, so only their assigned ones get the button.
  const quickMove = (ticket: IncidentSummary): Move | undefined => {
    if (ticket.status !== "ASSIGNED" && ticket.status !== "ACKNOWLEDGED") return undefined;
    const isAssignee = ticket.assigneeId === user?.id;
    return allowedMoves(ticket.status, role, isAssignee).find((move) => !move.text);
  };

  const runQuickMove = async (ticket: IncidentSummary, move: Move) => {
    setRowBusy(ticket.id);
    setRowError(null);
    try {
      const updated = await performMove(ticket.id, move.action);
      // Refresh just this row from the confirmed ticket.
      setIncidents((rows) =>
        rows.map((row) =>
          row.id === updated.id
            ? {
                ...row,
                status: updated.status,
                priority: updated.priority,
                slaStatus: updated.slaWindow?.slaStatus ?? row.slaStatus,
                assigneeName: updated.assignee?.fullName ?? row.assigneeName,
                assigneeId: updated.assignee?.id ?? row.assigneeId,
              }
            : row
        )
      );
      showToast({
        title: move.action === "acknowledge" ? "Ticket acknowledged" : "Work started",
        message: `${ticket.incidentNumber} · ${ticket.title}`,
        tone: "success",
      });
    } catch (err: unknown) {
      setRowError({
        id: ticket.id,
        message: err instanceof Error ? err.message : "Action failed",
      });
    } finally {
      setRowBusy(null);
    }
  };

  if (authLoading || !user) {
    return <SkeletonBlock label="Loading…" />;
  }

  const filtered = Object.values(filters).some(Boolean);
  const seesAll = admin || Boolean(user?.teamLead);
  const title = seesAll ? "All incidents" : role === "TECHNICIAN" ? "My queue" : "My tickets";
  const subtitle = seesAll
    ? "Every ticket across the organisation."
    : role === "TECHNICIAN"
      ? "Tickets assigned to you."
      : "Problems you have reported and where they stand.";

  return (
    <>
      {health?.status === "DOWN" && (
        <p className="alert" style={{ marginBottom: 16 }}>
          The service is not responding. Data shown may be out of date.
        </p>
      )}
      <div className="page-header">
        <div>
          <h1>
            {title}
            {!loading && !error && <span className="count">{totalElements}</span>}
          </h1>
          <p>{subtitle}</p>
        </div>
        <Link className="btn btn-primary" href="/incidents/new">
          <Icon name="plus" size={15} />
          Report an issue
        </Link>
      </div>

      {lookupFailed && (
        <p className="alert" role="alert" style={{ marginBottom: 16 }}>
          Some filters or your workload summary couldn’t load.{" "}
          <button type="button" className="link-btn" onClick={() => setLookupKey((k) => k + 1)}>
            Try again
          </button>
        </p>
      )}
      {workload && (
        <dl className="stat-strip" aria-label="Your workload">
          <div>
            <dt>Open</dt>
            <dd>{workload.active}</dd>
          </div>
          <div>
            <dt>In progress</dt>
            <dd>{workload.inProgress}</dd>
          </div>
          <div>
            <dt>Waiting on reporter</dt>
            <dd>{workload.waitingOnReporter}</dd>
          </div>
          <div>
            <dt>At risk</dt>
            <dd className={workload.atRisk ? "text-amber" : undefined}>{workload.atRisk}</dd>
          </div>
          <div>
            <dt>Overdue</dt>
            <dd className={workload.breached ? "text-red" : undefined}>{workload.breached}</dd>
          </div>
          <div>
            <dt>Resolved this week</dt>
            <dd>{workload.resolvedThisWeek}</dd>
          </div>
        </dl>
      )}

      <section className="card" aria-label="Ticket list">
        <SearchFilters
          filters={filters}
          onChange={handleFilterChange}
          services={services}
          technicians={admin ? technicians : undefined}
        >
          {filtered && (
            <button type="button" className="btn btn-ghost btn-sm" onClick={handleResetFilters}>
              Clear
            </button>
          )}
          <button
            type="button"
            className="icon-btn"
            style={{ marginLeft: "auto" }}
            onClick={reloadQueue}
            disabled={loading}
            aria-label="Refresh"
            title="Refresh"
          >
            <Icon name="refresh" size={15} />
          </button>
        </SearchFilters>

        {loading ? (
          <SkeletonBlock label="Loading tickets…" rows={8} />
        ) : error ? (
          <div className="empty" role="alert">
            <h2>Couldn’t load tickets</h2>
            <p>{error}</p>
            <button type="button" className="btn" onClick={reloadQueue}>
              Try again
            </button>
          </div>
        ) : incidents.length === 0 ? (
          <div className="empty">
            <h2>{filtered ? "No tickets match these filters" : "No tickets yet"}</h2>
            <p>
              {filtered
                ? "Try a different search, or clear the filters."
                : role === "TECHNICIAN"
                  ? "Nothing is assigned to you right now."
                  : role === "EMPLOYEE"
                    ? "When you report a problem it will show up here."
                    : "New tickets will appear here as they come in."}
            </p>
            {filtered ? (
              <button type="button" className="btn" onClick={handleResetFilters}>
                Clear filters
              </button>
            ) : (
              <Link className="btn" href="/incidents/new">
                Report an issue
              </Link>
            )}
          </div>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">ID</th>
                    <th scope="col">Title</th>
                    <th scope="col">Status</th>
                    <th scope="col">Priority</th>
                    <th scope="col" className="hide-sm">
                      Service
                    </th>
                    <th scope="col" className="hide-sm">
                      Category
                    </th>
                    {role !== "TECHNICIAN" && (
                      <th scope="col" className="hide-sm">
                        Assignee
                      </th>
                    )}
                    {role !== "EMPLOYEE" && (
                      <th scope="col" className="hide-sm">
                        Reporter
                      </th>
                    )}
                    <th scope="col" className="hide-sm">
                      SLA
                    </th>
                    <th scope="col" className="hide-sm">
                      Created
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {incidents.map((ticket) => {
                    const move = quickMove(ticket);
                    return (
                      <tr key={ticket.id}>
                        <td className="col-id">{ticket.incidentNumber}</td>
                        <td className="col-title row-link-cell">
                          <Link
                            className="title-link"
                            href={
                              ticket.joined
                                ? `/issues?${new URLSearchParams({ q: ticket.incidentNumber, open: ticket.id })}`
                                : `/incidents/${ticket.id}`
                            }
                          >
                            {ticket.title}
                          </Link>
                          {ticket.joined && <span className="muted"> · You’re following this</span>}
                        </td>
                        <td className="col-nowrap">
                          <StatusBadge status={ticket.status} />
                          {move && (
                            <button
                              type="button"
                              className="btn btn-sm row-action"
                              disabled={rowBusy === ticket.id}
                              aria-label={`${move.action === "acknowledge" ? "Acknowledge" : "Start"} ${ticket.incidentNumber}`}
                              onClick={() => void runQuickMove(ticket, move)}
                            >
                              {move.action === "acknowledge" ? "Acknowledge" : "Start"}
                            </button>
                          )}
                          {rowError?.id === ticket.id && (
                            <span className="row-action-error" role="alert">
                              {rowError.message}
                            </span>
                          )}
                        </td>
                        <td className="col-nowrap">
                          <PriorityBadge priority={ticket.priority} />
                        </td>
                        <td className="hide-sm">
                          {ticket.serviceName ?? <span className="unassigned">—</span>}
                        </td>
                        <td className="hide-sm">{CATEGORY_LABELS[ticket.category]}</td>
                        {role !== "TECHNICIAN" && (
                          <td className="hide-sm col-nowrap">
                            {ticket.assigneeName === "Unassigned" ? (
                              <span className="unassigned">Unassigned</span>
                            ) : (
                              ticket.assigneeName
                            )}
                          </td>
                        )}
                        {role !== "EMPLOYEE" && (
                          <td className="hide-sm col-nowrap">{ticket.reporterName}</td>
                        )}
                        <td className="hide-sm">
                          <SlaBadge
                            status={ticket.status === "CANCELLED" ? null : ticket.slaStatus}
                            isPaused={ticket.status === "PENDING_INFO"}
                            finished={ticket.status === "RESOLVED" || ticket.status === "CLOSED"}
                          />
                        </td>
                        <td className="hide-sm col-nowrap muted">
                          <time
                            dateTime={ticket.createdAt}
                            title={new Date(ticket.createdAt).toLocaleString()}
                          >
                            {timeAgo(ticket.createdAt)}
                          </time>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="table-footer">
              <span>
                {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + incidents.length} of {totalElements}
              </span>
              {totalPages > 1 && (
                <div className="row">
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => setPage(page - 1)}
                    disabled={page === 0}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => setPage(page + 1)}
                    disabled={page + 1 >= totalPages}
                  >
                    Next
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </section>
    </>
  );
}
