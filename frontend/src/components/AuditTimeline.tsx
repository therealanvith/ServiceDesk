import type { AuditLogItem } from "@/lib/apiClient";
import { auditLabel, humanize, timeAgo } from "@/lib/labels";

interface AuditTimelineProps {
  logs: AuditLogItem[];
}

export function AuditTimeline({ logs }: AuditTimelineProps) {
  return (
    <section className="card" aria-labelledby="history-title">
      <div className="card-header">
        <h2 id="history-title">History</h2>
      </div>

      {logs.length === 0 ? (
        <p className="card-body muted">No activity recorded yet.</p>
      ) : (
        <ol className="timeline">
          {logs.map((log) => (
            <li key={log.id} className={log.action === "SLA_BREACHED" ? "is-breach" : undefined}>
              <span className="timeline-actor">{log.actorName ?? "System"}</span>{" "}
              {auditLabel(log.action)}
              {(log.oldValue || log.newValue) && (
                <>
                  {" "}
                  {log.oldValue && <span className="timeline-old">{humanize(log.oldValue)}</span>}
                  {log.oldValue && log.newValue && " → "}
                  {log.newValue && (
                    <span className="timeline-change">{humanize(log.newValue)}</span>
                  )}
                </>
              )}
              <time
                className="timeline-time"
                dateTime={log.timestamp}
                title={new Date(log.timestamp).toLocaleString()}
              >
                {timeAgo(log.timestamp)}
              </time>
              {log.remarks && <p className="timeline-remark">{log.remarks}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
