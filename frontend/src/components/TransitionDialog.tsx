"use client";

import { Icon } from "@/components/Icon";
import { type IncidentDetail, api } from "@/lib/apiClient";
import { type ReactNode, useEffect, useRef } from "react";

/** Lifecycle moves that need a sentence before they go through. */
export type TextDialogKind = "requestInfo" | "provideInfo" | "resolve" | "reopen" | "cancel";

export interface TextDialog {
  title: string;
  description: string;
  label: string;
  placeholder: string;
  confirm: string;
  /** Toast title once the move has gone through. */
  done: string;
  danger?: boolean;
  submit: (incidentId: string, text: string) => Promise<IncidentDetail>;
}

export const TEXT_DIALOGS: Record<TextDialogKind, TextDialog> = {
  requestInfo: {
    title: "Ask the reporter for details",
    description: "The SLA clock pauses until they reply.",
    label: "Question",
    placeholder: "What do you need to know?",
    confirm: "Send question",
    done: "Question sent",
    submit: (id, text) => api.requestInfo(id, text),
  },
  provideInfo: {
    title: "Reply to the technician",
    description: "Work resumes as soon as you send this.",
    label: "Your reply",
    placeholder: "Answer the technician's question.",
    confirm: "Send reply",
    done: "Reply sent",
    submit: (id, text) => api.provideInfo(id, text),
  },
  resolve: {
    title: "Resolve ticket",
    description: "The reporter will be asked to confirm the fix.",
    label: "What fixed it?",
    placeholder: "Describe the cause and the fix.",
    confirm: "Mark resolved",
    done: "Ticket resolved",
    submit: (id, text) => api.resolveIncident(id, text),
  },
  reopen: {
    title: "Still not fixed?",
    description: "The ticket goes back to the technician, who sees what you write here.",
    label: "What is still wrong?",
    placeholder: "For example: it worked for a day, then started dropping again.",
    confirm: "Reopen ticket",
    done: "Ticket reopened",
    submit: (id, text) => api.reopenIncident(id, text),
  },
  cancel: {
    title: "Cancel ticket",
    description: "Cancelled tickets can’t be reopened.",
    label: "Reason",
    placeholder: "Why is this ticket being cancelled?",
    confirm: "Cancel ticket",
    done: "Ticket cancelled",
    danger: true,
    submit: (id, text) => api.cancelIncident(id, text),
  },
};

/**
 * The dialog for a text move (ask, reply, resolve, cancel). The caller owns the text, the busy
 * flag and the error, so the ticket page and the board can each run the request their own way.
 */
export function TransitionDialog({
  kind,
  text,
  onTextChange,
  onSubmit,
  onClose,
  busy,
  error,
}: {
  kind: TextDialogKind;
  text: string;
  onTextChange: (text: string) => void;
  /** Called with the trimmed text. */
  onSubmit: (text: string) => void;
  onClose: () => void;
  busy?: boolean;
  error?: string | null;
}) {
  const dialog = TEXT_DIALOGS[kind];
  return (
    <Modal error={error} busy={busy} title={dialog.title} onClose={onClose}>
      <p>{dialog.description}</p>
      <label className="field">
        {dialog.label}
        <textarea
          rows={4}
          value={text}
          onChange={(e) => onTextChange(e.target.value)}
          placeholder={dialog.placeholder}
        />
      </label>
      <div className="row-end">
        <button type="button" className="btn" onClick={onClose}>
          Back
        </button>
        <button
          type="button"
          className={`btn ${dialog.danger ? "btn-danger" : "btn-primary"}`}
          disabled={!text.trim()}
          onClick={() => onSubmit(text.trim())}
        >
          {dialog.confirm}
        </button>
      </div>
    </Modal>
  );
}

export function Modal({
  title,
  onClose,
  children,
  error,
  busy,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  error?: string | null;
  busy?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const opener = document.activeElement as HTMLElement | null;
    // showModal() makes the rest of the page inert and traps focus inside the dialog.
    if (!dialog.open) dialog.showModal();
    dialog.querySelector<HTMLElement>("textarea, select, input, button:not(.icon-btn)")?.focus();
    const onCancel = (e: Event) => {
      e.preventDefault();
      closeRef.current();
    };
    dialog.addEventListener("cancel", onCancel);
    return () => {
      dialog.removeEventListener("cancel", onCancel);
      if (dialog.open) dialog.close();
      // The opener may have re-mounted elsewhere (a board card that moved); then its page decides.
      if (opener?.isConnected) opener.focus();
    };
  }, []);

  return (
    <dialog
      ref={ref}
      className="modal"
      aria-labelledby="modal-title"
      aria-busy={busy || undefined}
      onMouseDown={(e) => {
        // A click on the backdrop (outside the dialog box) closes it.
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <div className="modal-header">
        <h2 id="modal-title">{title}</h2>
        <button
          type="button"
          className="icon-btn"
          onClick={onClose}
          aria-label="Close"
          disabled={busy}
        >
          <Icon name="close" size={16} />
        </button>
      </div>
      <div className="modal-body">
        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}
        <fieldset className="modal-fields" disabled={busy}>
          {children}
        </fieldset>
      </div>
    </dialog>
  );
}
