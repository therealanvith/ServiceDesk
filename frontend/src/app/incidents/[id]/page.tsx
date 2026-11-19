"use client";

import { AuditTimeline } from "@/components/AuditTimeline";
import { CommentThread } from "@/components/CommentThread";
import { PriorityBadge } from "@/components/PriorityBadge";
import { SimilarIncidentsPanel } from "@/components/SimilarIncidentsPanel";
import { SlaBadge } from "@/components/SlaBadge";
import { StateActions } from "@/components/StateActions";
import { StatusBadge } from "@/components/StatusBadge";
import { useAuth } from "@/context/AuthContext";
import { type AuditLogItem, type CommentItem, type IncidentDetail, api } from "@/lib/apiClient";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

export default function IncidentDetailPage() {
  const params = useParams();
  const incidentId = params?.id as string;

  const { user } = useAuth();

  const [incident, setIncident] = useState<IncidentDetail | null>(null);
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!incidentId) return;
    setLoading(true);
    setError(null);
    try {
      const inc = await api.getIncident(incidentId);
      setIncident(inc);

      // Load comments & audit logs in parallel
      const [cRes, aRes] = await Promise.allSettled([
        api.getComments(incidentId),
        api.getAuditLogs(incidentId),
      ]);

      if (cRes.status === "fulfilled") setComments(cRes.value);
      if (aRes.status === "fulfilled") setAuditLogs(aRes.value);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load incident");
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (loading) {
    return (
      <div
        style={{
          padding: "3rem",
          textAlign: "center",
          color: "var(--text-muted)",
          fontSize: "0.875rem",
        }}
      >
        Loading ticket details...
      </div>
    );
  }

  if (error || !incident) {
    return (
      <div style={{ maxWidth: "600px", margin: "2rem auto", textAlign: "center" }}>
        <div
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            padding: "1rem",
            borderRadius: "6px",
            border: "1px solid #fecaca",
            marginBottom: "1rem",
            fontSize: "0.875rem",
          }}
        >
          {error ?? "Incident not found"}
        </div>
        <Link
          href="/"
          style={{
            color: "var(--accent)",
            fontWeight: 600,
            fontSize: "0.875rem",
          }}
        >
          &larr; Return to Incidents Dashboard
        </Link>
      </div>
    );
  }

  const isReporter = Boolean(user && incident.reporter.id === user.id);
  const currentUserRole = user?.role ?? "EMPLOYEE";
  const isPaused = incident.status === "PENDING_INFO";

  return (
    <div style={{ maxWidth: "900px", margin: "0 auto" }}>
      {/* Top back link */}
      <div style={{ marginBottom: "1rem" }}>
        <Link
          href="/"
          style={{
            fontSize: "0.8125rem",
            color: "var(--accent)",
            fontWeight: 500,
          }}
        >
          &larr; Back to Incidents
        </Link>
      </div>

      {/* Ticket Header Card */}
      <div
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.5rem",
          marginBottom: "1.5rem",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            flexWrap: "wrap",
            gap: "0.75rem",
            marginBottom: "0.75rem",
          }}
        >
          <div>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "0.5rem",
                marginBottom: "0.25rem",
              }}
            >
              <span
                style={{
                  fontFamily: "var(--font-mono)",
                  fontWeight: 700,
                  fontSize: "0.875rem",
                  color: "var(--accent)",
                }}
              >
                {incident.incidentNumber}
              </span>
              <span
                style={{
                  fontSize: "0.75rem",
                  padding: "0.15rem 0.45rem",
                  borderRadius: "4px",
                  backgroundColor: "#f1f5f9",
                  color: "#475569",
                  fontWeight: 500,
                }}
              >
                {incident.category}
              </span>
            </div>
            <h1
              style={{
                fontSize: "1.375rem",
                fontWeight: 700,
                color: "var(--text-primary)",
                letterSpacing: "-0.02em",
              }}
            >
              {incident.title}
            </h1>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            <PriorityBadge priority={incident.priority} />
            <StatusBadge status={incident.status} />
            <SlaBadge status={incident.slaWindow?.slaStatus ?? null} isPaused={isPaused} />
          </div>
        </div>

        <p
          style={{
            fontSize: "0.9375rem",
            color: "var(--text-secondary)",
            lineHeight: 1.6,
            marginBottom: "1.25rem",
            whiteSpace: "pre-wrap",
          }}
        >
          {incident.description}
        </p>

        {/* Resolution Notes Banner */}
        {incident.resolutionNotes && (
          <div
            style={{
              backgroundColor: "#f0fdf4",
              border: "1px solid #bbf7d0",
              borderRadius: "4px",
              padding: "0.875rem",
              marginBottom: "1.25rem",
            }}
          >
            <div
              style={{
                fontSize: "0.8125rem",
                fontWeight: 700,
                color: "#166534",
                marginBottom: "0.25rem",
              }}
            >
              Resolution Summary
            </div>
            <p style={{ fontSize: "0.875rem", color: "#14532d" }}>{incident.resolutionNotes}</p>
          </div>
        )}

        {/* Metadata Grid */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
            gap: "1rem",
            fontSize: "0.8125rem",
            borderTop: "1px solid var(--border-color)",
            paddingTop: "1rem",
          }}
        >
          <div>
            <span style={{ color: "var(--text-muted)", display: "block" }}>Reporter</span>
            <strong style={{ color: "var(--text-primary)" }}>{incident.reporter.fullName}</strong>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
              {incident.reporter.email}
            </div>
          </div>

          <div>
            <span style={{ color: "var(--text-muted)", display: "block" }}>
              Assigned Specialist
            </span>
            {incident.assignee ? (
              <>
                <strong style={{ color: "var(--text-primary)" }}>
                  {incident.assignee.fullName}
                </strong>
                <div style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
                  {incident.assignee.email}
                </div>
              </>
            ) : (
              <span style={{ color: "var(--text-muted)", fontStyle: "italic" }}>Unassigned</span>
            )}
          </div>

          <div>
            <span style={{ color: "var(--text-muted)", display: "block" }}>
              SLA Target Resolution
            </span>
            <span
              style={{
                fontFamily: "var(--font-mono)",
                color: "var(--text-primary)",
                fontWeight: 600,
              }}
            >
              {incident.slaWindow?.resolutionDeadline
                ? new Date(incident.slaWindow.resolutionDeadline).toLocaleTimeString()
                : "N/A"}
            </span>
            {incident.slaWindow && incident.slaWindow.pausedSeconds > 0 && (
              <div style={{ fontSize: "0.75rem", color: "#7c3aed" }}>
                Paused {Math.round(incident.slaWindow.pausedSeconds / 60)}m in PENDING_INFO
              </div>
            )}
          </div>

          <div>
            <span style={{ color: "var(--text-muted)", display: "block" }}>Reported At</span>
            <span
              style={{
                fontFamily: "var(--font-mono)",
                color: "var(--text-secondary)",
              }}
            >
              {new Date(incident.createdAt).toLocaleString()}
            </span>
          </div>
        </div>
      </div>

      {/* State Actions: Lifecycle State Machine Transitions */}
      <StateActions
        incident={incident}
        currentUserRole={currentUserRole}
        isReporter={isReporter}
        onUpdate={(updated) => {
          setIncident(updated);
          loadData();
        }}
      />

      {/* Intelligent Duplicate & Similar Incident Detection */}
      <SimilarIncidentsPanel incidentId={incident.id} />

      {/* Diagnostic Discussion Thread */}
      <CommentThread
        incidentId={incident.id}
        initialComments={comments}
        currentUserRole={currentUserRole}
      />

      {/* Immutable Audit Ledger Timeline */}
      <AuditTimeline logs={auditLogs} />
    </div>
  );
}
