"use client";

import { PriorityBadge } from "@/components/PriorityBadge";
import { type FilterState, SearchFilters } from "@/components/SearchFilters";
import { SlaBadge } from "@/components/SlaBadge";
import { StatusBadge } from "@/components/StatusBadge";
import { useAuth } from "@/context/AuthContext";
import {
  type HealthResponse,
  type IncidentSummary,
  api,
  checkBackendHealth,
} from "@/lib/apiClient";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

const initialFilters: FilterState = {
  status: "",
  priority: "",
  category: "",
  slaStatus: "",
  searchText: "",
};

export default function IncidentsDashboardPage() {
  const { user } = useAuth();

  const [incidents, setIncidents] = useState<IncidentSummary[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<FilterState>(initialFilters);
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<HealthResponse | null>(null);

  // Load backend health
  useEffect(() => {
    checkBackendHealth()
      .then(setHealth)
      .catch(() => {});
  }, []);

  const loadIncidents = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.listIncidents({
        status: filters.status || undefined,
        priority: filters.priority || undefined,
        category: filters.category || undefined,
        slaStatus: filters.slaStatus || undefined,
        searchText: filters.searchText.trim() || undefined,
        page,
        size: 15,
      });
      setIncidents(res.content);
      setTotalPages(res.totalPages || 1);
      setTotalElements(res.totalElements);
    } catch {
      setIncidents([]);
      setTotalPages(1);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    loadIncidents();
  }, [loadIncidents]);

  const handleFilterChange = (updated: FilterState) => {
    setFilters(updated);
    setPage(0);
  };

  const handleResetFilters = () => {
    setFilters(initialFilters);
    setPage(0);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
      {/* Top Banner: Service Health & User Persona Status */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: "1rem",
          paddingBottom: "1rem",
          borderBottom: "1px solid var(--border-color)",
        }}
      >
        <div>
          <h1
            style={{
              fontSize: "1.5rem",
              fontWeight: 700,
              letterSpacing: "-0.03em",
              color: "var(--text-primary)",
              marginBottom: "0.25rem",
            }}
          >
            Incident Management & Triage
          </h1>
          <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
            {user ? (
              <>
                Logged in as <strong>{user.fullName}</strong> ({user.role}) &bull;{" "}
                {user.role === "EMPLOYEE"
                  ? "Displaying your reported tickets"
                  : "Displaying system-wide incident queues"}
              </>
            ) : (
              "Viewing public incident directory. Sign in to submit tickets and perform actions."
            )}
          </p>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "0.4rem",
              padding: "0.25rem 0.6rem",
              borderRadius: "9999px",
              fontSize: "0.75rem",
              fontWeight: 600,
              fontFamily: "var(--font-mono)",
              backgroundColor:
                health?.status === "UP" ? "var(--status-up-bg)" : "var(--status-down-bg)",
              color: health?.status === "UP" ? "var(--status-up-text)" : "var(--status-down-text)",
              border: `1px solid ${
                health?.status === "UP" ? "var(--status-up-border)" : "var(--status-down-border)"
              }`,
            }}
          >
            <span>●</span> Backend: {health?.status ?? "CHECKING"}
          </span>

          <Link
            href="/incidents/new"
            style={{
              backgroundColor: "var(--accent)",
              color: "white",
              padding: "0.5rem 1rem",
              borderRadius: "4px",
              fontSize: "0.875rem",
              fontWeight: 600,
              display: "inline-flex",
              alignItems: "center",
              gap: "0.4rem",
            }}
          >
            + Report Incident
          </Link>
        </div>
      </div>

      {/* 5+ Filter Bar */}
      <SearchFilters filters={filters} onChange={handleFilterChange} onReset={handleResetFilters} />

      {/* Incidents Table */}
      <div
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          overflow: "hidden",
        }}
      >
        <div
          style={{
            padding: "0.875rem 1.25rem",
            borderBottom: "1px solid var(--border-color)",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            backgroundColor: "var(--bg-primary)",
          }}
        >
          <span
            style={{
              fontSize: "0.8125rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
            }}
          >
            Total Results: <strong>{totalElements}</strong>
          </span>
          <span
            style={{
              fontSize: "0.8125rem",
              color: "var(--text-muted)",
            }}
          >
            Page {page + 1} of {Math.max(1, totalPages)}
          </span>
        </div>

        {loading ? (
          <div
            style={{
              padding: "3rem",
              textAlign: "center",
              color: "var(--text-muted)",
              fontSize: "0.875rem",
            }}
          >
            Loading incidents...
          </div>
        ) : incidents.length === 0 ? (
          <div
            style={{
              padding: "3rem",
              textAlign: "center",
              color: "var(--text-muted)",
              fontSize: "0.875rem",
            }}
          >
            No incidents match the active search criteria.
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table
              style={{
                width: "100%",
                borderCollapse: "collapse",
                fontSize: "0.875rem",
                textAlign: "left",
              }}
            >
              <thead>
                <tr
                  style={{
                    borderBottom: "1px solid var(--border-color)",
                    backgroundColor: "var(--bg-primary)",
                    color: "var(--text-muted)",
                    fontSize: "0.75rem",
                    fontWeight: 600,
                    textTransform: "uppercase",
                    letterSpacing: "0.05em",
                  }}
                >
                  <th style={{ padding: "0.75rem 1rem" }}>Ticket</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Title</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Category</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Priority</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Status</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Assignee</th>
                  <th style={{ padding: "0.75rem 1rem" }}>SLA</th>
                  <th style={{ padding: "0.75rem 1rem" }}>Created</th>
                </tr>
              </thead>
              <tbody>
                {incidents.map((inc) => (
                  <tr
                    key={inc.id}
                    style={{
                      borderBottom: "1px solid var(--border-color)",
                      cursor: "pointer",
                      transition: "background-color 0.1s",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.backgroundColor = "var(--bg-primary)";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.backgroundColor = "transparent";
                    }}
                  >
                    <td
                      style={{
                        padding: "0.75rem 1rem",
                        fontFamily: "var(--font-mono)",
                        fontWeight: 600,
                        fontSize: "0.8125rem",
                        color: "var(--accent)",
                      }}
                    >
                      <Link href={`/incidents/${inc.id}`}>{inc.incidentNumber}</Link>
                    </td>
                    <td
                      style={{
                        padding: "0.75rem 1rem",
                        fontWeight: 600,
                        color: "var(--text-primary)",
                        maxWidth: "280px",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      <Link href={`/incidents/${inc.id}`}>{inc.title}</Link>
                    </td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <span
                        style={{
                          fontSize: "0.75rem",
                          fontWeight: 500,
                          padding: "0.15rem 0.45rem",
                          borderRadius: "4px",
                          backgroundColor: "#f1f5f9",
                          color: "#475569",
                        }}
                      >
                        {inc.category}
                      </span>
                    </td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <PriorityBadge priority={inc.priority} />
                    </td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <StatusBadge status={inc.status} />
                    </td>
                    <td
                      style={{
                        padding: "0.75rem 1rem",
                        fontSize: "0.8125rem",
                        color:
                          inc.assigneeName === "Unassigned"
                            ? "var(--text-muted)"
                            : "var(--text-primary)",
                        fontStyle: inc.assigneeName === "Unassigned" ? "italic" : "normal",
                      }}
                    >
                      {inc.assigneeName}
                    </td>
                    <td style={{ padding: "0.75rem 1rem" }}>
                      <SlaBadge status={inc.slaStatus} />
                    </td>
                    <td
                      style={{
                        padding: "0.75rem 1rem",
                        fontSize: "0.75rem",
                        color: "var(--text-muted)",
                        fontFamily: "var(--font-mono)",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {new Date(inc.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination bar */}
        {totalPages > 1 && (
          <div
            style={{
              padding: "0.75rem 1.25rem",
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              borderTop: "1px solid var(--border-color)",
            }}
          >
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
              style={{
                padding: "0.4rem 0.8rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                backgroundColor: "var(--bg-primary)",
                fontSize: "0.8125rem",
                cursor: page === 0 ? "not-allowed" : "pointer",
                opacity: page === 0 ? 0.5 : 1,
              }}
            >
              &larr; Previous
            </button>
            <span style={{ fontSize: "0.8125rem", color: "var(--text-muted)" }}>
              Page {page + 1} of {totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage(page + 1)}
              style={{
                padding: "0.4rem 0.8rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                backgroundColor: "var(--bg-primary)",
                fontSize: "0.8125rem",
                cursor: page + 1 >= totalPages ? "not-allowed" : "pointer",
                opacity: page + 1 >= totalPages ? 0.5 : 1,
              }}
            >
              Next &rarr;
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
