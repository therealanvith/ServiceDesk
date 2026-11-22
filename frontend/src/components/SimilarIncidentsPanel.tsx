"use client";

import { showToast } from "@/components/LiveUpdates";
import { type SimilarIncident, api } from "@/lib/apiClient";
import Link from "next/link";
import { useEffect, useState } from "react";

interface SimilarIncidentsPanelProps {
  incidentId: string;
  /** Administrators can link this ticket under an open related one (a major incident). */
  canLink?: boolean;
  /** Called after a successful link, so the page shows the new parent. */
  onLinked?: () => void;
}

export function SimilarIncidentsPanel({
  incidentId,
  canLink = false,
  onLinked,
}: SimilarIncidentsPanelProps) {
  const [similar, setSimilar] = useState<SimilarIncident[]>([]);
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [linkingId, setLinkingId] = useState<string | null>(null);
  const [linkError, setLinkError] = useState<{ id: string; message: string } | null>(null);

  const linkTo = async (item: SimilarIncident) => {
    setLinkingId(item.id);
    setLinkError(null);
    try {
      await api.linkIncident(incidentId, item.id);
      showToast({ title: `Linked to ${item.incidentNumber}`, tone: "success" });
      onLinked?.();
    } catch (err: unknown) {
      // The server explains refusals, e.g. when that ticket is itself linked to another one.
      setLinkError({
        id: item.id,
        message: err instanceof Error ? err.message : "Couldn’t link these tickets",
      });
    } finally {
      setLinkingId(null);
    }
  };

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
                {canLink && item.open && (
                  <button
                    type="button"
                    className="btn btn-sm"
                    disabled={linkingId !== null}
                    onClick={() => linkTo(item)}
                  >
                    {linkingId === item.id ? "Linking…" : "Link as duplicate"}
                  </button>
                )}
              </div>
              {linkError?.id === item.id && (
                <p className="field-hint text-red" role="alert">
                  {linkError.message}
                </p>
              )}
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
