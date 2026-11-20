"use client";

import { Icon } from "@/components/Icon";
import type {
  Category,
  IncidentStatus,
  ItService,
  Priority,
  SlaStatus,
  TechnicianItem,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, PRIORITY_LABELS, SLA_LABELS, STATUS_LABELS } from "@/lib/labels";
import type { ReactNode } from "react";

export interface FilterState {
  status: IncidentStatus | "";
  priority: Priority | "";
  category: Category | "";
  slaStatus: SlaStatus | "";
  serviceId: string;
  assigneeId: string;
  createdFrom: string;
  createdTo: string;
  searchText: string;
}

export const EMPTY_FILTERS: FilterState = {
  status: "",
  priority: "",
  category: "",
  slaStatus: "",
  serviceId: "",
  assigneeId: "",
  createdFrom: "",
  createdTo: "",
  searchText: "",
};

const choices = [
  { key: "status", label: "Status", options: STATUS_LABELS },
  { key: "priority", label: "Priority", options: PRIORITY_LABELS },
  { key: "category", label: "Category", options: CATEGORY_LABELS },
  { key: "slaStatus", label: "SLA", options: SLA_LABELS },
] as const;

export function SearchFilters({
  filters,
  onChange,
  services,
  technicians,
  children,
}: {
  filters: FilterState;
  onChange: (updated: FilterState) => void;
  services: ItService[];
  /** Only passed for administrators, who can filter by technician. */
  technicians?: TechnicianItem[];
  children?: ReactNode;
}) {
  const set = (key: keyof FilterState, value: string) => onChange({ ...filters, [key]: value });

  return (
    <div className="toolbar">
      <label className="search">
        <Icon name="search" size={15} />
        <input
          type="search"
          value={filters.searchText}
          onChange={(e) => set("searchText", e.target.value)}
          placeholder="Search by title or ticket number"
          aria-label="Search tickets"
          data-shortcut="search"
        />
      </label>
      {choices.map(({ key, label, options }) => (
        <select
          key={key}
          aria-label={label}
          className={`filter-select ${filters[key] ? "is-set" : ""}`}
          value={filters[key]}
          onChange={(e) => set(key, e.target.value)}
        >
          <option value="">{label}: any</option>
          {Object.entries(options).map(([value, name]) => (
            <option key={value} value={value}>
              {label}: {name}
            </option>
          ))}
        </select>
      ))}
      {services.length > 0 && (
        <select
          aria-label="Service"
          className={`filter-select ${filters.serviceId ? "is-set" : ""}`}
          value={filters.serviceId}
          onChange={(e) => set("serviceId", e.target.value)}
        >
          <option value="">Service: any</option>
          {services.map((s) => (
            <option key={s.id} value={s.id}>
              Service: {s.name}
            </option>
          ))}
        </select>
      )}
      {technicians && (
        <select
          aria-label="Technician"
          className={`filter-select ${filters.assigneeId ? "is-set" : ""}`}
          value={filters.assigneeId}
          onChange={(e) => set("assigneeId", e.target.value)}
        >
          <option value="">Technician: any</option>
          {technicians.map((t) => (
            <option key={t.id} value={t.id}>
              Technician: {t.fullName}
            </option>
          ))}
        </select>
      )}
      <span className="date-range">
        <input
          type="date"
          aria-label="Created from"
          className={`filter-select ${filters.createdFrom ? "is-set" : ""}`}
          value={filters.createdFrom}
          max={filters.createdTo || undefined}
          onChange={(e) => set("createdFrom", e.target.value)}
        />
        <span className="muted">to</span>
        <input
          type="date"
          aria-label="Created to"
          className={`filter-select ${filters.createdTo ? "is-set" : ""}`}
          value={filters.createdTo}
          min={filters.createdFrom || undefined}
          onChange={(e) => set("createdTo", e.target.value)}
        />
      </span>
      {children}
    </div>
  );
}
