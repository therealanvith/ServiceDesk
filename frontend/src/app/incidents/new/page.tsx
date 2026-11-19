"use client";

import { PriorityMatrixPreview } from "@/components/PriorityMatrixPreview";
import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type CategorySuggestion,
  type Impact,
  type ItService,
  type SimilarIncident,
  type Urgency,
  api,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, CRITICALITY_LABELS } from "@/lib/labels";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

/** Wait for a pause in typing before asking the server for suggestions. */
const DRAFT_DEBOUNCE_MS = 500;

export default function NewIncidentPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<Category>("APPLICATION");
  const [categoryTouched, setCategoryTouched] = useState(false);
  const [impact, setImpact] = useState<Impact>("MEDIUM");
  const [urgency, setUrgency] = useState<Urgency>("MEDIUM");
  const [serviceId, setServiceId] = useState("");
  const [services, setServices] = useState<ItService[]>([]);
  const [suggestion, setSuggestion] = useState<CategorySuggestion | null>(null);
  const [duplicates, setDuplicates] = useState<SimilarIncident[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user)
      api
        .listServices()
        .then(setServices)
        .catch(() => {});
  }, [user]);

  // Category suggestion and duplicate check run together on the draft text.
  useEffect(() => {
    if (!user || (title.trim().length < 4 && description.trim().length < 10)) {
      setSuggestion(null);
      setDuplicates([]);
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      api
        .suggestCategory(title, description)
        .then((s) => active && setSuggestion(s.category ? s : null))
        .catch(() => {});
      api
        .checkDuplicates(title, description)
        .then((d) => active && setDuplicates(d))
        .catch(() => {});
    }, DRAFT_DEBOUNCE_MS);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [title, description, user]);

  // Pre-select the suggestion until the reporter picks a category themselves.
  useEffect(() => {
    if (suggestion?.category && !categoryTouched) setCategory(suggestion.category);
  }, [suggestion, categoryTouched]);

  const service = services.find((s) => s.id === serviceId);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      setError("Add a title and a description.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await api.createIncident({
        title: title.trim(),
        description: description.trim(),
        category,
        impact,
        urgency,
        serviceId: serviceId || undefined,
      });
      router.push(`/incidents/${created.id}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to report incident");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !user) return <output className="empty">Loading…</output>;

  return (
    <>
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link href="/">Incidents</Link>
        <span aria-hidden="true">/</span>
        <span>New</span>
      </nav>
      <div className="page-header">
        <div>
          <h1>Report an issue</h1>
          <p>The more detail you give, the faster it gets fixed.</p>
        </div>
      </div>

      <div className="form-grid">
        <form className="card form-card" onSubmit={handleSubmit}>
          {error && (
            <p className="alert" role="alert">
              {error}
            </p>
          )}
          <label className="field">
            Title
            <input
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="A short summary of the problem"
            />
          </label>
          <label className="field">
            What’s happening?
            <textarea
              required
              rows={7}
              maxLength={10000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Where is it, who’s affected, and what have you already tried?"
            />
          </label>

          {duplicates.length > 0 && (
            <output className="callout callout-warn">
              <p className="callout-title">This may already be reported</p>
              <ul className="plain-list">
                {duplicates.map((d) => (
                  <li key={d.id}>
                    <Link href={`/incidents/${d.id}`} className="link">
                      {d.incidentNumber}
                    </Link>{" "}
                    {d.title}{" "}
                    <span className="muted">· {Math.round(d.similarityScore * 100)}% match</span>
                  </li>
                ))}
              </ul>
              <p className="field-hint">
                If it’s the same problem, add details there instead of opening a new ticket.
              </p>
            </output>
          )}

          <label className="field">
            Affected service
            <select value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
              <option value="">Not sure / other</option>
              {services.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
            {service?.description && <span className="field-hint">{service.description}</span>}
          </label>

          <div className="field-row">
            <label className="field">
              Category
              <select
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value as Category);
                  setCategoryTouched(true);
                }}
              >
                {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
              {suggestion?.category && (
                <span className="field-hint">
                  {suggestion.category === category ? "Suggested" : "Suggestion"}:{" "}
                  {CATEGORY_LABELS[suggestion.category]}
                  {suggestion.matchedKeywords.length > 0 &&
                    ` (from “${suggestion.matchedKeywords.slice(0, 3).join("”, “")}”)`}
                  {suggestion.category !== category && (
                    <>
                      {" · "}
                      <button
                        type="button"
                        className="link-btn"
                        onClick={() => suggestion.category && setCategory(suggestion.category)}
                      >
                        Use it
                      </button>
                    </>
                  )}
                </span>
              )}
            </label>
            <label className="field">
              Who’s affected?
              <select value={impact} onChange={(e) => setImpact(e.target.value as Impact)}>
                <option value="LOW">Just me</option>
                <option value="MEDIUM">My team</option>
                <option value="HIGH">Several teams</option>
              </select>
            </label>
            <label className="field">
              How urgent?
              <select value={urgency} onChange={(e) => setUrgency(e.target.value as Urgency)}>
                <option value="LOW">It can wait</option>
                <option value="MEDIUM">Soon</option>
                <option value="HIGH">I’m blocked</option>
              </select>
            </label>
          </div>
          <div className="form-actions">
            <Link href="/" className="btn">
              Cancel
            </Link>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Submitting…" : "Submit ticket"}
            </button>
          </div>
        </form>

        <aside className="card card-body">
          <h2 className="section-title">Priority</h2>
          <p className="muted" style={{ fontSize: 13, marginBottom: 12 }}>
            Set from who’s affected (rows), how urgent it is (columns)
            {service
              ? ` and how critical ${service.name} is (${CRITICALITY_LABELS[service.criticality].toLowerCase()})`
              : ""}
            .
          </p>
          <PriorityMatrixPreview
            impact={impact}
            urgency={urgency}
            criticality={service?.criticality}
          />
        </aside>
      </div>
    </>
  );
}
