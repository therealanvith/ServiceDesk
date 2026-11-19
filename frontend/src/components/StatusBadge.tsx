import type { IncidentStatus } from "@/lib/apiClient";
import { STATUS_LABELS } from "@/lib/labels";

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span className={`status status-${status.toLowerCase()}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}
