import type { SlaStatus } from "@/lib/apiClient";
import { SLA_LABELS } from "@/lib/labels";

export function SlaBadge({
  status,
  isPaused,
}: {
  status: SlaStatus | null;
  isPaused?: boolean;
}) {
  if (isPaused) {
    return <span className="sla sla-paused">Paused</span>;
  }
  if (!status) {
    return <span className="sla">—</span>;
  }
  return <span className={`sla sla-${status.toLowerCase()}`}>{SLA_LABELS[status] ?? status}</span>;
}
