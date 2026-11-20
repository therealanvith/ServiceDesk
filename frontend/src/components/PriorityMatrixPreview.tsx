import { PriorityBadge } from "@/components/PriorityBadge";
import type { Impact, PriorityPreview, Urgency } from "@/lib/apiClient";
import { CRITICALITY_LABELS } from "@/lib/labels";

const LEVELS = ["HIGH", "MEDIUM", "LOW"] as const;
const LEVEL_LABELS = { HIGH: "High", MEDIUM: "Med", LOW: "Low" } as const;

/**
 * Presentation only: the grid and the resulting priority come from the server
 * (`GET /catalog/priority-preview`), so the priority policy lives in one place.
 */
export function PriorityMatrixPreview({
  impact,
  urgency,
  preview,
  failed,
  onRetry,
}: {
  impact: Impact;
  urgency: Urgency;
  preview: PriorityPreview | null;
  failed?: boolean;
  onRetry?: () => void;
}) {
  if (failed) {
    return (
      <div className="row" role="alert">
        <span className="muted">Couldn’t load the priority preview.</span>
        {onRetry && (
          <button type="button" className="btn btn-sm" onClick={onRetry}>
            Try again
          </button>
        )}
      </div>
    );
  }
  if (!preview) {
    return <output className="muted">Working out priority…</output>;
  }

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
                  {preview.matrix[imp][urg].slice(0, 2)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {preview.criticality !== "MEDIUM" && (
        <p className="field-hint" style={{ marginTop: 8 }}>
          Adjusted for a {CRITICALITY_LABELS[preview.criticality].toLowerCase()}-criticality
          service.
        </p>
      )}
      <div className="priority-result">
        <span className="muted">This ticket will be</span>
        <PriorityBadge priority={preview.priority} />
      </div>
    </div>
  );
}
