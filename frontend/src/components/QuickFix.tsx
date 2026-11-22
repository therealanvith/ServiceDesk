"use client";

import { SOURCE_LABELS } from "@/components/AssistantHelper";
import { type AssistantAdvice, api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import { useCallback, useEffect, useState } from "react";

/**
 * Quick fix for a reporter's own open ticket: steps to try while they wait. It loads by itself
 * when the ticket opens, so nobody has to ask.
 */
export function QuickFix({ incidentId }: { incidentId: string }) {
  const [fix, setFix] = useState<AssistantAdvice | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const cooldown = useCooldown();

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      setFix(await api.suggestFix(incidentId));
    } catch (err: unknown) {
      cooldown.trigger(err);
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [incidentId, cooldown.trigger]);

  // biome-ignore lint/correctness/useExhaustiveDependencies: load once per ticket
  useEffect(() => {
    load();
  }, [incidentId]);

  if (failed && !loading) {
    return (
      <section className="card card-body quick-fix" aria-labelledby="quick-fix-title">
        <h2 id="quick-fix-title" className="section-title">
          Quick fix
        </h2>
        <p className="field-hint">
          Couldn’t load suggestions right now.{" "}
          <button type="button" className="link-btn" disabled={cooldown.coolingDown} onClick={load}>
            {cooldown.coolingDown ? `Try again in ${cooldown.secondsLeft} s` : "Try again"}
          </button>
        </p>
      </section>
    );
  }

  return (
    <section
      className="card card-body quick-fix"
      aria-labelledby="quick-fix-title"
      aria-busy={loading}
    >
      <h2 id="quick-fix-title" className="section-title">
        Quick fix
      </h2>
      {loading || !fix ? (
        <p className="field-hint">Looking for steps that might help…</p>
      ) : (
        <>
          <p className="callout-title">{fix.summary}</p>
          <ol className="assistant-steps">
            {fix.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
          <p className="field-hint">
            From {SOURCE_LABELS[fix.source] ?? fix.source}. If it doesn’t help, the team is on it.
          </p>
        </>
      )}
    </section>
  );
}
