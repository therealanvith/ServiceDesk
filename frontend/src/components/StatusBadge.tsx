import type { IncidentStatus } from "@/lib/apiClient";

export function StatusBadge({ status }: { status: IncidentStatus }) {
  const config = {
    REPORTED: {
      label: "Reported",
      bg: "#eef2ff",
      text: "#4338ca",
      border: "#c7d2fe",
    },
    ASSIGNED: {
      label: "Assigned",
      bg: "#ecfeff",
      text: "#0e7490",
      border: "#a5f3fc",
    },
    IN_PROGRESS: {
      label: "In Progress",
      bg: "#fffbeb",
      text: "#b45309",
      border: "#fde68a",
    },
    PENDING_INFO: {
      label: "Pending Info",
      bg: "#faf5ff",
      text: "#7e22ce",
      border: "#e9d5ff",
    },
    RESOLVED: {
      label: "Resolved",
      bg: "#ecfdf5",
      text: "#047857",
      border: "#a7f3d0",
    },
    CLOSED: {
      label: "Closed",
      bg: "#f1f5f9",
      text: "#334155",
      border: "#cbd5e1",
    },
    CANCELLED: {
      label: "Cancelled",
      bg: "#fff1f2",
      text: "#be123c",
      border: "#fecdd3",
    },
  }[status] ?? {
    label: status,
    bg: "#f8fafc",
    text: "#475569",
    border: "#e2e8f0",
  };

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "0.2rem 0.55rem",
        borderRadius: "4px",
        fontSize: "0.75rem",
        fontWeight: 600,
        backgroundColor: config.bg,
        color: config.text,
        border: `1px solid ${config.border}`,
      }}
    >
      {config.label}
    </span>
  );
}
