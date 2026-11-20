import type {
  Category,
  Criticality,
  EscalationLevel,
  IncidentStatus,
  Priority,
  Role,
  SlaStatus,
} from "@/lib/apiClient";

export const STATUS_LABELS: Record<IncidentStatus, string> = {
  REPORTED: "Reported",
  ASSIGNED: "Assigned",
  ACKNOWLEDGED: "Acknowledged",
  IN_PROGRESS: "In progress",
  PENDING_INFO: "Waiting on reporter",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
  CANCELLED: "Cancelled",
};

export const ESCALATION_LABELS: Record<EscalationLevel, string> = {
  NONE: "Not escalated",
  TEAM_LEAD: "Escalated to the team lead",
  ADMINISTRATOR: "Escalated to administrators",
  IT_MANAGER: "Escalated to the IT manager",
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  P1_CRITICAL: "Critical",
  P2_HIGH: "High",
  P3_MEDIUM: "Medium",
  P4_LOW: "Low",
};

export const CATEGORY_LABELS: Record<Category, string> = {
  NETWORK: "Network",
  HARDWARE: "Hardware",
  SOFTWARE: "Software",
  ACCESS: "Authentication & access",
  DATABASE: "Database",
  CLOUD: "Cloud",
  SECURITY: "Security",
  APPLICATION: "Application",
  STORAGE: "Storage",
};

export const CRITICALITY_LABELS: Record<Criticality, string> = {
  CRITICAL: "Critical",
  HIGH: "High",
  MEDIUM: "Medium",
  LOW: "Low",
};

export const SLA_LABELS: Record<SlaStatus, string> = {
  WITHIN_SLA: "On track",
  WARNING_BREACH_IMMINENT: "At risk",
  BREACHED: "Overdue",
};

export const ROLE_LABELS: Record<Role, string> = {
  EMPLOYEE: "Employee",
  TECHNICIAN: "Technician",
  ADMINISTRATOR: "Administrator",
  ROOT: "Root",
};

/** Administrators and root see every ticket and the settings pages. */
export function isAdmin(role: Role | undefined): boolean {
  return role === "ADMINISTRATOR" || role === "ROOT";
}

/** Short duration for SLA targets, e.g. 90 -> "1 h 30 min". */
export function formatMinutes(total: number): string {
  if (total < 60) return `${total} min`;
  const days = Math.floor(total / 1440);
  const hours = Math.floor((total % 1440) / 60);
  const minutes = total % 60;
  return [days && `${days} d`, hours && `${hours} h`, minutes && `${minutes} min`]
    .filter(Boolean)
    .join(" ");
}

const AUDIT_LABELS: Record<string, string> = {
  CREATED: "created the ticket",
  STATUS_CHANGED: "changed status",
  ASSIGNED: "assigned the ticket",
  PRIORITY_OVERRIDDEN: "changed priority",
  SLA_BREACHED: "SLA target missed",
  COMMENTED: "commented",
  ESCALATED: "escalated: no response in time",
};

export function auditLabel(action: string): string {
  return AUDIT_LABELS[action] ?? action.toLowerCase().replace(/_/g, " ");
}

/** Turns enum-like values ("IN_PROGRESS", "P1_CRITICAL") into readable text where possible. */
export function humanize(value: string): string {
  return (
    STATUS_LABELS[value as IncidentStatus] ??
    PRIORITY_LABELS[value as Priority] ??
    CATEGORY_LABELS[value as Category] ??
    SLA_LABELS[value as SlaStatus] ??
    value
  );
}

const relative = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });

export function timeAgo(iso: string): string {
  const seconds = (new Date(iso).getTime() - Date.now()) / 1000;
  const abs = Math.abs(seconds);
  if (abs < 60) return "just now";
  if (abs < 3600) return relative.format(Math.round(seconds / 60), "minute");
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), "hour");
  if (abs < 86400 * 7) return relative.format(Math.round(seconds / 86400), "day");
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: new Date(iso).getFullYear() === new Date().getFullYear() ? undefined : "numeric",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
