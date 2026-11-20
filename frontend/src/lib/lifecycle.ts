import { type IncidentDetail, type IncidentStatus, type Role, api } from "@/lib/apiClient";
import { isAdmin } from "@/lib/labels";

/**
 * The ticket lifecycle as the board sees it. Mirrors the backend state machine
 * (domain/incident/state): the server still decides, this only keeps the UI from offering moves
 * it would refuse.
 */

/** Moves that need a sentence from the person making them open a dialog first. */
export type TextMove = "requestInfo" | "provideInfo" | "resolve";
export type MoveAction = "acknowledge" | "start" | TextMove;

/** Who may make a move: the person working the ticket, or the person who reported it. */
type Actor = "worker" | "reporter";

export interface Move {
  action: MoveAction;
  to: IncidentStatus;
  label: string;
  /** Set when the move needs text (the question, or the resolution summary). */
  text?: TextMove;
  actor: Actor;
}

const TRANSITIONS: Record<IncidentStatus, Move[]> = {
  REPORTED: [],
  ASSIGNED: [
    { action: "acknowledge", to: "ACKNOWLEDGED", label: "Acknowledge", actor: "worker" },
    // Starting straight away also meets the response target.
    { action: "start", to: "IN_PROGRESS", label: "Start work", actor: "worker" },
  ],
  ACKNOWLEDGED: [{ action: "start", to: "IN_PROGRESS", label: "Start work", actor: "worker" }],
  IN_PROGRESS: [
    {
      action: "requestInfo",
      to: "PENDING_INFO",
      label: "Ask reporter for details",
      text: "requestInfo",
      actor: "worker",
    },
    { action: "resolve", to: "RESOLVED", label: "Resolve", text: "resolve", actor: "worker" },
  ],
  PENDING_INFO: [
    {
      action: "provideInfo",
      to: "IN_PROGRESS",
      label: "Reply with details",
      text: "provideInfo",
      actor: "reporter",
    },
  ],
  // Closing is the reporter's confirmation and cancelling needs a reason: both stay on the ticket.
  RESOLVED: [],
  CLOSED: [],
  CANCELLED: [],
};

/**
 * The moves this person may make from `status`. Only the assigned technician (or an admin) works
 * a ticket; replies to a question come from the reporter (or an admin).
 */
export function allowedMoves(
  status: IncidentStatus,
  role: Role | undefined,
  isAssignee: boolean,
  isReporter = false
): Move[] {
  const admin = isAdmin(role);
  const canWork = admin || (role === "TECHNICIAN" && isAssignee);
  const canReply = admin || isReporter;
  return TRANSITIONS[status].filter((move) => (move.actor === "worker" ? canWork : canReply));
}

/** The move that takes a ticket from `from` to `to`, if this person may make it. */
export function findMove(
  from: IncidentStatus,
  to: IncidentStatus,
  role: Role | undefined,
  isAssignee: boolean,
  isReporter = false
): Move | undefined {
  return allowedMoves(from, role, isAssignee, isReporter).find((move) => move.to === to);
}

/** Calls the endpoint behind a move; text moves pass the dialog's text. */
export function performMove(
  incidentId: string,
  action: MoveAction,
  text = ""
): Promise<IncidentDetail> {
  switch (action) {
    case "acknowledge":
      return api.acknowledge(incidentId);
    case "start":
      return api.startWork(incidentId);
    case "requestInfo":
      return api.requestInfo(incidentId, text);
    case "provideInfo":
      return api.provideInfo(incidentId, text);
    case "resolve":
      return api.resolveIncident(incidentId, text);
  }
}
