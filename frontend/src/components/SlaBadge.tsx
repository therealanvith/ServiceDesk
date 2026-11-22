import type { SlaStatus } from "@/lib/apiClient";
import { SLA_LABELS } from "@/lib/labels";

export function SlaBadge({
  status,
  isPaused,
  finished,
}: {
  status: SlaStatus | null;
  isPaused?: boolean;
  /** Resolved or closed: nothing is due any more, so say whether the target was met. */
  finished?: boolean;
}) {
  if (finished) {
    if (!status) return <span className="sla">—</span>;
    return status === "BREACHED" ? (
      <span className="sla sla-missed">Missed target</span>
    ) : (
      <span className="sla sla-met">Met target</span>
    );
  }
  if (isPaused) {
    return <span className="sla sla-paused">Paused</span>;
  }
  if (!status) {
    return <span className="sla">—</span>;
  }
  return <span className={`sla sla-${status.toLowerCase()}`}>{SLA_LABELS[status] ?? status}</span>;
}
