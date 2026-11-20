"use client";

import { type CommentItem, api } from "@/lib/apiClient";
import { timeAgo } from "@/lib/labels";
import { useEffect, useState } from "react";

interface CommentThreadProps {
  incidentId: string;
  initialComments: CommentItem[];
  currentUserRole: string;
}

export function CommentThread({
  incidentId,
  initialComments,
  currentUserRole,
}: CommentThreadProps) {
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

  return (
    <section className="card" aria-labelledby="comments-title">
      <div className="card-header">
        <h2 id="comments-title">
          Comments{comments.length > 0 && <span className="count">{comments.length}</span>}
        </h2>
      </div>

      {comments.length > 0 && (
        <div className="comments">
          {comments.map((c) => (
            <article key={c.id} className={`comment ${c.isInternal ? "comment-internal" : ""}`}>
              <div>
                <div className="comment-meta">
                  <span className="comment-author">{c.authorName}</span>
                  {c.isInternal && <span className="tag internal-tag">Internal</span>}
                  <time
                    className="comment-time"
                    dateTime={c.createdAt}
                    title={new Date(c.createdAt).toLocaleString()}
                  >
                    {timeAgo(c.createdAt)}
                  </time>
                </div>
                <p className="comment-body">{c.commentText}</p>
              </div>
            </article>
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
          placeholder={comments.length === 0 ? "Start the conversation…" : "Write a reply…"}
          aria-label="Comment"
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
            {submitting ? "Posting…" : "Comment"}
          </button>
        </div>
      </form>
    </section>
  );
}
