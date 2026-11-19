"use client";

import { PriorityBadge } from "@/components/PriorityBadge";
import { type SlaPolicy, api } from "@/lib/apiClient";
import { formatMinutes } from "@/lib/labels";
import { useEffect, useState } from "react";

/** Response and resolution targets per priority; edits apply to tickets created afterwards. */
export function SlaPolicySettings({ onError }: { onError: (message: string | null) => void }) {
  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [drafts, setDrafts] = useState<Record<string, { response: string; resolution: string }>>(
    {}
  );
  const [saving, setSaving] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  useEffect(() => {
    api
      .listSlaPolicies()
      .then((list) => {
        setPolicies(list);
        setDrafts(
          Object.fromEntries(
            list.map((p) => [
              p.priority,
              { response: String(p.responseMinutes), resolution: String(p.resolutionMinutes) },
            ])
          )
        );
      })
      .catch((err: Error) => onError(err.message));
  }, [onError]);

  const save = async (policy: SlaPolicy) => {
    const draft = drafts[policy.priority];
    if (!draft) return;
    const response = Number(draft.response);
    const resolution = Number(draft.resolution);
    if (!Number.isInteger(response) || !Number.isInteger(resolution) || response < 1) {
      onError("Targets must be whole numbers of minutes.");
      return;
    }
    if (resolution < response) {
      onError("Resolution target can't be shorter than the response target.");
      return;
    }
    setSaving(policy.priority);
    onError(null);
    try {
      const updated = await api.updateSlaPolicy(policy.priority, response, resolution);
      setPolicies((list) => list.map((p) => (p.priority === updated.priority ? updated : p)));
      setSaved(policy.priority);
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : "Couldn't save the SLA target");
    } finally {
      setSaving(null);
    }
  };

  return (
    <div className="card table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Priority</th>
            <th scope="col">First response (min)</th>
            <th scope="col">Resolution (min)</th>
            <th scope="col" aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {policies.map((p) => {
            const draft = drafts[p.priority] ?? { response: "", resolution: "" };
            const dirty =
              draft.response !== String(p.responseMinutes) ||
              draft.resolution !== String(p.resolutionMinutes);
            return (
              <tr key={p.priority}>
                <td className="col-nowrap">
                  <PriorityBadge priority={p.priority} />
                </td>
                <td>
                  <input
                    className="input input-narrow"
                    type="number"
                    min={1}
                    max={43200}
                    aria-label={`First response target for ${p.priority}`}
                    value={draft.response}
                    onChange={(e) => {
                      setSaved(null);
                      setDrafts({
                        ...drafts,
                        [p.priority]: { ...draft, response: e.target.value },
                      });
                    }}
                  />
                  <span className="props-sub">{formatMinutes(p.responseMinutes)}</span>
                </td>
                <td>
                  <input
                    className="input input-narrow"
                    type="number"
                    min={1}
                    max={43200}
                    aria-label={`Resolution target for ${p.priority}`}
                    value={draft.resolution}
                    onChange={(e) => {
                      setSaved(null);
                      setDrafts({
                        ...drafts,
                        [p.priority]: { ...draft, resolution: e.target.value },
                      });
                    }}
                  />
                  <span className="props-sub">{formatMinutes(p.resolutionMinutes)}</span>
                </td>
                <td className="col-nowrap">
                  {dirty ? (
                    <button
                      type="button"
                      className="btn btn-sm btn-primary"
                      disabled={saving === p.priority}
                      onClick={() => save(p)}
                    >
                      {saving === p.priority ? "Saving…" : "Save"}
                    </button>
                  ) : (
                    saved === p.priority && <span className="muted">Saved</span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
