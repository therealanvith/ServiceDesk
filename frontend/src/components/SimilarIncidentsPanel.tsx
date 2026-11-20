"use client";

import { type SimilarIncident, api } from "@/lib/apiClient";
import Link from "next/link";
import { useEffect, useState } from "react";

interface SimilarIncidentsPanelProps {
  incidentId: string;
}

export function SimilarIncidentsPanel({ incidentId }: SimilarIncidentsPanelProps) {
  const [similar, setSimilar] = useState<SimilarIncident[]>([]);
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    let isMounted = true;
    setFailed(false);
    api
      .getSimilarIncidents(incidentId)
      .then((res) => {
        if (isMounted) setSimilar(res);
      })
      .catch(() => {
        if (isMounted) setFailed(true);
      });

    return () => {
      isMounted = false;
    };
  }, [incidentId, reloadKey]);

  if (failed) {
    return (
      <section className="card" role="alert">
        <div className="card-header">
          <h2>Related tickets</h2>
        </div>
        <div className="card-body row">
          <span className="muted">Couldn’t look for related tickets.</span>
          <button type="button" className="btn btn-sm" onClick={() => setReloadKey((k) => k + 1)}>
            Try again
          </button>
        </div>
      </section>
    );
  }

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
