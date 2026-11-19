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

  useEffect(() => {
    api
      .listServices(true)
      .then(setServices)
      .catch((err: Error) => onError(err.message));
  }, [onError]);

  const startEdit = (service: ItService | null) => {
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
    setSaving(true);
    onError(null);
    try {
      const payload = {
        ...draft,
        name: draft.name.trim(),
        description: draft.description?.trim() || null,
      };
      const result =
        editing === "new"
          ? await api.createService(payload)
          : await api.updateService(editing as string, payload);
      setServices((list) =>
        (editing === "new"
          ? [...list, result]
          : list.map((s) => (s.id === result.id ? result : s))
        ).sort((a, b) => a.name.localeCompare(b.name))
      );
      setEditing(null);
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
        <button type="button" className="btn" onClick={() => setEditing(null)}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {saving ? "Saving…" : "Save service"}
        </button>
      </div>
    </form>
  );

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
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => startEdit(s)}>
                Edit
              </button>
            </li>
          )
        )}
        <li>
          {editing === "new" ? (
            form
          ) : (
            <button type="button" className="btn btn-sm" onClick={() => startEdit(null)}>
              Add a service
            </button>
          )}
        </li>
      </ul>
    </div>
  );
}
