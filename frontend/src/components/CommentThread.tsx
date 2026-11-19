"use client";

import { type CommentItem, api } from "@/lib/apiClient";
import { useState } from "react";

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

  const canPostInternal = currentUserRole === "TECHNICIAN" || currentUserRole === "ADMINISTRATOR";

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
    <div
      style={{
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-color)",
        borderRadius: "6px",
        padding: "1.25rem",
        marginBottom: "1.5rem",
      }}
    >
      <h3
        style={{
          fontSize: "0.9375rem",
          fontWeight: 600,
          color: "var(--text-primary)",
          marginBottom: "1rem",
        }}
      >
        Diagnostic Discussion Thread ({comments.length})
      </h3>

      {comments.length === 0 ? (
        <p
          style={{
            fontSize: "0.875rem",
            color: "var(--text-muted)",
            marginBottom: "1.25rem",
          }}
        >
          No diagnostic notes or discussion comments yet.
        </p>
      ) : (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "0.75rem",
            marginBottom: "1.25rem",
          }}
        >
          {comments.map((c) => (
            <div
              key={c.id}
              style={{
                padding: "0.75rem",
                borderRadius: "4px",
                border: "1px solid",
                borderColor: c.isInternal ? "#fed7aa" : "var(--border-color)",
                backgroundColor: c.isInternal ? "#fffaf5" : "var(--bg-primary)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: "0.35rem",
                  fontSize: "0.75rem",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                  <span style={{ fontWeight: 600, color: "var(--text-primary)" }}>
                    {c.authorName}
                  </span>
                  {c.isInternal && (
                    <span
                      style={{
                        padding: "0.1rem 0.4rem",
                        borderRadius: "3px",
                        fontSize: "0.6875rem",
                        fontWeight: 700,
                        backgroundColor: "#ffedd5",
                        color: "#c2410c",
                      }}
                    >
                      INTERNAL NOTE
                    </span>
                  )}
                </div>
                <span style={{ color: "var(--text-muted)" }}>
                  {new Date(c.createdAt).toLocaleString()}
                </span>
              </div>
              <p
                style={{
                  fontSize: "0.875rem",
                  color: "var(--text-secondary)",
                  whiteSpace: "pre-wrap",
                }}
              >
                {c.commentText}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Post comment form */}
      <form onSubmit={handlePost}>
        {error && (
          <div
            style={{
              backgroundColor: "#fef2f2",
              color: "#b91c1c",
              padding: "0.4rem 0.6rem",
              borderRadius: "4px",
              fontSize: "0.8125rem",
              marginBottom: "0.75rem",
            }}
          >
            {error}
          </div>
        )}

        <textarea
          rows={3}
          value={commentText}
          onChange={(e) => setCommentText(e.target.value)}
          placeholder="Type diagnostic update or question..."
          style={{
            width: "100%",
            padding: "0.6rem",
            borderRadius: "4px",
            border: "1px solid var(--border-color)",
            fontSize: "0.875rem",
            backgroundColor: "var(--bg-primary)",
            color: "var(--text-primary)",
            marginBottom: "0.5rem",
            fontFamily: "inherit",
          }}
        />

        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          {canPostInternal ? (
            <label
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.4rem",
                fontSize: "0.8125rem",
                color: "var(--text-secondary)",
                cursor: "pointer",
              }}
            >
              <input
                type="checkbox"
                checked={isInternal}
                onChange={(e) => setIsInternal(e.target.checked)}
              />
              Internal note (hidden from employees)
            </label>
          ) : (
            <div />
          )}

          <button
            type="submit"
            disabled={submitting || !commentText.trim()}
            style={{
              padding: "0.45rem 0.9rem",
              borderRadius: "4px",
              backgroundColor: "var(--accent)",
              color: "white",
              fontSize: "0.8125rem",
              fontWeight: 600,
              border: "none",
              cursor: "pointer",
              opacity: submitting || !commentText.trim() ? 0.6 : 1,
            }}
          >
            {submitting ? "Posting..." : "Post Comment"}
          </button>
        </div>
      </form>
    </div>
  );
}
