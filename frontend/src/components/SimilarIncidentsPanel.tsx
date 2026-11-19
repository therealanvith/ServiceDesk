"use client";

import { type SimilarIncident, api } from "@/lib/apiClient";
import Link from "next/link";
import { useEffect, useState } from "react";

interface SimilarIncidentsPanelProps {
  incidentId: string;
}

export function SimilarIncidentsPanel({ incidentId }: SimilarIncidentsPanelProps) {
  const [similar, setSimilar] = useState<SimilarIncident[]>([]);

  useEffect(() => {
    let isMounted = true;
    api
      .getSimilarIncidents(incidentId)
      .then((res) => {
        if (isMounted) setSimilar(res);
      })
      .catch(() => {});

    return () => {
      isMounted = false;
    };
  }, [incidentId]);

  // Only worth the space when there is something to show.
  if (similar.length === 0) {
    return null;
  }

  return (
    <section className="card" aria-labelledby="similar-title">
      <div className="card-header">
        <h2 id="similar-title">Related tickets</h2>
        <span className="hint">Possible duplicates and known fixes</span>
      </div>
      <ul className="similar-list">
        {similar.map((item) => {
          const matchPercent = Math.round(item.similarityScore * 100);
          return (
            <li key={item.incidentNumber} className="similar-item">
              <div className="similar-head">
                <Link href={`/incidents/${item.id}`} className="mono link">
                  {item.incidentNumber}
                </Link>
                <span className="title">{item.title}</span>
                <span className={`tag ${item.open ? "tag-warn" : ""}`}>
                  {item.open ? "Possible duplicate" : "Has a fix"}
                </span>
                <span className={`match ${item.similarityScore >= 0.6 ? "match-high" : ""}`}>
                  {matchPercent}% match
                </span>
              </div>
              {item.resolutionSummary && (
                <p className="similar-fix">
                  <span className="muted">Fix: </span>
                  {item.resolutionSummary}
                </p>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
