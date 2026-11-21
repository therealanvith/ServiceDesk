"use client";

import { showToast } from "@/components/LiveUpdates";
import { Modal, TEXT_DIALOGS, TransitionDialog } from "@/components/TransitionDialog";
import { type IncidentDetail, type Priority, type TechnicianItem, api } from "@/lib/apiClient";
import { CATEGORY_LABELS, PRIORITY_LABELS } from "@/lib/labels";
import { type ReactNode, useEffect, useState } from "react";

interface StateActionsProps {
  incident: IncidentDetail;
  currentUserRole: string;
  isReporter: boolean;
  isAssignee: boolean;
  onUpdate: (updated: IncidentDetail) => void;
  /**
   * Opens a dialog from elsewhere on the page (the pinned question's Reply, or the assistant's
   * "Use as resolution"); a new id re-opens it.
   */
  dialogRequest?: { id: number; kind: "provideInfo" | "resolve"; text?: string };
}

type DialogKind =
  | "assign"
  | "requestInfo"
  | "provideInfo"
  | "resolve"
  | "reopen"
  | "cancel"
  | "override"
  | "close";

/** Mirrors ClosedState.REOPEN_WINDOW on the server. */
const REOPEN_WINDOW_MS = 14 * 24 * 60 * 60 * 1000;
export function StateActions({
  incident,
  currentUserRole,
  isReporter,
  isAssignee,
  onUpdate,
  dialogRequest,
}: StateActionsProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<DialogKind | null>(null);

  const [text, setText] = useState("");
  const [rating, setRating] = useState(0);
  const [overridePriority, setOverridePriority] = useState<Priority>("P1_CRITICAL");
  const [selectedTechId, setSelectedTechId] = useState("");
  const [technicians, setTechnicians] = useState<TechnicianItem[]>([]);

  // Assigning is an administrator decision; technicians only see their own tickets.
  const isAdmin = currentUserRole === "ADMINISTRATOR" || currentUserRole === "ROOT";
  const isTechOrAdmin = isAdmin;
  // Mirrors the backend: only the assigned technician (or an admin) works the ticket.
  const canWork = isAdmin || (currentUserRole === "TECHNICIAN" && isAssignee);
  const status = incident.status;
  const terminal = status === "CLOSED" || status === "CANCELLED";

  const [techniciansFailed, setTechniciansFailed] = useState(false);
  const [technicianKey, setTechnicianKey] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: technicianKey re-runs the load for "Try again"
  useEffect(() => {
    if (dialog === "assign" && isTechOrAdmin) {
      setTechniciansFailed(false);
      api
        .listTechnicians()
        .then(setTechnicians)
        .catch(() => setTechniciansFailed(true));
    }
  }, [dialog, isTechOrAdmin, technicianKey]);

  const open = (kind: DialogKind) => {
    setText("");
    setDialog(kind);
  };

  useEffect(() => {
    if (dialogRequest) {
      setText(dialogRequest.text ?? "");
      setDialog(dialogRequest.kind);
    }
  }, [dialogRequest]);

  const handleAction = async (actionFn: () => Promise<IncidentDetail>, done: string) => {
    setLoading(true);
    setError(null);
    try {
      const updated = await actionFn();
      setDialog(null);
      onUpdate(updated);
      showToast({ title: done, tone: "success" });
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setLoading(false);
    }
  };

  const primary: ReactNode[] = [];
  const secondary: ReactNode[] = [];
  const btn = (key: string, label: string, onClick: () => void, variant = "") => (
    <button
      key={key}
      type="button"
      className={`btn btn-block ${variant}`}
      onClick={onClick}
      disabled={loading}
    >
      {label}
    </button>
  );

  if (status === "REPORTED" && isTechOrAdmin) {
    primary.push(btn("assign", "Assign", () => open("assign"), "btn-primary"));
  }
  if (status === "ASSIGNED" && canWork) {
    // Acknowledging meets the response target; starting straight away also counts.
    primary.push(
      btn(
        "acknowledge",
        "Acknowledge",
        () => handleAction(() => api.acknowledge(incident.id), "Ticket acknowledged"),
        "btn-primary"
      )
    );
    secondary.push(
      btn("start", "Start work", () =>
        handleAction(() => api.startWork(incident.id), "Work started")
      )
    );
  }
  if (status === "ACKNOWLEDGED" && canWork) {
    primary.push(
      btn(
        "start",
        "Start work",
        () => handleAction(() => api.startWork(incident.id), "Work started"),
        "btn-primary"
      )
    );
  }
  if (status === "IN_PROGRESS" && canWork) {
    primary.push(btn("resolve", "Resolve", () => open("resolve"), "btn-primary"));
    secondary.push(btn("request", "Ask reporter for details", () => open("requestInfo")));
  }
  if (status === "PENDING_INFO" && (isReporter || isAdmin)) {
    primary.push(btn("provide", "Reply with details", () => open("provideInfo"), "btn-primary"));
  }
  if (status === "RESOLVED" && (isReporter || isAdmin)) {
    primary.push(
      btn(
        "close",
        "Confirm fix and close",
        () => {
          setRating(0);
          open("close");
        },
        "btn-primary"
      )
    );
    secondary.push(btn("reopen", "It’s still not fixed", () => open("reopen")));
  }
  // A closed ticket can still be reopened for two weeks if the fix didn't hold.
  if (
    status === "CLOSED" &&
    (isReporter || isAdmin) &&
    incident.closedAt &&
    Date.now() - new Date(incident.closedAt).getTime() <= REOPEN_WINDOW_MS
  ) {
    secondary.push(btn("reopen", "It’s still not fixed", () => open("reopen")));
  }
  if (
    (status === "ASSIGNED" || status === "ACKNOWLEDGED" || status === "IN_PROGRESS") &&
    isTechOrAdmin
  ) {
    secondary.push(btn("reassign", "Reassign", () => open("assign")));
  }
  if (isAdmin && !terminal) {
    secondary.push(btn("override", "Change priority", () => open("override")));
  }
  if (
    (status === "REPORTED" && (isReporter || isAdmin)) ||
    ((status === "ASSIGNED" || status === "ACKNOWLEDGED") && isAdmin)
  ) {
    secondary.push(btn("cancel", "Cancel ticket", () => open("cancel"), "btn-danger"));
  }

  const textKind = dialog && dialog in TEXT_DIALOGS ? (dialog as keyof typeof TEXT_DIALOGS) : null;

  let note: string | null = null;
  if (terminal) {
    note = status === "CLOSED" ? "This ticket is closed." : "This ticket was cancelled.";
  } else if (primary.length + secondary.length === 0) {
    note =
      status === "RESOLVED"
        ? "Waiting for the reporter to confirm the fix."
        : status === "PENDING_INFO"
          ? "Waiting for the reporter to reply."
          : "Nothing for you to do here right now.";
  }

  return (
    <section className="card card-body" aria-label="Actions">
      {error && !dialog && (
        <p className="alert" role="alert" style={{ marginBottom: 10 }}>
          {error}
        </p>
      )}
      {note && <p className="actions-note">{note}</p>}
      {primary.length + secondary.length > 0 && (
        <div className="actions">
          {primary}
          {secondary}
        </div>
      )}

      {dialog === "close" && (
        <Modal
          error={dialog ? error : null}
          busy={loading}
          title="Confirm the fix"
          onClose={() => setDialog(null)}
        >
          <p>
            Closing confirms the problem is solved. If it comes back within 14 days, you can reopen
            the ticket.
          </p>
          {isReporter && (
            <fieldset className="field rating">
              <legend>How was the support? (optional)</legend>
              <div className="rating-options">
                {[1, 2, 3, 4, 5].map((n) => (
                  <label key={n} className={`rating-option ${rating === n ? "is-selected" : ""}`}>
                    <input
                      type="radio"
                      name="rating"
                      value={n}
                      checked={rating === n}
                      onChange={() => setRating(n)}
                    />
                    {n}
                  </label>
                ))}
              </div>
              <span className="field-hint">1 = poor, 5 = excellent</span>
            </fieldset>
          )}
          <label className="field">
            Comment <span className="field-hint">(optional)</span>
            <textarea
              rows={3}
              maxLength={2000}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </label>
          <div className="row-end">
            <button type="button" className="btn" onClick={() => setDialog(null)}>
              Back
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() =>
                handleAction(
                  () =>
                    api.closeIncident(incident.id, {
                      rating: isReporter && rating ? rating : undefined,
                      remarks: text.trim() || undefined,
                    }),
                  "Ticket closed"
                )
              }
            >
              Close ticket
            </button>
          </div>
        </Modal>
      )}

      {dialog === "assign" && (
        <Modal
          error={dialog ? error : null}
          busy={loading}
          title="Assign ticket"
          onClose={() => setDialog(null)}
        >
          {techniciansFailed && (
            <p className="alert" role="alert">
              Couldn’t load technicians, so only automatic assignment is available.{" "}
              <button
                type="button"
                className="link-btn"
                onClick={() => setTechnicianKey((k) => k + 1)}
              >
                Try again
              </button>
            </p>
          )}
          <label className="field">
            Technician
            <select value={selectedTechId} onChange={(e) => setSelectedTechId(e.target.value)}>
              <option value="">Pick automatically</option>
              {technicians.map((tech) => (
                <option key={tech.id} value={tech.id}>
                  {tech.fullName} — {tech.skills.map((s) => CATEGORY_LABELS[s] ?? s).join(", ")} ·{" "}
                  {tech.activeTicketsCount} open
                </option>
              ))}
            </select>
            <span className="field-hint">Automatic assignment uses the rule set in Settings.</span>
          </label>
          <div className="row-end">
            <button type="button" className="btn" onClick={() => setDialog(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={() =>
                handleAction(
                  () => api.assignIncident(incident.id, selectedTechId || undefined),
                  "Ticket assigned"
                )
              }
            >
              Assign
            </button>
          </div>
        </Modal>
      )}

      {dialog === "override" && (
        <Modal
          error={dialog ? error : null}
          busy={loading}
          title="Change priority"
          onClose={() => setDialog(null)}
        >
          <label className="field">
            New priority
            <select
              value={overridePriority}
              onChange={(e) => setOverridePriority(e.target.value as Priority)}
            >
              {Object.entries(PRIORITY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>
                  {value.slice(0, 2)} · {label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            Reason
            <textarea
              rows={3}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Why is the priority changing?"
            />
            <span className="field-hint">Saved to the ticket’s history.</span>
          </label>
          <div className="row-end">
            <button type="button" className="btn" onClick={() => setDialog(null)}>
              Cancel
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={!text.trim()}
              onClick={() =>
                handleAction(
                  () => api.overridePriority(incident.id, overridePriority, text.trim()),
                  "Priority changed"
                )
              }
            >
              Change priority
            </button>
          </div>
        </Modal>
      )}

      {textKind && (
        <TransitionDialog
          kind={textKind}
          text={text}
          onTextChange={setText}
          busy={loading}
          error={dialog ? error : null}
          onClose={() => setDialog(null)}
          onSubmit={(value) =>
            handleAction(
              () => TEXT_DIALOGS[textKind].submit(incident.id, value),
              TEXT_DIALOGS[textKind].done
            )
          }
        />
      )}
    </section>
  );
}
