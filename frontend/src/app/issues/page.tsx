"use client";

import { Icon } from "@/components/Icon";
import { PriorityBadge } from "@/components/PriorityBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { UpdateEntry } from "@/components/UpdateEntry";
import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type IncidentStatus,
  type ItService,
  type PublicIssue,
  api,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, ROLE_LABELS, STATUS_LABELS, timeAgo } from "@/lib/labels";
import Link from "next/link";
import { useEffect, useState } from "react";

const PAGE_SIZE = 20;

interface Filters {
  searchText: string;
  status: IncidentStatus | "";
  category: Category | "";
  serviceId: string;
}

const EMPTY: Filters = { searchText: "", status: "", category: "", serviceId: "" };

/** Read-only list of every issue, so people can check before reporting and follow progress. */
export default function IssuesPage() {
  const { user, loading: authLoading } = useAuth();
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const [issues, setIssues] = useState<PublicIssue[] | null>(null);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [services, setServices] = useState<ItService[]>([]);
  const [openId, setOpenId] = useState<string | null>(null);
  const [details, setDetails] = useState<Record<string, PublicIssue | "error">>({});

  useEffect(() => {
    if (user)
      api
        .listServices(true)
        .then(setServices)
        .catch(() => {});
  }, [user]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    if (!user) return;
    let active = true;
    setError(null);
    const timer = window.setTimeout(
      () => {
        api
          .listIssues({
            searchText: filters.searchText.trim() || undefined,
            status: filters.status || undefined,
            category: filters.category || undefined,
            serviceId: filters.serviceId || undefined,
            page,
            size: PAGE_SIZE,
          })
          .then((res) => {
            if (!active) return;
            setIssues(res.content);
            setTotal(res.totalElements);
            setTotalPages(res.totalPages || 1);
          })
          .catch((err: Error) => active && setError(err.message));
      },
      filters.searchText ? 250 : 0
    );
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [user, filters, page, reloadKey]);

  const toggle = (id: string) => {
    const next = openId === id ? null : id;
    setOpenId(next);
    if (next && !details[next]) {
      api
        .getIssue(next)
        .then((issue) => setDetails((d) => ({ ...d, [next]: issue })))
        .catch(() => setDetails((d) => ({ ...d, [next]: "error" })));
    }
  };

  const set = (patch: Partial<Filters>) => {
    setFilters((f) => ({ ...f, ...patch }));
    setPage(0);
  };

  if (authLoading || !user) return <output className="empty">Loading…</output>;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>All issues{issues && <span className="count">{total}</span>}</h1>
          <p>Check whether a problem is already known and follow its progress. Read-only.</p>
        </div>
        <Link className="btn btn-primary" href="/incidents/new">
          <Icon name="plus" size={15} />
          Report an issue
        </Link>
      </div>

      <section className="card" aria-label="All issues">
        <div className="toolbar">
          <label className="search">
            <Icon name="search" size={15} />
            <input
              type="search"
              placeholder="Search issues"
              aria-label="Search issues"
              value={filters.searchText}
              onChange={(e) => set({ searchText: e.target.value })}
            />
          </label>
          <select
            aria-label="Status"
            className={`filter-select ${filters.status ? "is-set" : ""}`}
            value={filters.status}
            onChange={(e) => set({ status: e.target.value as IncidentStatus | "" })}
          >
            <option value="">Status: any</option>
            {Object.entries(STATUS_LABELS).map(([v, l]) => (
              <option key={v} value={v}>
                Status: {l}
              </option>
            ))}
          </select>
          <select
            aria-label="Category"
            className={`filter-select ${filters.category ? "is-set" : ""}`}
            value={filters.category}
            onChange={(e) => set({ category: e.target.value as Category | "" })}
          >
            <option value="">Category: any</option>
            {Object.entries(CATEGORY_LABELS).map(([v, l]) => (
              <option key={v} value={v}>
                Category: {l}
              </option>
            ))}
          </select>
          {services.length > 0 && (
            <select
              aria-label="Service"
              className={`filter-select ${filters.serviceId ? "is-set" : ""}`}
              value={filters.serviceId}
              onChange={(e) => set({ serviceId: e.target.value })}
            >
              <option value="">Service: any</option>
              {services.map((s) => (
                <option key={s.id} value={s.id}>
                  Service: {s.name}
                </option>
              ))}
            </select>
          )}
        </div>

        {error ? (
          <div className="empty" role="alert">
            <h2>Couldn’t load issues</h2>
            <button type="button" className="btn" onClick={() => setReloadKey((k) => k + 1)}>
              Try again
            </button>
          </div>
        ) : !issues ? (
          <output className="empty">Loading…</output>
        ) : issues.length === 0 ? (
          <div className="empty">
            <h2>No issues match</h2>
            <p>Nothing reported matches these filters.</p>
          </div>
        ) : (
          <ul className="issue-list">
            {issues.map((issue) => {
              const open = openId === issue.id;
              const detail = details[issue.id];
              return (
                <li key={issue.id} className={open ? "is-open" : undefined}>
                  <button
                    type="button"
                    className="issue-row"
                    aria-expanded={open}
                    aria-controls={`issue-${issue.id}`}
                    onClick={() => toggle(issue.id)}
                  >
                    <span className="mono muted">{issue.incidentNumber}</span>
                    <span className="issue-title">{issue.title}</span>
                    <StatusBadge status={issue.status} />
                    <span className="hide-sm">
                      <PriorityBadge priority={issue.priority} compact />
                    </span>
                    <span className="hide-sm muted">{issue.serviceName ?? ""}</span>
                    <span className="hide-sm muted issue-meta">
                      {issue.updateCount} update{issue.updateCount === 1 ? "" : "s"} ·{" "}
                      {timeAgo(issue.updatedAt)}
                    </span>
                  </button>
                  {open && (
                    <div className="issue-detail" id={`issue-${issue.id}`}>
                      {!detail ? (
                        <output className="muted">Loading…</output>
                      ) : detail === "error" ? (
                        <p className="muted" role="alert">
                          Couldn’t load this issue.
                        </p>
                      ) : (
                        <>
                          <p className="description">{detail.description}</p>
                          <div className="issue-detail-meta">
                            <p className="field-hint">
                              {CATEGORY_LABELS[detail.category]}
                              {detail.serviceName && ` · ${detail.serviceName}`}
                              {detail.handledBy && ` · Handled by ${detail.handledBy}`}
                            </p>
                            {!["RESOLVED", "CLOSED", "CANCELLED"].includes(detail.status) && (
                              <Link
                                className="btn btn-sm"
                                href={`/incidents/new?${new URLSearchParams({
                                  related: detail.incidentNumber,
                                  title: detail.title,
                                  category: detail.category,
                                })}`}
                              >
                                This affects me too
                              </Link>
                            )}
                          </div>
                          {detail.updates.length === 0 ? (
                            <p className="muted">No updates yet.</p>
                          ) : (
                            <div className="comments">
                              {detail.updates.map((u) => (
                                <UpdateEntry
                                  key={u.id}
                                  author={u.author}
                                  role={
                                    u.authorRole && u.authorRole !== "EMPLOYEE"
                                      ? ROLE_LABELS[u.authorRole]
                                      : null
                                  }
                                  kind={u.kind}
                                  text={u.text}
                                  createdAt={u.createdAt}
                                />
                              ))}
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        {issues && totalPages > 1 && (
          <div className="table-footer">
            <span>
              Page {page + 1} of {totalPages}
            </span>
            <div className="row">
              <button
                type="button"
                className="btn btn-sm"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn btn-sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  );
}
