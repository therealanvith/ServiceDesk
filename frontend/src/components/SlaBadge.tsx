import type { SlaStatus } from "@/lib/apiClient";

export function SlaBadge({
  status,
  isPaused,
}: {
  status: SlaStatus | null;
  isPaused?: boolean;
}) {
  if (isPaused) {
    return (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "0.25rem",
          padding: "0.2rem 0.55rem",
          borderRadius: "4px",
          fontSize: "0.75rem",
          fontWeight: 600,
          backgroundColor: "#f5f3ff",
          color: "#6d28d9",
          border: "1px solid #ddd6fe",
        }}
      >
        <span>⏸</span> SLA Paused
      </span>
    );
  }

  if (!status) {
    return null;
  }

  const config = {
    WITHIN_SLA: {
      label: "Within SLA",
      bg: "#ecfdf5",
      text: "#047857",
      border: "#a7f3d0",
    },
    WARNING_BREACH_IMMINENT: {
      label: "Warning: SLA Imminent",
      bg: "#fffbeb",
      text: "#b45309",
      border: "#fde68a",
    },
    BREACHED: {
      label: "SLA Breached",
      bg: "#fef2f2",
      text: "#b91c1c",
      border: "#fecaca",
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
