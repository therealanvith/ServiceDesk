import type { Priority } from "@/lib/apiClient";

export function PriorityBadge({ priority }: { priority: Priority }) {
  const config = {
    P1_CRITICAL: {
      label: "P1 Critical",
      bg: "#fef2f2",
      text: "#b91c1c",
      border: "#fecaca",
    },
    P2_HIGH: {
      label: "P2 High",
      bg: "#fff7ed",
      text: "#c2410c",
      border: "#fed7aa",
    },
    P3_MEDIUM: {
      label: "P3 Medium",
      bg: "#eff6ff",
      text: "#1d4ed8",
      border: "#bfdbfe",
    },
    P4_LOW: {
      label: "P4 Low",
      bg: "#f8fafc",
      text: "#475569",
      border: "#e2e8f0",
    },
  }[priority] ?? {
    label: priority,
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
