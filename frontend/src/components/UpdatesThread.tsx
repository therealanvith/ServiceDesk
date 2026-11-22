"use client";

import { UpdateEntry } from "@/components/UpdateEntry";
import { type CommentItem, type IncidentStatus, api } from "@/lib/apiClient";
import { ROLE_LABELS } from "@/lib/labels";
import { useEffect, useState } from "react";

interface UpdatesThreadProps {
  incidentId: string;
  initialComments: CommentItem[];
  currentUserRole: string;
  status?: IncidentStatus;
  /** Shown to the reporter while a question is waiting on them. */
  onReply?: () => void;
}

export function UpdatesThread({
  incidentId,
  initialComments,
  currentUserRole,
  status,
  onReply,
}: UpdatesThreadProps) {
  const [comments, setComments] = useState<CommentItem[]>(initialComments);
  const [commentText, setCommentText] = useState("");
  const [isInternal, setIsInternal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setComments(initialComments);
  }, [initialComments]);

  const canPostInternal = currentUserRole !== "EMPLOYEE";

  const handlePost = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentText.trim()) return;

    setSubmitting(true);
    setError(null);
    try {
      const newComment = await api.addComment(
        incidentId,
        commentText.trim(),
        canPostInternal && isInternal
      );
      setComments([...comments, newComment]);
      setCommentText("");
      setIsInternal(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to post comment");
    } finally {
      setSubmitting(false);
    }
  };

  // While the ticket waits on the reporter, the open question is pinned on top.
  const openQuestion =
    status === "PENDING_INFO" ? [...comments].reverse().find((c) => c.kind === "QUESTION") : null;

  return (
    <section className="card" aria-labelledby="updates-title">
      <div className="card-header">
        <h2 id="updates-title">
          Updates{comments.length > 0 && <span className="count">{comments.length}</span>}
        </h2>
      </div>

      {openQuestion && (
        <div className="pinned-question">
          <p className="callout-title">
            {onReply ? `${openQuestion.authorName} asked you` : "Waiting for the reporter’s reply"}
          </p>
          <p className="comment-body">{openQuestion.commentText}</p>
          {onReply && (
            <button type="button" className="btn btn-primary btn-sm" onClick={onReply}>
              Reply
            </button>
          )}
        </div>
      )}

      {comments.length > 0 && (
        <div className="comments">
          {comments.map((c) => (
            <UpdateEntry
              key={c.id}
              author={c.authorName}
              role={c.authorRole && c.authorRole !== "EMPLOYEE" ? ROLE_LABELS[c.authorRole] : null}
              kind={c.kind}
              text={c.commentText}
              createdAt={c.createdAt}
              internal={c.isInternal}
            />
          ))}
        </div>
      )}

      <form className="composer" onSubmit={handlePost}>
        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}
        <textarea
          className="input"
          rows={3}
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          placeholder={comments.length === 0 ? "Start the conversation…" : "Write an update…"}
          aria-label="Write an update"
          // Locked while posting so nothing typed meanwhile is lost when the box clears.
          readOnly={submitting}
          aria-busy={submitting || undefined}
        />
        <div className="composer-footer">
          {canPostInternal ? (
            <label className="checkbox">
              <input
                type="checkbox"
                checked={isInternal}
                onChange={(e) => setIsInternal(e.target.checked)}
              />
              Internal note — only staff can see it
            </label>
          ) : (
            <span />
          )}
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submitting || !commentText.trim()}
          >
            {submitting ? "Posting…" : "Post update"}
          </button>
        </div>
      </form>
    </section>
  );
}
