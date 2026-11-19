import type { Priority } from "@/lib/apiClient";
import { PRIORITY_LABELS } from "@/lib/labels";

export function PriorityBadge({ priority, compact }: { priority: Priority; compact?: boolean }) {
  const level = priority.slice(0, 2);
  return (
    <span className={`priority priority-${level.toLowerCase()}`}>
      <span className="priority-tag">{level}</span>
      {!compact && (PRIORITY_LABELS[priority] ?? priority)}
    </span>
  );
}
