import type { Impact, Priority, Urgency } from "@/lib/apiClient";

export function calculateMatrixPriority(impact: Impact, urgency: Urgency): Priority {
  if (impact === "HIGH") {
    return urgency === "LOW" ? "P2_HIGH" : "P1_CRITICAL";
  }
  if (impact === "MEDIUM") {
    return urgency === "HIGH" ? "P2_HIGH" : "P3_MEDIUM";
  }
  // LOW impact
  return urgency === "HIGH" ? "P3_MEDIUM" : "P4_LOW";
}

export function PriorityMatrixPreview({
  impact,
  urgency,
}: {
  impact: Impact;
  urgency: Urgency;
}) {
  const impacts: Impact[] = ["HIGH", "MEDIUM", "LOW"];
  const urgencies: Urgency[] = ["HIGH", "MEDIUM", "LOW"];

  const currentPriority = calculateMatrixPriority(impact, urgency);

  return (
    <div
      style={{
        border: "1px solid var(--border-color)",
        borderRadius: "6px",
        padding: "1rem",
        backgroundColor: "var(--bg-surface)",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "0.75rem",
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
          Dynamic Priority Calculation (3×3 Matrix)
        </span>
        <span
          style={{
            fontSize: "0.875rem",
            fontWeight: 700,
            color:
              currentPriority === "P1_CRITICAL"
                ? "#b91c1c"
                : currentPriority === "P2_HIGH"
                  ? "#c2410c"
                  : currentPriority === "P3_MEDIUM"
                    ? "#1d4ed8"
                    : "#475569",
          }}
        >
          Calculated: {currentPriority}
        </span>
      </div>

      <table
        style={{
          width: "100%",
          borderCollapse: "collapse",
          fontSize: "0.75rem",
          textAlign: "center",
        }}
      >
        <thead>
          <tr>
            <th
              style={{
                padding: "0.4rem",
                border: "1px solid var(--border-color)",
                color: "var(--text-muted)",
                textAlign: "left",
              }}
            >
              Impact \ Urgency
            </th>
            {urgencies.map((u) => (
              <th
                key={u}
                style={{
                  padding: "0.4rem",
                  border: "1px solid var(--border-color)",
                  backgroundColor: u === urgency ? "#eff6ff" : "transparent",
                  color: u === urgency ? "#1d4ed8" : "var(--text-secondary)",
                  fontWeight: u === urgency ? 700 : 500,
                }}
              >
                {u}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {impacts.map((imp) => (
            <tr key={imp}>
              <td
                style={{
                  padding: "0.4rem",
                  border: "1px solid var(--border-color)",
                  textAlign: "left",
                  backgroundColor: imp === impact ? "#eff6ff" : "transparent",
                  color: imp === impact ? "#1d4ed8" : "var(--text-secondary)",
                  fontWeight: imp === impact ? 700 : 500,
                }}
              >
                {imp}
              </td>
              {urgencies.map((urg) => {
                const cellPrio = calculateMatrixPriority(imp, urg);
                const isSelected = imp === impact && urg === urgency;

                return (
                  <td
                    key={urg}
                    style={{
                      padding: "0.4rem",
                      border: "1px solid var(--border-color)",
                      fontWeight: isSelected ? 700 : 500,
                      backgroundColor: isSelected
                        ? cellPrio === "P1_CRITICAL"
                          ? "#fee2e2"
                          : cellPrio === "P2_HIGH"
                            ? "#ffedd5"
                            : cellPrio === "P3_MEDIUM"
                              ? "#dbeafe"
                              : "#e2e8f0"
                        : "transparent",
                      color: isSelected ? "#0f172a" : "var(--text-muted)",
                      outline: isSelected ? "2px solid #2563eb" : "none",
                    }}
                  >
                    {cellPrio.replace("_", " ")}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
