"use client";

import { AssistantPanel } from "@/components/AssistantPanel";
import { AuditTimeline } from "@/components/AuditTimeline";
import { showToast } from "@/components/LiveUpdates";
import { PriorityBadge } from "@/components/PriorityBadge";
import { SimilarIncidentsPanel } from "@/components/SimilarIncidentsPanel";
import { SkeletonBlock } from "@/components/Skeleton";
import { SlaBadge } from "@/components/SlaBadge";
import { StateActions } from "@/components/StateActions";
import { StatusBadge } from "@/components/StatusBadge";
import { UpdatesThread } from "@/components/UpdatesThread";
import { useAuth } from "@/context/AuthContext";
import { type AuditLogItem, type CommentItem, type IncidentDetail, api } from "@/lib/apiClient";
import {
  CATEGORY_LABELS,
  CRITICALITY_LABELS,
  ESCALATION_LABELS,
  formatDateTime,
  isAdmin,
  timeAgo,
} from "@/lib/labels";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

export default function IncidentDetailPage() {
  const params = useParams();
  const incidentId = params?.id as string;

  const { user, loading: authLoading } = useAuth();
  const requestVersion = useRef(0);

  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [commentsFailed, setCommentsFailed] = useState(false);
  const [auditFailed, setAuditFailed] = useState(false);
  const panelVersion = useRef(0);
  const [dialogRequest, setDialogRequest] = useState<
    { id: number; kind: "provideInfo" | "resolve"; text?: string } | undefined
  >();
  const requestDialog = (kind: "provideInfo" | "resolve", text?: string) =>
    setDialogRequest((prev) => ({ id: (prev?.id ?? 0) + 1, kind, text }));

  // Comments and history load separately from the ticket, so one failing never hides the other,
  // and after an action the confirmed ticket stays on screen while these refresh.
  const loadPanels = useCallback(async () => {
    if (!incidentId || !user) return;
    const version = ++panelVersion.current;
    const [cRes, aRes] = await Promise.allSettled([
      api.getComments(incidentId),
      user.role === "EMPLOYEE" ? Promise.resolve([]) : api.getAuditLogs(incidentId),
    ]);
    if (version !== panelVersion.current) return;
    setCommentsFailed(cRes.status === "rejected");
    setAuditFailed(aRes.status === "rejected");
    if (cRes.status === "fulfilled") setComments(cRes.value);
    if (aRes.status === "fulfilled") setAuditLogs(aRes.value);
  }, [incidentId, user]);

  // Shows the latest state without blanking the page.
  const refresh = useCallback(() => {
    api
      .getIncident(incidentId)
      .then((inc) => {
        setIncident(inc);
        return loadPanels();
      })
      .catch(() => {});
  }, [incidentId, loadPanels]);

  // Someone else changed this ticket, or a ticket linked under it (its list shows their status).
  useLiveEvent((e) => {
    if (e.event !== "incident-updated" || !incident) return;
    const { incidentId: changed } = e.data;
    if (
      changed === incidentId ||
      changed === incident.parentIncident?.id ||
      incident.childIncidents.some((c) => c.id === changed)
    ) {
      refresh();
    }
  });

  const [unlinking, setUnlinking] = useState(false);
  const [unlinkError, setUnlinkError] = useState<string | null>(null);
  const unlink = async () => {
    setUnlinking(true);
    setUnlinkError(null);
    try {
      const updated = await api.unlinkIncident(incidentId);
      setIncident(updated);
      loadPanels();
      showToast({ title: "Ticket unlinked", tone: "success" });
    } catch (err: unknown) {
      setUnlinkError(err instanceof Error ? err.message : "Couldn’t unlink this ticket");
    } finally {
      setUnlinking(false);
    }
  };

  const loadData = useCallback(async () => {
    const version = ++requestVersion.current;
    setIncident(null);
    setComments([]);
    setAuditLogs([]);
    if (!incidentId || !user) return;
    setLoading(true);
    setError(null);
    try {
      const inc = await api.getIncident(incidentId);
      if (version !== requestVersion.current) return;
      setIncident(inc);
      await loadPanels();
    } catch (err: unknown) {
      if (version !== requestVersion.current) return;
      setError(err instanceof Error ? err.message : "Failed to load incident");
    } finally {
      if (version === requestVersion.current) setLoading(false);
    }
  }, [incidentId, user, loadPanels]);

  useEffect(() => {
    loadData();
    return () => {
      requestVersion.current++;
    };
  }, [loadData]);

  if (!authLoading && !user) {
    return (
      <div className="card empty">
        <h2>Sign in to view this ticket</h2>
        <Link className="btn btn-primary" href="/login">
          Sign in
        </Link>
      </div>
    );
  }

  if (loading) {
    return <SkeletonBlock label="Loading ticket…" variant="detail" />;
  }

  if (error || !incident) {
    return (
      <div className="card empty" role="alert">
        <h2>Couldn’t open this ticket</h2>
        <p>{error ?? "It may have been removed, or you don’t have access."}</p>
        <Link className="btn" href="/">
          Back to incidents
        </Link>
      </div>
    );
  }

  const isReporter = Boolean(user && incident.reporter.id === user.id);
  const currentUserRole = user?.role ?? "EMPLOYEE";
  const isPaused = incident.status === "PENDING_INFO";
  const sla = incident.slaWindow;
  const parent = incident.parentIncident;
  const children = incident.childIncidents ?? [];

  return (
    <>
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link href="/">Incidents</Link>
        <span aria-hidden="true">/</span>
        <span className="mono">{incident.incidentNumber}</span>
      </nav>
      <div className="detail-title-row">
        <h1 className="detail-title">{incident.title}</h1>
        {children.length > 0 && <span className="tag tag-major">Major incident</span>}
      </div>
      <p className="detail-sub">
        Opened by {incident.reporter.fullName}{" "}
        <time dateTime={incident.createdAt} title={new Date(incident.createdAt).toLocaleString()}>
          {timeAgo(incident.createdAt)}
        </time>
      </p>

      <div className="detail">
        <div className="stack">
          {parent && (
            <div className="callout callout-neutral linked-parent">
              <p>
                Part of{" "}
                <Link href={`/incidents/${parent.id}`} className="link mono">
                  {parent.incidentNumber}
                </Link>
                : {parent.title} <StatusBadge status={parent.status} />
              </p>
              {isAdmin(currentUserRole) && (
                <button type="button" className="btn btn-sm" disabled={unlinking} onClick={unlink}>
                  {unlinking ? "Unlinking…" : "Unlink"}
                </button>
              )}
              {unlinkError && (
                <p className="field-hint text-red" role="alert">
                  {unlinkError}
                </p>
              )}
            </div>
          )}

          <section className="card card-body stack" aria-label="Description">
            <p className="description">{incident.description}</p>
            {incident.resolutionNotes && (
              <div className="callout">
                <p className="callout-title">Resolution</p>
                <p className="description">{incident.resolutionNotes}</p>
              </div>
            )}
            {incident.feedbackRating != null && (
              <div className="callout callout-neutral">
                <p className="callout-title">Reporter feedback: {incident.feedbackRating}/5</p>
                {incident.feedbackComment && (
                  <p className="description">{incident.feedbackComment}</p>
                )}
              </div>
            )}
          </section>

          {children.length > 0 && (
            <section className="card" aria-labelledby="linked-title">
              <div className="card-header">
                <h2 id="linked-title">Linked tickets ({children.length})</h2>
                <span className="hint">Resolved together with this one</span>
              </div>
              <ul className="similar-list">
                {children.map((child) => (
                  <li key={child.id} className="similar-item">
                    <div className="similar-head">
                      <Link href={`/incidents/${child.id}`} className="mono link">
                        {child.incidentNumber}
                      </Link>
                      <span className="title">{child.title}</span>
                      <StatusBadge status={child.status} />
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {currentUserRole !== "EMPLOYEE" && (
            <SimilarIncidentsPanel
              incidentId={incident.id}
              canLink={
                isAdmin(currentUserRole) &&
                !parent &&
                children.length === 0 &&
                !["RESOLVED", "CLOSED", "CANCELLED"].includes(incident.status)
              }
              onLinked={refresh}
            />
          )}
          {currentUserRole !== "EMPLOYEE" && (
            <AssistantPanel
              incidentId={incident.id}
              onUseAsResolution={
                incident.status === "IN_PROGRESS" &&
                (isAdmin(currentUserRole) || incident.assignee?.id === user?.id)
                  ? (text) => requestDialog("resolve", text)
                  : undefined
              }
            />
          )}

          {commentsFailed ? (
            <PanelError title="Updates" onRetry={loadPanels} />
          ) : (
            <UpdatesThread
              incidentId={incident.id}
              initialComments={comments}
              currentUserRole={currentUserRole}
              status={incident.status}
              onReply={isReporter ? () => requestDialog("provideInfo") : undefined}
            />
          )}

          {currentUserRole !== "EMPLOYEE" &&
            (auditFailed ? (
              <PanelError title="History" onRetry={loadPanels} />
            ) : (
              <AuditTimeline logs={auditLogs} />
            ))}
        </div>

        <aside className="aside" aria-label="Ticket details">
          <section className="card card-body">
            <dl className="props">
              <dt>Status</dt>
              <dd>
                <StatusBadge status={incident.status} />
              </dd>
              <dt>Priority</dt>
              <dd>
                <PriorityBadge priority={incident.priority} />
              </dd>
              <dt>SLA</dt>
              <dd>
                <SlaBadge status={sla?.slaStatus ?? null} isPaused={isPaused} />
                {sla && sla.pausedSeconds > 0 && (
                  <span className="props-sub">
                    Paused {Math.round(sla.pausedSeconds / 60)} min so far
                  </span>
                )}
              </dd>
              <dt>Due</dt>
              <dd>
                {sla?.resolutionDeadline ? (
                  formatDateTime(sla.resolutionDeadline)
                ) : (
                  <span className="muted">—</span>
                )}
              </dd>
              <dt>Response</dt>
              <dd>
                {incident.respondedAt ? (
                  <>
                    {formatDateTime(incident.respondedAt)}
                    {sla && (
                      <span className="props-sub">
                        {new Date(incident.respondedAt) <= new Date(sla.responseDeadline)
                          ? "Within target"
                          : "Later than target"}
                      </span>
                    )}
                  </>
                ) : sla ? (
                  <>
                    <span className="muted">Waiting</span>
                    <span className="props-sub">Due {formatDateTime(sla.responseDeadline)}</span>
                  </>
                ) : (
                  <span className="muted">—</span>
                )}
                {incident.escalatedAt && (
                  <span className="props-sub text-red">
                    {incident.escalationLevel !== "NONE"
                      ? ESCALATION_LABELS[incident.escalationLevel]
                      : "Escalated"}{" "}
                    {formatDateTime(incident.escalatedAt)}
                  </span>
                )}
              </dd>
              <dt>Service</dt>
              <dd>
                {incident.service ? (
                  <>
                    {incident.service.name}
                    <span className="props-sub">
                      {CRITICALITY_LABELS[incident.service.criticality]} criticality
                    </span>
                  </>
                ) : (
                  <span className="muted">Not specified</span>
                )}
              </dd>
              <dt>Category</dt>
              <dd>{CATEGORY_LABELS[incident.category] ?? incident.category}</dd>
              <dt>Assignee</dt>
              <dd>
                {incident.assignee ? (
                  incident.assignee.fullName
                ) : (
                  <span className="unassigned">Unassigned</span>
                )}
              </dd>
              <dt>Reporter</dt>
              <dd>{incident.reporter.fullName}</dd>
              <dt>Created</dt>
              <dd>{formatDateTime(incident.createdAt)}</dd>
            </dl>
          </section>

          <StateActions
            incident={incident}
            currentUserRole={currentUserRole}
            isReporter={isReporter}
            isAssignee={Boolean(user && incident.assignee?.id === user.id)}
            dialogRequest={dialogRequest}
            onUpdate={(updated) => {
              // The action succeeded: show the confirmed ticket and refresh only the panels.
              setIncident(updated);
              loadPanels();
            }}
          />
        </aside>
      </div>
    </>
  );
}

function PanelError({ title, onRetry }: { title: string; onRetry: () => void }) {
  return (
    <section className="card" role="alert">
      <div className="card-header">
        <h2>{title}</h2>
      </div>
      <div className="card-body row">
        <span className="muted">Couldn’t load this section.</span>
        <button type="button" className="btn btn-sm" onClick={onRetry}>
          Try again
        </button>
      </div>
    </section>
  );
}
