import type { UpdateKind } from "@/lib/apiClient";
import { timeAgo } from "@/lib/labels";

const KIND_LABELS: Partial<Record<UpdateKind, string>> = {
  QUESTION: "Asked for details",
  ANSWER: "Replied",
  PICKUP: "Picked up",
  RESOLUTION: "Resolution",
  STATUS: "Status",
};

/** One entry in a ticket's Updates thread; shared by the ticket page and "All issues". */
export function UpdateEntry({
  author,
  role,
  kind,
  text,
  createdAt,
  internal,
}: {
  author: string;
  role?: string | null;
  kind: UpdateKind;
  text: string;
  createdAt: string;
  internal?: boolean;
}) {
  const label = KIND_LABELS[kind];
  return (
    <article
      className={`comment update-${kind.toLowerCase()} ${internal ? "comment-internal" : ""}`}
    >
      <div className="comment-meta">
        <span className="comment-author">{author}</span>
        {role && <span className="muted">{role}</span>}
        {label && (
          <span className={`tag update-tag update-tag-${kind.toLowerCase()}`}>{label}</span>
        )}
        {internal && <span className="tag internal-tag">Internal</span>}
        <time
          className="comment-time"
          dateTime={createdAt}
          title={new Date(createdAt).toLocaleString()}
        >
          {timeAgo(createdAt)}
        </time>
      </div>
      <p className="comment-body">{text}</p>
    </article>
  );
}
