"use client";

import { SOURCE_LABELS } from "@/components/AssistantHelper";
import { type AssistantAdvice, type AssistantSummary, api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import { useCallback, useEffect, useState } from "react";

/**
 * Staff-only assistant on a ticket: a summary and a draft fix. Both load by themselves when the
 * ticket opens; the server keeps them, so the next visit reads the saved answer.
 */
export function AssistantPanel({
  incidentId,
  onUseAsResolution,
  canSuggestFix = true,
}: {
  incidentId: string;
  /** False once the ticket is resolved, closed or cancelled: there is nothing left to fix. */
  canSuggestFix?: boolean;
  /** Present when the viewer can resolve the ticket now. */
  onUseAsResolution?: (text: string) => void;
}) {
  const [summary, setSummary] = useState<AssistantSummary | null>(null);
  const [fix, setFix] = useState<AssistantAdvice | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const cooldown = useCooldown();

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const [s, f] = await Promise.all([
        api.aiSummary(incidentId),
        canSuggestFix ? api.suggestFix(incidentId) : Promise.resolve(null),
      ]);
      setSummary(s);
      setFix(f);
    } catch (err: unknown) {
      cooldown.trigger(err);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [incidentId, canSuggestFix, cooldown.trigger]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: load once per ticket
  useEffect(() => {
    load();
  }, [incidentId]);

  return (
    <section className="card card-body" aria-labelledby="assistant-title" aria-busy={loading}>
      <h2 id="assistant-title" className="section-title">
        Assistant
      </h2>
      {loading && <p className="field-hint">Reading the ticket…</p>}
      {failed && !loading && (
        <p className="field-hint" role="alert">
          The assistant isn’t available right now.{" "}
          <button type="button" className="link-btn" disabled={cooldown.coolingDown} onClick={load}>
            {cooldown.coolingDown ? `Try again in ${cooldown.secondsLeft} s` : "Try again"}
          </button>
        </p>
      )}
      {summary && (
        <div className="assistant-result">
          <p>{summary.summary}</p>
          <p className="field-hint">From {SOURCE_LABELS[summary.source] ?? summary.source}</p>
        </div>
      )}
      {fix && canSuggestFix && (
        <div className="assistant-result">
          <p className="callout-title">{fix.summary}</p>
          <ol className="assistant-steps">
            {fix.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
          <div className="row">
            {onUseAsResolution && (
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => onUseAsResolution(fix.steps.join("\n"))}
              >
                Use as resolution
              </button>
            )}
            <span className="field-hint">
              Draft from {SOURCE_LABELS[fix.source] ?? fix.source}; check it before using.
            </span>
          </div>
        </div>
      )}
    </section>
  );
}
