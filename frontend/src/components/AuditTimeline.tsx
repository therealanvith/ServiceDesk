import type { AuditLogItem } from "@/lib/apiClient";

interface AuditTimelineProps {
  logs: AuditLogItem[];
}

export function AuditTimeline({ logs }: AuditTimelineProps) {
  return (
    <div
      style={{
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-color)",
        borderRadius: "6px",
        padding: "1.25rem",
        marginBottom: "1.5rem",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "1rem",
        }}
      >
        <h3
          style={{
            fontSize: "0.9375rem",
            fontWeight: 600,
            color: "var(--text-primary)",
          }}
        >
          Immutable Audit Ledger ({logs.length} Entries)
        </h3>
        <span
          style={{
            fontSize: "0.75rem",
            color: "var(--text-muted)",
          }}
        >
          Append-Only Compliance History
        </span>
      </div>

      {logs.length === 0 ? (
        <p style={{ fontSize: "0.875rem", color: "var(--text-muted)" }}>
          No audit logs recorded for this ticket.
        </p>
      ) : (
        <div
          style={{
            position: "relative",
            paddingLeft: "1.25rem",
            borderLeft: "2px solid var(--border-color)",
            display: "flex",
            flexDirection: "column",
            gap: "1.25rem",
            marginLeft: "0.5rem",
          }}
        >
          {logs.map((log) => (
            <div key={log.id} style={{ position: "relative" }}>
              {/* Dot */}
              <div
                style={{
                  position: "absolute",
                  left: "-1.625rem",
                  top: "0.25rem",
                  width: "10px",
                  height: "10px",
                  borderRadius: "50%",
                  backgroundColor:
                    log.action === "CREATED"
                      ? "#2563eb"
                      : log.action === "SLA_BREACHED"
                        ? "#dc2626"
                        : log.action === "STATUS_CHANGED"
                          ? "#059669"
                          : "#64748b",
                  border: "2px solid white",
                  boxShadow: "0 0 0 1px var(--border-color)",
                }}
              />

              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "baseline",
                  marginBottom: "0.25rem",
                  flexWrap: "wrap",
                  gap: "0.5rem",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                  <span
                    style={{
                      fontSize: "0.8125rem",
                      fontWeight: 700,
                      color: "var(--text-primary)",
                    }}
                  >
                    {log.action}
                  </span>
                  <span
                    style={{
                      fontSize: "0.75rem",
                      color: "var(--text-secondary)",
                    }}
                  >
                    by <strong>{log.actorName}</strong>
                  </span>
                </div>
                <span
                  style={{
                    fontSize: "0.75rem",
                    color: "var(--text-muted)",
                    fontFamily: "var(--font-mono)",
                  }}
                >
                  {new Date(log.timestamp).toLocaleString()}
                </span>
              </div>

              {(log.oldValue || log.newValue) && (
                <div
                  style={{
                    fontSize: "0.8125rem",
                    color: "var(--text-secondary)",
                    marginBottom: "0.2rem",
                  }}
                >
                  {log.oldValue && (
                    <span style={{ textDecoration: "line-through", color: "#94a3b8" }}>
                      {log.oldValue}
                    </span>
                  )}
                  {log.oldValue && log.newValue && <span> &rarr; </span>}
                  {log.newValue && (
                    <strong style={{ color: "var(--text-primary)" }}>{log.newValue}</strong>
                  )}
                </div>
              )}

              {log.remarks && (
                <div
                  style={{
                    fontSize: "0.8125rem",
                    color: "var(--text-muted)",
                    fontStyle: "italic",
                  }}
                >
                  &ldquo;{log.remarks}&rdquo;
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
