"use client";

import type { Category, IncidentStatus, Priority, SlaStatus } from "@/lib/apiClient";

export interface FilterState {
  status: IncidentStatus | "";
  priority: Priority | "";
  category: Category | "";
  slaStatus: SlaStatus | "";
  searchText: string;
}

interface SearchFiltersProps {
  filters: FilterState;
  onChange: (updated: FilterState) => void;
  onReset: () => void;
}

export function SearchFilters({ filters, onChange, onReset }: SearchFiltersProps) {
  const update = (key: keyof FilterState, value: string) => {
    onChange({ ...filters, [key]: value });
  };

  return (
    <div
      style={{
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-color)",
        borderRadius: "6px",
        padding: "1rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.75rem",
        marginBottom: "1.5rem",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <span
          style={{
            fontSize: "0.8125rem",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            color: "var(--text-muted)",
          }}
        >
          Filters & Search (5 Criteria)
        </span>
        <button
          type="button"
          onClick={onReset}
          style={{
            background: "none",
            border: "none",
            color: "var(--accent)",
            fontSize: "0.8125rem",
            cursor: "pointer",
            fontWeight: 500,
          }}
        >
          Reset Filters
        </button>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
          gap: "0.75rem",
        }}
      >
        {/* 1. Free-text Search */}
        <div>
          <label
            htmlFor="filter-search"
            style={{
              display: "block",
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Search Text
          </label>
          <input
            id="filter-search"
            type="text"
            value={filters.searchText}
            onChange={(e) => update("searchText", e.target.value)}
            placeholder="Search title, description..."
            style={{
              width: "100%",
              padding: "0.45rem 0.65rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.8125rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          />
        </div>

        {/* 2. Status Filter */}
        <div>
          <label
            htmlFor="filter-status"
            style={{
              display: "block",
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Status
          </label>
          <select
            id="filter-status"
            value={filters.status}
            onChange={(e) => update("status", e.target.value)}
            style={{
              width: "100%",
              padding: "0.45rem 0.65rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.8125rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          >
            <option value="">All Statuses</option>
            <option value="REPORTED">Reported</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="PENDING_INFO">Pending Info</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>

        {/* 3. Priority Filter */}
        <div>
          <label
            htmlFor="filter-priority"
            style={{
              display: "block",
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Priority
          </label>
          <select
            id="filter-priority"
            value={filters.priority}
            onChange={(e) => update("priority", e.target.value)}
            style={{
              width: "100%",
              padding: "0.45rem 0.65rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.8125rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          >
            <option value="">All Priorities</option>
            <option value="P1_CRITICAL">P1 Critical</option>
            <option value="P2_HIGH">P2 High</option>
            <option value="P3_MEDIUM">P3 Medium</option>
            <option value="P4_LOW">P4 Low</option>
          </select>
        </div>

        {/* 4. Category Filter */}
        <div>
          <label
            htmlFor="filter-category"
            style={{
              display: "block",
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Category
          </label>
          <select
            id="filter-category"
            value={filters.category}
            onChange={(e) => update("category", e.target.value)}
            style={{
              width: "100%",
              padding: "0.45rem 0.65rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.8125rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          >
            <option value="">All Categories</option>
            <option value="NETWORK">Network</option>
            <option value="HARDWARE">Hardware</option>
            <option value="SOFTWARE">Software</option>
            <option value="ACCESS">Access</option>
          </select>
        </div>

        {/* 5. SLA Status Filter */}
        <div>
          <label
            htmlFor="filter-sla"
            style={{
              display: "block",
              fontSize: "0.75rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            SLA Status
          </label>
          <select
            id="filter-sla"
            value={filters.slaStatus}
            onChange={(e) => update("slaStatus", e.target.value)}
            style={{
              width: "100%",
              padding: "0.45rem 0.65rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.8125rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          >
            <option value="">All SLA Statuses</option>
            <option value="WITHIN_SLA">Within SLA</option>
            <option value="WARNING_BREACH_IMMINENT">Warning: Imminent Breach</option>
            <option value="BREACHED">Breached</option>
          </select>
        </div>
      </div>
    </div>
  );
}
