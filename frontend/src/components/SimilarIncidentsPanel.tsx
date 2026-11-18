"use client";

import { type SimilarIncident, api } from "@/lib/apiClient";
import { useEffect, useState } from "react";

interface SimilarIncidentsPanelProps {
  incidentId: string;
}

export function SimilarIncidentsPanel({ incidentId }: SimilarIncidentsPanelProps) {
  const [similar, setSimilar] = useState<SimilarIncident[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;
    api
      .getSimilarIncidents(incidentId)
      .then((res) => {
        if (isMounted) setSimilar(res);
      })
      .catch(() => {})
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [incidentId]);

  if (loading) {
    return (
      <div
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.25rem",
          marginBottom: "1.5rem",
          fontSize: "0.875rem",
          color: "var(--text-muted)",
        }}
      >
        Calculating Jaccard similarity across past resolved incidents...
      </div>
    );
  }

  if (similar.length === 0) {
    return null;
  }

  return (
    <div
      style={{
        backgroundColor: "#f0fdf4",
        border: "1px solid #bbf7d0",
        borderRadius: "6px",
        padding: "1.25rem",
        marginBottom: "1.5rem",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "0.75rem",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
          <span style={{ fontSize: "1.1rem" }}>💡</span>
          <h3
            style={{
              fontSize: "0.9375rem",
              fontWeight: 600,
              color: "#166534",
            }}
          >
            Intelligent Duplicate & Similar Incident Detection
          </h3>
        </div>
        <span
          style={{
            fontSize: "0.75rem",
            color: "#15803d",
            fontWeight: 600,
          }}
        >
          Jaccard Similarity $\ge 0.30$
        </span>
      </div>

      <p
        style={{
          fontSize: "0.8125rem",
          color: "#166534",
          marginBottom: "1rem",
        }}
      >
        The following past resolved tickets share vocabulary and root cause characteristics with this incident:
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
        {similar.map((item) => {
          const matchPercent = Math.round(item.similarityScore * 100);
          const isHighMatch = item.similarityScore >= 0.6;

          return (
            <div
              key={item.incidentNumber}
              style={{
                backgroundColor: "white",
                border: `1px solid ${isHighMatch ? "#86efac" : "#dcfce7"}`,
                borderRadius: "4px",
                padding: "0.875rem",
                boxShadow: isHighMatch
                  ? "0 1px 3px rgba(34, 197, 94, 0.2)"
                  : "none",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "baseline",
                  marginBottom: "0.35rem",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                  <span
                    style={{
                      fontFamily: "var(--font-mono)",
                      fontWeight: 700,
                      fontSize: "0.8125rem",
                      color: "#166534",
                    }}
                  >
                    {item.incidentNumber}
                  </span>
                  <span
                    style={{
                      fontSize: "0.875rem",
                      fontWeight: 600,
                      color: "var(--text-primary)",
                    }}
                  >
                    {item.title}
                  </span>
                </div>
                <span
                  style={{
                    fontSize: "0.75rem",
                    fontWeight: 700,
                    padding: "0.15rem 0.45rem",
                    borderRadius: "9999px",
                    backgroundColor: isHighMatch ? "#bbf7d0" : "#f0fdf4",
                    color: isHighMatch ? "#14532d" : "#166534",
                  }}
                >
                  {matchPercent}% Similarity
                </span>
              </div>

              {item.resolutionSummary && (
                <div
                  style={{
                    fontSize: "0.8125rem",
                    backgroundColor: "#f8fafc",
                    padding: "0.5rem 0.75rem",
                    borderRadius: "4px",
                    border: "1px solid var(--border-color)",
                    color: "var(--text-secondary)",
                    marginTop: "0.5rem",
                  }}
                >
                  <strong style={{ color: "var(--text-primary)" }}>
                    Past Resolution:{" "}
                  </strong>
                  {item.resolutionSummary}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
