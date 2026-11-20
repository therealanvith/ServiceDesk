"use client";

import { type Criticality, type ItService, api } from "@/lib/apiClient";
import { CRITICALITY_LABELS } from "@/lib/labels";
import { useEffect, useState } from "react";

type Draft = Omit<ItService, "id">;

const NEW_SERVICE: Draft = { name: "", description: "", criticality: "MEDIUM", active: true };

/** The IT services people can report problems against, and how critical each one is. */
export function ServiceCatalogSettings({ onError }: { onError: (message: string | null) => void }) {
  const [services, setServices] = useState<ItService[]>([]);
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(NEW_SERVICE);
  const [saving, setSaving] = useState(false);
  // Nothing can be created or edited until the catalogue has loaded, so a slow first load can't
  // overwrite a service added in the meantime.
  const [status, setStatus] = useState<"loading" | "ready" | "failed">("loading");
  const [reloadKey, setReloadKey] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    setStatus("loading");
    api
      .listServices(true)
      .then((list) => {
        setServices(list);
        setStatus("ready");
      })
      .catch((err: Error) => {
        onError(err.message);
        setStatus("failed");
      });
  }, [onError, reloadKey]);

  const startEdit = (service: ItService | null) => {
    if (saving) return;
    onError(null);
    setEditing(service?.id ?? "new");
    setDraft(
      service
        ? {
            name: service.name,
            description: service.description ?? "",
            criticality: service.criticality,
            active: service.active,
          }
        : NEW_SERVICE
    );
  };

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    const session = editing;
    setSaving(true);
    onError(null);
    try {
      const payload = {
        ...draft,
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
      };
      const result =
        session === "new"
          ? await api.createService(payload)
          : await api.updateService(session as string, payload);
      setServices((list) =>
        (session === "new"
          ? [...list, result]
          : list.map((s) => (s.id === result.id ? result : s))
        ).sort((a, b) => a.name.localeCompare(b.name))
      );
      // Close only the editor that started this save.
      setEditing((current) => (current === session ? null : current));
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : "Couldn't save the service");
    } finally {
      setSaving(false);
    }
  };

  const form = (
    <form className="service-form" onSubmit={save}>
      <label className="field">
        Name
        <input
          required
          maxLength={100}
          value={draft.name}
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
        />
      </label>
      <label className="field">
        Business criticality
        <select
          value={draft.criticality}
          onChange={(e) => setDraft({ ...draft, criticality: e.target.value as Criticality })}
        >
          {Object.entries(CRITICALITY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label className="field service-form-wide">
        Description <span className="field-hint">(shown to reporters)</span>
        <input
          maxLength={1000}
          value={draft.description ?? ""}
          onChange={(e) => setDraft({ ...draft, description: e.target.value })}
        />
      </label>
      {editing !== "new" && (
        <label className="checkbox">
          <input
            type="checkbox"
            checked={draft.active}
            onChange={(e) => setDraft({ ...draft, active: e.target.checked })}
          />
          Available on the report form
        </label>
      )}
      <div className="row-end service-form-wide">
        <button type="button" className="btn" disabled={saving} onClick={() => setEditing(null)}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? "Saving…" : "Save service"}
        </button>
      </div>
    </form>
  );

  if (status === "loading") {
    return <output className="card empty">Loading services…</output>;
  }
  if (status === "failed") {
    return (
      <div className="card empty" role="alert">
        <h2>Couldn’t load services</h2>
        <button type="button" className="btn" onClick={() => setReloadKey((k) => k + 1)}>
          Try again
        </button>
      </div>
    );
  }

  return (
    <div className="card">
      <ul className="service-list">
        {services.map((s) =>
          editing === s.id ? (
            <li key={s.id}>{form}</li>
          ) : (
            <li key={s.id} className={s.active ? undefined : "is-inactive"}>
              <div>
                <span className="radio-title">{s.name}</span>
                {!s.active && (
                  <span className="tag" style={{ marginLeft: 6 }}>
                    Retired
                  </span>
                )}
                {s.description && <span className="radio-desc"> — {s.description}</span>}
              </div>
              <span className="muted col-nowrap">{CRITICALITY_LABELS[s.criticality]}</span>
              <button
                type="button"
                className="btn btn-ghost btn-sm"
                disabled={saving}
                onClick={() => startEdit(s)}
              >
                Edit
              </button>
            </li>
          )
        )}
        <li>
          {editing === "new" ? (
            form
          ) : (
            <button
              type="button"
              className="btn btn-sm"
              disabled={saving}
              onClick={() => startEdit(null)}
            >
              Add a service
            </button>
          )}
        </li>
      </ul>
    </div>
  );
}
