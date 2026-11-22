"use client";

import { type AssistantAdvice, type TriageSuggestion, api } from "@/lib/apiClient";
import { CATEGORY_LABELS, PRIORITY_LABELS } from "@/lib/labels";
import { useCooldown } from "@/lib/useCooldown";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

export const SOURCE_LABELS: Record<string, string> = {
  gemini: "Gemini",
  rules: "built-in rules",
};

/**
 * The assistant on the report form. A quick fix appears by itself once the reporter finishes the
 * description and moves on (`finishedKey` changes); "Suggest details" fills in category, impact,
 * urgency and service when asked.
 */
export function AssistantHelper({
  title,
  description,
  finishedKey,
  onApply,
}: {
  title: string;
  description: string;
  /** Bumped each time the reporter leaves the description box. */
  finishedKey: number;
  onApply: (suggestion: TriageSuggestion) => void;
}) {
  const [triage, setTriage] = useState<TriageSuggestion | null>(null);
  const [advice, setAdvice] = useState<AssistantAdvice | null>(null);
  const [busy, setBusy] = useState<"triage" | "help" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<"fixed" | "dismissed" | null>(null);
  const cooldown = useCooldown();

  const ready = title.trim().length >= 4 || description.trim().length >= 10;
  const disabled = !ready || busy !== null || cooldown.coolingDown;

  const run = async <T,>(kind: "triage" | "help", call: () => Promise<T>, done: (r: T) => void) => {
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

  const suggest = () =>
    run(
      "triage",
      () => api.triage(title.trim(), description.trim()),
      (result) => {
        setTriage(result);
        onApply(result);
      }
    );

  // Ask once per finished description; the same text never asks twice.
  const askedFor = useRef("");
  // biome-ignore lint/correctness/useExhaustiveDependencies: only a finished description triggers it
  useEffect(() => {
    const text = `${title.trim()}\n${description.trim()}`;
    if (finishedKey === 0 || !ready || text === askedFor.current) return;
    askedFor.current = text;
    run(
      "help",
      () => api.selfHelp(title.trim(), description.trim()),
      (result) => {
        setAdvice(result);
        setOutcome(null);
      }
    );
  }, [finishedKey]);

  const answer = (helped: boolean) => {
    setOutcome(helped ? "fixed" : "dismissed");
    // Feedback only feeds the deflection report; a failure here shouldn't bother the reporter.
    api.selfHelpFeedback(helped).catch(() => {});
  };

  const waitLabel = cooldown.coolingDown ? ` (${cooldown.secondsLeft} s)` : "";

  return (
    <div className="assistant">
      <div className="row assistant-actions">
        <button type="button" className="btn btn-sm" disabled={disabled} onClick={suggest}>
          {busy === "triage" ? "Thinking…" : `Suggest details${waitLabel}`}
        </button>
        {busy === "help" && <span className="field-hint">Looking for a quick fix…</span>}
        {!ready && <span className="field-hint">Describe the problem to get help.</span>}
      </div>

      {error && (
        <p className="field-hint" role="alert">
          {error}
        </p>
      )}

      {triage && (
        <output className="callout assistant-note">
          <p className="callout-title">
            Filled in by {SOURCE_LABELS[triage.source] ?? triage.source}:{" "}
            {CATEGORY_LABELS[triage.category]}, {PRIORITY_LABELS[triage.predictedPriority]}
            {triage.serviceName ? ` on ${triage.serviceName}` : ""}
          </p>
          <p className="field-hint">
            {triage.itRelated
              ? "You can change anything below."
              : "This doesn’t look like an IT problem."}
          </p>
        </output>
      )}

      {advice && !advice.itRelated && (
        <output className="callout callout-warn">
          <p className="callout-title">This doesn’t look like an IT problem</p>
          <p className="field-hint">
            The service desk handles computers, software, networks, accounts and devices. Check that
            you’re describing a technical problem before you submit.
          </p>
        </output>
      )}

      {advice?.itRelated && outcome !== "dismissed" && (
        <section className="callout assistant-help" aria-label="Try this first">
          {outcome === "fixed" ? (
            <>
              <p className="callout-title">Glad that worked. No ticket needed.</p>
              <Link href="/" className="btn btn-sm">
                Back to my tickets
              </Link>
            </>
          ) : (
            <>
              <p className="callout-title">{advice.summary}</p>
              <ol className="assistant-steps">
                {advice.steps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
              <div className="row">
                <button
                  type="button"
                  className="btn btn-sm btn-primary"
                  onClick={() => answer(true)}
                >
                  This fixed it
                </button>
                <button type="button" className="btn btn-sm" onClick={() => answer(false)}>
                  Still need help
                </button>
                <span className="field-hint">
                  From {SOURCE_LABELS[advice.source] ?? advice.source}
                </span>
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
