"use client";

import { Icon } from "@/components/Icon";
import { type IncidentDetail, type Priority, type TechnicianItem, api } from "@/lib/apiClient";
import { CATEGORY_LABELS, PRIORITY_LABELS } from "@/lib/labels";
import { type ReactNode, useEffect, useState } from "react";

interface StateActionsProps {
  incident: IncidentDetail;
  currentUserRole: string;
  isReporter: boolean;
  isAssignee: boolean;
  onUpdate: (updated: IncidentDetail) => void;
}

type DialogKind =
  | "assign"
  | "requestInfo"
  | "provideInfo"
  | "resolve"
  | "cancel"
  | "override"
  | "close";

interface TextDialog {
  title: string;
  description: string;
  label: string;
  placeholder: string;
  confirm: string;
  danger?: boolean;
  submit: (incidentId: string, text: string) => Promise<IncidentDetail>;
}

const TEXT_DIALOGS: Record<"requestInfo" | "provideInfo" | "resolve" | "cancel", TextDialog> = {
  requestInfo: {
    title: "Ask the reporter for details",
    description: "The SLA clock pauses until they reply.",
    label: "Question",
    placeholder: "What do you need to know?",
    confirm: "Send question",
    submit: (id, text) => api.requestInfo(id, text),
  },
  provideInfo: {
    title: "Reply to the technician",
    description: "Work resumes as soon as you send this.",
    label: "Your reply",
    placeholder: "Answer the technician's question.",
    confirm: "Send reply",
    submit: (id, text) => api.provideInfo(id, text),
  },
  resolve: {
    title: "Resolve ticket",
    description: "The reporter will be asked to confirm the fix.",
    label: "What fixed it?",
    placeholder: "Describe the cause and the fix.",
    confirm: "Mark resolved",
    submit: (id, text) => api.resolveIncident(id, text),
  },
  cancel: {
    title: "Cancel ticket",
    description: "Cancelled tickets can’t be reopened.",
    label: "Reason",
    placeholder: "Why is this ticket being cancelled?",
    confirm: "Cancel ticket",
    danger: true,
    submit: (id, text) => api.cancelIncident(id, text),
  },
};

export function StateActions({
  incident,
  currentUserRole,
  isReporter,
  isAssignee,
  onUpdate,
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

  useEffect(() => {
    if (dialog === "assign" && isTechOrAdmin) {
      api
        .listTechnicians()
        .then(setTechnicians)
        .catch(() => {});
    }
  }, [dialog, isTechOrAdmin]);

  const open = (kind: DialogKind) => {
    setText("");
    setDialog(kind);
  };

  const handleAction = async (actionFn: () => Promise<IncidentDetail>) => {
    setDialog(null);
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
    primary.push(
      btn(
        "start",
        "Start work",
        () => handleAction(() => api.startWork(incident.id)),
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
  }
  if ((status === "ASSIGNED" || status === "IN_PROGRESS") && isTechOrAdmin) {
    secondary.push(btn("reassign", "Reassign", () => open("assign")));
  }
  if (isAdmin && !terminal) {
    secondary.push(btn("override", "Change priority", () => open("override")));
  }
  if ((status === "REPORTED" && (isReporter || isAdmin)) || (status === "ASSIGNED" && isAdmin)) {
    secondary.push(btn("cancel", "Cancel ticket", () => open("cancel"), "btn-danger"));
  }

  const textDialog =
    dialog && dialog in TEXT_DIALOGS ? TEXT_DIALOGS[dialog as keyof typeof TEXT_DIALOGS] : null;

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
      {error && (
        <p className="alert" role="alert" style={{ marginBottom: 10 }}>
          {error}
        </p>
      )}
      {note ? (
        <p className="actions-note">{note}</p>
      ) : (
        <div className="actions">
          {primary}
          {secondary}
        </div>
      )}

      {dialog === "close" && (
        <Modal title="Confirm the fix" onClose={() => setDialog(null)}>
          <p>Closing confirms the problem is solved. Tickets can’t be reopened after closing.</p>
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
                handleAction(() =>
                  api.closeIncident(incident.id, {
                    rating: isReporter && rating ? rating : undefined,
                    remarks: text.trim() || undefined,
                  })
                )
              }
            >
              Close ticket
            </button>
          </div>
        </Modal>
      )}

      {dialog === "assign" && (
        <Modal title="Assign ticket" onClose={() => setDialog(null)}>
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
                handleAction(() => api.assignIncident(incident.id, selectedTechId || undefined))
              }
            >
              Assign
            </button>
          </div>
        </Modal>
      )}

      {dialog === "override" && (
        <Modal title="Change priority" onClose={() => setDialog(null)}>
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
                handleAction(() => api.overridePriority(incident.id, overridePriority, text.trim()))
              }
            >
              Change priority
            </button>
          </div>
        </Modal>
      )}

      {textDialog && (
        <Modal title={textDialog.title} onClose={() => setDialog(null)}>
          <p>{textDialog.description}</p>
          <label className="field">
            {textDialog.label}
            <textarea
              rows={4}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={textDialog.placeholder}
            />
          </label>
          <div className="row-end">
            <button type="button" className="btn" onClick={() => setDialog(null)}>
              Back
            </button>
            <button
              type="button"
              className={`btn ${textDialog.danger ? "btn-danger" : "btn-primary"}`}
              disabled={!text.trim()}
              onClick={() => handleAction(() => textDialog.submit(incident.id, text.trim()))}
            >
              {textDialog.confirm}
            </button>
          </div>
        </Modal>
      )}
    </section>
  );
}

function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <dialog open className="modal" aria-modal="true" aria-labelledby="modal-title">
        <div className="modal-header">
          <h2 id="modal-title">{title}</h2>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">
            <Icon name="close" size={16} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </dialog>
    </div>
  );
}
