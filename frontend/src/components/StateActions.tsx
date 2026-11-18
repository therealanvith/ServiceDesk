"use client";

import {
  type IncidentDetail,
  type Priority,
  type TechnicianItem,
  api,
} from "@/lib/apiClient";
import { useEffect, useState } from "react";

interface StateActionsProps {
  incident: IncidentDetail;
  currentUserRole: string;
  isReporter: boolean;
  onUpdate: (updated: IncidentDetail) => void;
}

export function StateActions({
  incident,
  currentUserRole,
  isReporter,
  onUpdate,
}: StateActionsProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Dialog state
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [showRequestInfoModal, setShowRequestInfoModal] = useState(false);
  const [showProvideInfoModal, setShowProvideInfoModal] = useState(false);
  const [showResolveModal, setShowResolveModal] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [showOverrideModal, setShowOverrideModal] = useState(false);

  // Form fields
  const [questionText, setQuestionText] = useState("");
  const [answerText, setAnswerText] = useState("");
  const [resolutionNotes, setResolutionNotes] = useState("");
  const [cancelReason, setCancelReason] = useState("");
  const [overridePriority, setOverridePriority] = useState<Priority>("P1_CRITICAL");
  const [overrideRemarks, setOverrideRemarks] = useState("");
  const [selectedTechId, setSelectedTechId] = useState("");
  const [technicians, setTechnicians] = useState<TechnicianItem[]>([]);

  const isTechOrAdmin =
    currentUserRole === "TECHNICIAN" || currentUserRole === "ADMINISTRATOR";
  const isAdmin = currentUserRole === "ADMINISTRATOR";

  useEffect(() => {
    if (showAssignModal && isTechOrAdmin) {
      api.listTechnicians().then(setTechnicians).catch(() => {});
    }
  }, [showAssignModal, isTechOrAdmin]);

  const handleAction = async (actionFn: () => Promise<IncidentDetail>) => {
    setLoading(true);
    setError(null);
    try {
      const updated = await actionFn();
      onUpdate(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-color)",
        borderRadius: "6px",
        padding: "1.25rem",
        marginBottom: "1.5rem",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "1rem",
        }}
      >
        <h3
          style={{
            fontSize: "0.9375rem",
            fontWeight: 600,
            color: "var(--text-primary)",
          }}
        >
          Lifecycle Actions (State Pattern)
        </h3>
        <span
          style={{
            fontSize: "0.75rem",
            color: "var(--text-muted)",
          }}
        >
          Current State: <strong>{incident.status}</strong>
        </span>
      </div>

      {error && (
        <div
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            padding: "0.5rem 0.75rem",
            borderRadius: "4px",
            fontSize: "0.8125rem",
            marginBottom: "1rem",
            border: "1px solid #fecaca",
          }}
        >
          {error}
        </div>
      )}

      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
        {/* REPORTED STATE ACTIONS */}
        {incident.status === "REPORTED" && (
          <>
            {isTechOrAdmin && (
              <button
                type="button"
                onClick={() => setShowAssignModal(true)}
                disabled={loading}
                style={primaryBtnStyle}
              >
                Assign Technician
              </button>
            )}
            {(isReporter || isAdmin) && (
              <button
                type="button"
                onClick={() => setShowCancelModal(true)}
                disabled={loading}
                style={dangerBtnStyle}
              >
                Cancel Incident
              </button>
            )}
          </>
        )}

        {/* ASSIGNED STATE ACTIONS */}
        {incident.status === "ASSIGNED" && (
          <>
            {isTechOrAdmin && (
              <>
                <button
                  type="button"
                  onClick={() => handleAction(() => api.startWork(incident.id))}
                  disabled={loading}
                  style={primaryBtnStyle}
                >
                  Start Work (IN_PROGRESS)
                </button>
                <button
                  type="button"
                  onClick={() => setShowAssignModal(true)}
                  disabled={loading}
                  style={secondaryBtnStyle}
                >
                  Reassign
                </button>
              </>
            )}
            {isAdmin && (
              <button
                type="button"
                onClick={() => setShowCancelModal(true)}
                disabled={loading}
                style={dangerBtnStyle}
              >
                Cancel Incident
              </button>
            )}
          </>
        )}

        {/* IN_PROGRESS STATE ACTIONS */}
        {incident.status === "IN_PROGRESS" && (
          <>
            {isTechOrAdmin && (
              <>
                <button
                  type="button"
                  onClick={() => setShowRequestInfoModal(true)}
                  disabled={loading}
                  style={warningBtnStyle}
                >
                  Request Information (Pause SLA)
                </button>
                <button
                  type="button"
                  onClick={() => setShowResolveModal(true)}
                  disabled={loading}
                  style={successBtnStyle}
                >
                  Resolve Incident
                </button>
                <button
                  type="button"
                  onClick={() => setShowAssignModal(true)}
                  disabled={loading}
                  style={secondaryBtnStyle}
                >
                  Reassign
                </button>
              </>
            )}
          </>
        )}

        {/* PENDING_INFO STATE ACTIONS */}
        {incident.status === "PENDING_INFO" && (
          <>
            <button
              type="button"
              onClick={() => setShowProvideInfoModal(true)}
              disabled={loading}
              style={successBtnStyle}
            >
              Provide Information (Resume SLA)
            </button>
          </>
        )}

        {/* RESOLVED STATE ACTIONS */}
        {incident.status === "RESOLVED" && (
          <>
            {(isReporter || isAdmin) && (
              <button
                type="button"
                onClick={() => handleAction(() => api.closeIncident(incident.id))}
                disabled={loading}
                style={successBtnStyle}
              >
                Confirm Resolution & Close
              </button>
            )}
          </>
        )}

        {/* TERMINAL STATES */}
        {(incident.status === "CLOSED" || incident.status === "CANCELLED") && (
          <span
            style={{
              fontSize: "0.8125rem",
              color: "var(--text-muted)",
              fontStyle: "italic",
            }}
          >
            Ticket is in terminal state ({incident.status}). Invariants prevent further mutations.
          </span>
        )}

        {/* ADMIN PRIORITY OVERRIDE */}
        {isAdmin &&
          incident.status !== "CLOSED" &&
          incident.status !== "CANCELLED" && (
            <button
              type="button"
              onClick={() => setShowOverrideModal(true)}
              disabled={loading}
              style={{ ...secondaryBtnStyle, marginLeft: "auto" }}
            >
              Override Priority
            </button>
          )}
      </div>

      {/* MODAL: ASSIGN */}
      {showAssignModal && (
        <Modal title="Assign Incident" onClose={() => setShowAssignModal(false)}>
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
              Select a technician or leave empty to trigger automated assignment strategy.
            </p>
            <select
              value={selectedTechId}
              onChange={(e) => setSelectedTechId(e.target.value)}
              style={inputStyle}
            >
              <option value="">Automated Strategy Assignment</option>
              {technicians.map((tech) => (
                <option key={tech.id} value={tech.id}>
                  {tech.fullName} ({tech.skills.join(", ")}) — {tech.activeTicketsCount} active
                </option>
              ))}
            </select>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowAssignModal(false)}
                style={secondaryBtnStyle}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowAssignModal(false);
                  handleAction(() =>
                    api.assignIncident(
                      incident.id,
                      selectedTechId || undefined
                    )
                  );
                }}
                style={primaryBtnStyle}
              >
                Confirm Assignment
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: REQUEST INFO */}
      {showRequestInfoModal && (
        <Modal
          title="Request Information (Pauses SLA)"
          onClose={() => setShowRequestInfoModal(false)}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
              Ask the reporter for clarification. The SLA resolution countdown will pause until they respond.
            </p>
            <textarea
              rows={3}
              value={questionText}
              onChange={(e) => setQuestionText(e.target.value)}
              placeholder="e.g., Which room port or switch are you connected to?"
              style={inputStyle}
            />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowRequestInfoModal(false)}
                style={secondaryBtnStyle}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!questionText.trim()}
                onClick={() => {
                  setShowRequestInfoModal(false);
                  handleAction(() =>
                    api.requestInfo(incident.id, questionText.trim())
                  );
                }}
                style={warningBtnStyle}
              >
                Send Request
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: PROVIDE INFO */}
      {showProvideInfoModal && (
        <Modal
          title="Provide Information (Resumes SLA)"
          onClose={() => setShowProvideInfoModal(false)}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
              Enter the requested details. Work status will return to IN_PROGRESS and SLA timers will resume.
            </p>
            <textarea
              rows={3}
              value={answerText}
              onChange={(e) => setAnswerText(e.target.value)}
              placeholder="e.g., Cable verified and re-seated; port LED is solid green."
              style={inputStyle}
            />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowProvideInfoModal(false)}
                style={secondaryBtnStyle}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!answerText.trim()}
                onClick={() => {
                  setShowProvideInfoModal(false);
                  handleAction(() =>
                    api.provideInfo(incident.id, answerText.trim())
                  );
                }}
                style={successBtnStyle}
              >
                Submit Response
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: RESOLVE */}
      {showResolveModal && (
        <Modal title="Resolve Incident" onClose={() => setShowResolveModal(false)}>
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
              Summarize the diagnostic resolution and steps taken to remediate the issue.
            </p>
            <textarea
              rows={3}
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              placeholder="e.g., Replaced faulty Cat6 patch cable and rebooted local switch SW-C304."
              style={inputStyle}
            />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowResolveModal(false)}
                style={secondaryBtnStyle}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!resolutionNotes.trim()}
                onClick={() => {
                  setShowResolveModal(false);
                  handleAction(() =>
                    api.resolveIncident(incident.id, resolutionNotes.trim())
                  );
                }}
                style={successBtnStyle}
              >
                Mark Resolved
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: CANCEL */}
      {showCancelModal && (
        <Modal title="Cancel Incident" onClose={() => setShowCancelModal(false)}>
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
              Provide the cancellation justification. This transition is immutable.
            </p>
            <textarea
              rows={3}
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="e.g., Duplicate ticket reported earlier."
              style={inputStyle}
            />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowCancelModal(false)}
                style={secondaryBtnStyle}
              >
                Close
              </button>
              <button
                type="button"
                disabled={!cancelReason.trim()}
                onClick={() => {
                  setShowCancelModal(false);
                  handleAction(() =>
                    api.cancelIncident(incident.id, cancelReason.trim())
                  );
                }}
                style={dangerBtnStyle}
              >
                Confirm Cancel
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* MODAL: OVERRIDE PRIORITY */}
      {showOverrideModal && (
        <Modal title="Administrator Priority Override" onClose={() => setShowOverrideModal(false)}>
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            <div>
              <label
                htmlFor="override-priority-select"
                style={{
                  display: "block",
                  fontSize: "0.8125rem",
                  fontWeight: 600,
                  marginBottom: "0.25rem",
                }}
              >
                New Priority Tier
              </label>
              <select
                id="override-priority-select"
                value={overridePriority}
                onChange={(e) => setOverridePriority(e.target.value as Priority)}
                style={inputStyle}
              >
                <option value="P1_CRITICAL">P1 Critical</option>
                <option value="P2_HIGH">P2 High</option>
                <option value="P3_MEDIUM">P3 Medium</option>
                <option value="P4_LOW">P4 Low</option>
              </select>
            </div>
            <div>
              <label
                htmlFor="override-remarks"
                style={{
                  display: "block",
                  fontSize: "0.8125rem",
                  fontWeight: 600,
                  marginBottom: "0.25rem",
                }}
              >
                Justification Remarks (Recorded in Audit Ledger)
              </label>
              <textarea
                id="override-remarks"
                rows={3}
                value={overrideRemarks}
                onChange={(e) => setOverrideRemarks(e.target.value)}
                placeholder="e.g., Dean office request; escalating priority to P1."
                style={inputStyle}
              />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: "0.5rem" }}>
              <button
                type="button"
                onClick={() => setShowOverrideModal(false)}
                style={secondaryBtnStyle}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={!overrideRemarks.trim()}
                onClick={() => {
                  setShowOverrideModal(false);
                  handleAction(() =>
                    api.overridePriority(
                      incident.id,
                      overridePriority,
                      overrideRemarks.trim()
                    )
                  );
                }}
                style={primaryBtnStyle}
              >
                Apply Override
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(15, 23, 42, 0.45)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 50,
        padding: "1rem",
      }}
    >
      <div
        style={{
          backgroundColor: "var(--bg-surface)",
          borderRadius: "8px",
          width: "100%",
          maxWidth: "480px",
          padding: "1.5rem",
          boxShadow: "0 10px 25px -5px rgba(0, 0, 0, 0.1)",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "1rem",
          }}
        >
          <h4 style={{ fontSize: "1rem", fontWeight: 600 }}>{title}</h4>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              fontSize: "1.25rem",
              cursor: "pointer",
              color: "var(--text-muted)",
            }}
          >
            &times;
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: "100%",
  padding: "0.5rem 0.75rem",
  borderRadius: "4px",
  border: "1px solid var(--border-color)",
  fontSize: "0.875rem",
  fontFamily: "inherit",
  backgroundColor: "var(--bg-primary)",
  color: "var(--text-primary)",
};

const baseBtnStyle: React.CSSProperties = {
  padding: "0.45rem 0.85rem",
  borderRadius: "4px",
  fontSize: "0.8125rem",
  fontWeight: 600,
  cursor: "pointer",
  border: "1px solid transparent",
};

const primaryBtnStyle: React.CSSProperties = {
  ...baseBtnStyle,
  backgroundColor: "var(--accent)",
  color: "white",
};

const secondaryBtnStyle: React.CSSProperties = {
  ...baseBtnStyle,
  backgroundColor: "var(--bg-primary)",
  borderColor: "var(--border-color)",
  color: "var(--text-primary)",
};

const warningBtnStyle: React.CSSProperties = {
  ...baseBtnStyle,
  backgroundColor: "#fef3c7",
  borderColor: "#fde68a",
  color: "#92400e",
};

const successBtnStyle: React.CSSProperties = {
  ...baseBtnStyle,
  backgroundColor: "#d1fae5",
  borderColor: "#a7f3d0",
  color: "#065f46",
};

const dangerBtnStyle: React.CSSProperties = {
  ...baseBtnStyle,
  backgroundColor: "#fee2e2",
  borderColor: "#fecaca",
  color: "#991b1b",
};
