import { PriorityBadge } from "@/components/PriorityBadge";
import type { Criticality, Impact, Priority, Urgency } from "@/lib/apiClient";
import { CRITICALITY_LABELS } from "@/lib/labels";

const WEIGHT = { HIGH: 3, MEDIUM: 2, LOW: 1 } as const;
const ADJUSTMENT: Record<Criticality, number> = { CRITICAL: 2, HIGH: 1, MEDIUM: 0, LOW: -1 };

/**
 * Mirrors the server's WeightedPriorityStrategy so the reporter sees the same answer before
 * submitting: score = 2 x impact + urgency + service criticality adjustment.
 */
export function calculatePriority(
  impact: Impact,
  urgency: Urgency,
  criticality: Criticality = "MEDIUM"
): Priority {
  const score = 2 * WEIGHT[impact] + WEIGHT[urgency] + ADJUSTMENT[criticality];
  if (score >= 8) return "P1_CRITICAL";
  if (score === 7) return "P2_HIGH";
  if (score >= 5) return "P3_MEDIUM";
  return "P4_LOW";
}

const LEVELS = ["HIGH", "MEDIUM", "LOW"] as const;
const LEVEL_LABELS = { HIGH: "High", MEDIUM: "Med", LOW: "Low" } as const;

export function PriorityMatrixPreview({
  impact,
  urgency,
  criticality,
}: {
  impact: Impact;
  urgency: Urgency;
  criticality?: Criticality;
}) {
  const currentPriority = calculatePriority(impact, urgency, criticality);

  return (
    <div>
      <table className="matrix">
        <caption className="sr-only">Priority by impact (rows) and urgency (columns)</caption>
        <thead>
          <tr>
            <th scope="col" aria-label="Impact" />
            {LEVELS.map((u) => (
              <th key={u} scope="col" className={u === urgency ? "is-current" : undefined}>
                {LEVEL_LABELS[u]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {LEVELS.map((imp) => (
            <tr key={imp}>
              <th scope="row" className={imp === impact ? "is-current" : undefined}>
                {LEVEL_LABELS[imp]}
              </th>
              {LEVELS.map((urg) => (
                <td
                  key={urg}
                  className={imp === impact && urg === urgency ? "is-current" : undefined}
                >
                  {calculatePriority(imp, urg, criticality).slice(0, 2)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {criticality && criticality !== "MEDIUM" && (
        <p className="field-hint" style={{ marginTop: 8 }}>
          Adjusted for a {CRITICALITY_LABELS[criticality].toLowerCase()}-criticality service.
        </p>
      )}
      <div className="priority-result">
        <span className="muted">This ticket will be</span>
        <PriorityBadge priority={currentPriority} />
      </div>
    </div>
  );
}
