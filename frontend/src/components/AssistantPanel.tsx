"use client";

import { SOURCE_LABELS } from "@/components/AssistantHelper";
import { type AssistantAdvice, type AssistantSummary, api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import { useState } from "react";

/** Staff-only assistant on a ticket: a quick summary and a draft fix. */
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
  const [busy, setBusy] = useState<"summary" | "fix" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const cooldown = useCooldown();

  const run = async <T,>(kind: "summary" | "fix", call: () => Promise<T>, done: (r: T) => void) => {
    setBusy(kind);
    setError(null);
    try {
      done(await call());
    } catch (err: unknown) {
      cooldown.trigger(err);
      setError(err instanceof Error ? err.message : "The assistant isn’t available right now.");
    } finally {
      setBusy(null);
    }
  };

  const disabled = busy !== null || cooldown.coolingDown;
  const waitLabel = cooldown.coolingDown ? ` (${cooldown.secondsLeft} s)` : "";

  return (
    <section className="card card-body" aria-labelledby="assistant-title">
      <h2 id="assistant-title" className="section-title">
        Assistant
      </h2>
      <div className="row">
        <button
          type="button"
          className="btn btn-sm"
          disabled={disabled}
          onClick={() => run("summary", () => api.aiSummary(incidentId), setSummary)}
        >
          {busy === "summary" ? "Summarising…" : `Summarise${waitLabel}`}
        </button>
        {canSuggestFix && (
          <button
            type="button"
            className="btn btn-sm"
            disabled={disabled}
            onClick={() => run("fix", () => api.suggestFix(incidentId), setFix)}
          >
            {busy === "fix" ? "Thinking…" : `Suggest a fix${waitLabel}`}
          </button>
        )}
      </div>
      {error && (
        <p className="field-hint" role="alert">
          {error}
        </p>
      )}
      {summary && (
        <div className="assistant-result">
          <p>{summary.summary}</p>
          <p className="field-hint">From {SOURCE_LABELS[summary.source] ?? summary.source}</p>
        </div>
      )}
      {fix && (
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
