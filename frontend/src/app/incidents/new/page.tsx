"use client";

import { AssistantHelper } from "@/components/AssistantHelper";
import { PriorityMatrixPreview } from "@/components/PriorityMatrixPreview";
import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type CategorySuggestion,
  type Impact,
  type ItService,
  type PriorityPreview,
  type ServiceStatus,
  type SimilarIncident,
  type TriageSuggestion,
  type Urgency,
  api,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, CRITICALITY_LABELS, SERVICE_HEALTH_LABELS } from "@/lib/labels";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

/** Wait for a pause in typing before asking the server for suggestions. */
const DRAFT_DEBOUNCE_MS = 500;
const DEFAULT_CATEGORY: Category = "APPLICATION";

export default function NewIncidentPage() {
  const { user, loading } = useAuth();
  const router = useRouter();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<Category>(DEFAULT_CATEGORY);
  const [categoryTouched, setCategoryTouched] = useState(false);
  const [impact, setImpact] = useState<Impact>("MEDIUM");
  const [urgency, setUrgency] = useState<Urgency>("MEDIUM");
  const [serviceId, setServiceId] = useState("");
  const [services, setServices] = useState<ItService[]>([]);
  const [servicesFailed, setServicesFailed] = useState(false);
  const [servicesKey, setServicesKey] = useState(0);
  const [preview, setPreview] = useState<PriorityPreview | null>(null);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [previewKey, setPreviewKey] = useState(0);
  const [suggestion, setSuggestion] = useState<CategorySuggestion | null>(null);
  const [duplicates, setDuplicates] = useState<SimilarIncident[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [finishedKey, setFinishedKey] = useState(0);
  const [serviceStatus, setServiceStatus] = useState<ServiceStatus[]>([]);
  const [triage, setTriage] = useState<TriageSuggestion | null>(null);
  const [offered, setOffered] = useState<Category[] | null>(null);
  // A suggestion for a category an administrator switched off is ignored.
  const offeredRef = useRef<Category[] | null>(null);
  const pickCategory = useCallback((c: Category) => {
    const list = offeredRef.current;
    if (!list || list.includes(c)) setCategory(c);
  }, []);

  // biome-ignore lint/correctness/useExhaustiveDependencies: servicesKey re-runs the load for "Try again"
  useEffect(() => {
    if (!user) return;
    setServicesFailed(false);
    api
      .listServices()
      .then(setServices)
      .catch(() => setServicesFailed(true));
  }, [user, servicesKey]);

  // Only the categories an administrator has switched on are offered.
  useEffect(() => {
    if (!user) return;
    api
      .listCategories()
      .then((list) => {
        const active = list.filter((c) => c.active).map((c) => c.category);
        offeredRef.current = active;
        setOffered(active);
      })
      .catch(() => {});
  }, [user]);

  // Known problems on a service: the reporter can check them before adding another ticket.
  useEffect(() => {
    if (!user) return;
    api
      .getServiceStatus()
      .then(setServiceStatus)
      .catch(() => {});
  }, [user]);

  // The server owns the priority policy; ask it whenever an input changes.
  // biome-ignore lint/correctness/useExhaustiveDependencies: previewKey re-runs the request for "Try again"
  useEffect(() => {
    if (!user) return;
    let active = true;
    setPreviewFailed(false);
    api
      .previewPriority(impact, urgency, serviceId || undefined)
      .then((p) => active && setPreview(p))
      .catch(() => active && setPreviewFailed(true));
    return () => {
      active = false;
    };
  }, [user, impact, urgency, serviceId, previewKey]);

  // Category suggestion and duplicate check run together on the draft text. Advice belongs to
  // the exact text that produced it: as soon as the text changes the old advice is dropped, and
  // a category the system picked (not one the reporter chose) goes back to the default.
  useEffect(() => {
    setSuggestion(null);
    setDuplicates([]);
    if (!categoryTouched) setCategory(DEFAULT_CATEGORY);
    if (!user || (title.trim().length < 4 && description.trim().length < 10)) {
      return;
    }
    let active = true;
    const timer = window.setTimeout(() => {
      api
        .suggestCategory(title, description)
        .then((s) => {
          if (!active) return;
          setSuggestion(s.category ? s : null);
          if (s.category && !categoryTouched) pickCategory(s.category);
        })
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
  }, [title, description, user, categoryTouched, pickCategory]);

  const service = services.find((s) => s.id === serviceId);
  const affected = serviceStatus.find(
    (s) => s.serviceId === serviceId && s.status !== "OPERATIONAL"
  );

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
        // Noted in the audit trail when the reporter kept every suggested value.
        aiSuggestionAccepted:
          triage !== null &&
          triage.category === category &&
          triage.impact === impact &&
          triage.urgency === urgency &&
          (triage.serviceId ?? "") === serviceId,
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
              onBlur={() => setFinishedKey((k) => k + 1)}
              placeholder="Where is it, who’s affected, and what have you already tried?"
            />
          </label>

          <AssistantHelper
            title={title}
            description={description}
            finishedKey={finishedKey}
            onApply={(s) => {
              setTriage(s);
              pickCategory(s.category);
              setCategoryTouched(true);
              setImpact(s.impact);
              setUrgency(s.urgency);
              if (s.serviceId) setServiceId(s.serviceId);
            }}
          />

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
            {servicesFailed && (
              <span className="field-hint" role="alert">
                Couldn’t load the service list.{" "}
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => setServicesKey((k) => k + 1)}
                >
                  Try again
                </button>
              </span>
            )}
          </label>
          {affected && (
            <output className="callout callout-warn known-issue">
              <p className="callout-title">Known issue on {affected.name}</p>
              <p className="field-hint">
                {SERVICE_HEALTH_LABELS[affected.status]} · {affected.openTickets} open ticket
                {affected.openTickets === 1 ? "" : "s"}.{" "}
                <Link
                  href={`/issues?${new URLSearchParams({ serviceId: affected.serviceId })}`}
                  className="link"
                >
                  See reported issues
                </Link>{" "}
                before opening a new one.
              </p>
            </output>
          )}

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
                {Object.entries(CATEGORY_LABELS)
                  .filter(([value]) => !offered || offered.includes(value as Category))
                  .map(([value, label]) => (
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
                        onClick={() => {
                          if (suggestion.category) {
                            pickCategory(suggestion.category);
                            setCategoryTouched(true);
                          }
                        }}
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
            preview={preview}
            failed={previewFailed}
            onRetry={() => setPreviewKey((k) => k + 1)}
          />
        </aside>
      </div>
    </>
  );
}
