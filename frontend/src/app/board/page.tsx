"use client";

import { Icon } from "@/components/Icon";
import { showToast } from "@/components/LiveUpdates";
import { PriorityBadge } from "@/components/PriorityBadge";
import { SkeletonBlock } from "@/components/Skeleton";
import { SlaBadge } from "@/components/SlaBadge";
import { TEXT_DIALOGS, type TextDialogKind, TransitionDialog } from "@/components/TransitionDialog";
import { useAuth } from "@/context/AuthContext";
import { type IncidentStatus, type IncidentSummary, api } from "@/lib/apiClient";
import { STATUS_LABELS, isAdmin } from "@/lib/labels";
import { type Move, allowedMoves, findMove, performMove } from "@/lib/lifecycle";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

const COLUMNS = ["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS", "PENDING_INFO", "RESOLVED"] as const;
type ColumnStatus = (typeof COLUMNS)[number];
type Board = Record<ColumnStatus, IncidentSummary[]>;

const RESOLVED_WINDOW_MS = 7 * 24 * 60 * 60 * 1000;
// The list endpoint caps a page at 100; a board column holding more than that needs the list view.
const COLUMN_SIZE = 100;

/** Resolved tickets stay on the board for a week; older ones only show in the list. */
function recentlyResolved(ticket: IncidentSummary): boolean {
  if (!ticket.resolvedAt) return true;
  return Date.now() - new Date(ticket.resolvedAt).getTime() <= RESOLVED_WINDOW_MS;
}

interface PendingText {
  ticket: IncidentSummary;
  kind: TextDialogKind;
  to: IncidentStatus;
  /** Started from the keyboard menu, so focus follows the card to its new column. */
  viaMenu: boolean;
}

export default function BoardPage() {
  const { user, loading: authLoading } = useAuth();
  const [board, setBoard] = useState<Board | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [moveError, setMoveError] = useState<{ id: string; message: string } | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [dragging, setDragging] = useState<IncidentSummary | null>(null);
  const [over, setOver] = useState<ColumnStatus | null>(null);
  const [pending, setPending] = useState<PendingText | null>(null);
  // The card whose Move button gets focus once it has re-mounted in its new column.
  const [focusTicketId, setFocusTicketId] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [dialogBusy, setDialogBusy] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const loadVersion = useRef(0);

  const role = user?.role;
  const admin = isAdmin(role);
  const staff = Boolean(user && role !== "EMPLOYEE");

  const load = useCallback(async () => {
    if (!user || user.role === "EMPLOYEE") return;
    const version = ++loadVersion.current;
    try {
      // One request per column: a single unfiltered list would fill up with closed tickets.
      const pages = await Promise.all(
        COLUMNS.map((status) => api.listIncidents({ status, size: COLUMN_SIZE }))
      );
      if (version !== loadVersion.current) return;
      const next = {} as Board;
      COLUMNS.forEach((status, i) => {
        next[status] = (pages[i]?.content ?? []).filter(
          (ticket) =>
            // Technicians also see tickets they reported; the board is only the ones they work.
            (isAdmin(user.role) || ticket.assigneeId === user.id) &&
            (status !== "RESOLVED" || recentlyResolved(ticket))
        );
      });
      setBoard(next);
      setLoadError(null);
    } catch (err: unknown) {
      if (version !== loadVersion.current) return;
      setLoadError(err instanceof Error ? err.message : "Unable to load the board");
    }
  }, [user]);

  useEffect(() => {
    void load();
  }, [load]);

  // Someone else moved a ticket: refresh quietly.
  useLiveEvent((e) => {
    if (e.event === "incident-updated") void load();
  });

  // Every card here is the viewer's own (technicians) or one an admin may work.
  const movesFor = (ticket: IncidentSummary) => allowedMoves(ticket.status, role, true);

  const finish = (ticket: IncidentSummary, to: IncidentStatus, done: string, viaMenu: boolean) => {
    if (viaMenu) setFocusTicketId(ticket.id);
    showToast({
      title: done,
      message: `${ticket.incidentNumber} · ${ticket.title}`,
      tone: "success",
    });
    // Show the card in its new column straight away; the reload confirms it.
    setBoard((current) => {
      if (!current) return current;
      const next = { ...current };
      for (const status of COLUMNS) next[status] = next[status].filter((t) => t.id !== ticket.id);
      if ((COLUMNS as readonly string[]).includes(to)) {
        next[to as ColumnStatus] = [{ ...ticket, status: to }, ...next[to as ColumnStatus]];
      }
      return next;
    });
    void load();
  };

  const startMove = async (ticket: IncidentSummary, move: Move, viaMenu = false) => {
    setMoveError(null);
    if (move.text) {
      setText("");
      setDialogError(null);
      setPending({ ticket, kind: move.text, to: move.to, viaMenu });
      return;
    }
    setBusyId(ticket.id);
    try {
      await performMove(ticket.id, move.action);
      finish(ticket, move.to, `Moved to ${STATUS_LABELS[move.to].toLowerCase()}`, viaMenu);
    } catch (err: unknown) {
      setMoveError({
        id: ticket.id,
        message: err instanceof Error ? err.message : "Couldn’t move this ticket",
      });
    } finally {
      setBusyId(null);
    }
  };

  const submitText = async (value: string) => {
    if (!pending) return;
    setDialogBusy(true);
    setDialogError(null);
    try {
      await TEXT_DIALOGS[pending.kind].submit(pending.ticket.id, value);
      finish(pending.ticket, pending.to, TEXT_DIALOGS[pending.kind].done, pending.viaMenu);
      setPending(null);
    } catch (err: unknown) {
      setDialogError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setDialogBusy(false);
    }
  };

  const dropTarget = (column: ColumnStatus) =>
    dragging ? findMove(dragging.status, column, role, true) : undefined;

  if (authLoading || !user) {
    return <SkeletonBlock label="Loading the board…" variant="board" />;
  }

  if (!staff) {
    return (
      <div className="card empty">
        <h2>The board is for technicians and administrators</h2>
        <p>Your tickets and where they stand are on your ticket list.</p>
        <Link className="btn" href="/">
          Go to my tickets
        </Link>
      </div>
    );
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Board</h1>
          <p>
            {admin
              ? "Every open ticket by stage. Drag a card, or use Move to…, to change its status."
              : "Your tickets by stage. Drag a card, or use Move to…, to change its status."}
          </p>
        </div>
        <button
          type="button"
          className="icon-btn"
          onClick={() => void load()}
          aria-label="Refresh"
          title="Refresh"
        >
          <Icon name="refresh" size={15} />
        </button>
      </div>

      {loadError && (
        <p className="alert" role="alert" style={{ marginBottom: 16 }}>
          Couldn’t load the board: {loadError}{" "}
          <button type="button" className="link-btn" onClick={() => void load()}>
            Try again
          </button>
        </p>
      )}

      {!board ? (
        !loadError && <SkeletonBlock label="Loading the board…" variant="board" />
      ) : (
        <div className={`board ${dragging ? "is-dragging" : ""}`}>
          {COLUMNS.map((column) => {
            const target = dropTarget(column);
            const tickets = board[column];
            return (
              <section
                key={column}
                className={[
                  "board-column",
                  target ? "is-target" : "",
                  target && over === column ? "is-over" : "",
                ].join(" ")}
                aria-labelledby={`col-${column}`}
                onDragOver={(e) => {
                  if (!target) return;
                  e.preventDefault();
                  e.dataTransfer.dropEffect = "move";
                  if (over !== column) setOver(column);
                }}
                onDragLeave={(e) => {
                  if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setOver(null);
                }}
                onDrop={(e) => {
                  e.preventDefault();
                  const ticket = dragging;
                  setDragging(null);
                  setOver(null);
                  if (ticket && target) void startMove(ticket, target);
                }}
              >
                <header className="board-column-header">
                  <h2 id={`col-${column}`}>{STATUS_LABELS[column]}</h2>
                  <span className="count">{tickets.length}</span>
                  {column === "RESOLVED" && <span className="field-hint">Last 7 days</span>}
                </header>
                {tickets.length === 0 ? (
                  <p className="board-empty">Nothing here</p>
                ) : (
                  <ul className="board-cards">
                    {tickets.map((ticket) => (
                      <BoardCard
                        key={ticket.id}
                        ticket={ticket}
                        showAssignee={admin}
                        moves={movesFor(ticket)}
                        busy={busyId === ticket.id}
                        dragged={dragging?.id === ticket.id}
                        error={moveError?.id === ticket.id ? moveError.message : null}
                        onMove={(move) => void startMove(ticket, move, true)}
                        focusMove={focusTicketId === ticket.id}
                        onFocused={() => setFocusTicketId(null)}
                        onDragStart={() => {
                          setMoveError(null);
                          setDragging(ticket);
                        }}
                        onDragEnd={() => {
                          setDragging(null);
                          setOver(null);
                        }}
                      />
                    ))}
                  </ul>
                )}
              </section>
            );
          })}
        </div>
      )}

      {pending && (
        <TransitionDialog
          kind={pending.kind}
          text={text}
          onTextChange={setText}
          busy={dialogBusy}
          error={dialogError}
          onClose={() => setPending(null)}
          onSubmit={(value) => void submitText(value)}
        />
      )}
    </>
  );
}

function BoardCard({
  ticket,
  showAssignee,
  moves,
  busy,
  dragged,
  error,
  onMove,
  focusMove,
  onFocused,
  onDragStart,
  onDragEnd,
}: {
  ticket: IncidentSummary;
  showAssignee: boolean;
  moves: Move[];
  busy: boolean;
  dragged: boolean;
  error: string | null;
  onMove: (move: Move) => void;
  focusMove: boolean;
  onFocused: () => void;
  onDragStart: () => void;
  onDragEnd: () => void;
}) {
  const draggable = moves.length > 0 && !busy;
  return (
    <li
      className={`board-card ${dragged ? "is-dragged" : ""} ${draggable ? "is-draggable" : ""}`}
      draggable={draggable}
      aria-busy={busy || undefined}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", ticket.incidentNumber);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
    >
      <div className="board-card-top">
        <span className="mono muted">{ticket.incidentNumber}</span>
        <PriorityBadge priority={ticket.priority} compact />
      </div>
      <Link
        className="title-link board-card-title"
        href={`/incidents/${ticket.id}`}
        draggable={false}
      >
        {ticket.title}
      </Link>
      <div className="board-card-meta">
        <SlaBadge status={ticket.slaStatus} isPaused={ticket.status === "PENDING_INFO"} />
        {showAssignee && (
          <span className="muted board-card-assignee">
            {ticket.assigneeName === "Unassigned" ? (
              <span className="unassigned">Unassigned</span>
            ) : (
              ticket.assigneeName
            )}
          </span>
        )}
      </div>
      {error && (
        <p className="alert board-card-error" role="alert">
          {error}
        </p>
      )}
      {moves.length > 0 ? (
        <MoveMenu
          ticket={ticket}
          moves={moves}
          busy={busy}
          onMove={onMove}
          focusMove={focusMove}
          onFocused={onFocused}
        />
      ) : (
        <p className="field-hint">
          {ticket.status === "RESOLVED"
            ? "Waiting for the reporter to confirm."
            : ticket.status === "PENDING_INFO"
              ? "Waiting for the reporter to reply."
              : ""}
        </p>
      )}
    </li>
  );
}

/** The keyboard (and touch) way to move a card: a button that opens the valid targets. */
function MoveMenu({
  ticket,
  moves,
  busy,
  onMove,
  focusMove,
  onFocused,
}: {
  ticket: IncidentSummary;
  moves: Move[];
  busy: boolean;
  onMove: (move: Move) => void;
  focusMove: boolean;
  onFocused: () => void;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuId = `move-${ticket.id}`;

  useEffect(() => {
    if (!open) return;
    wrapRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();
    const onPointer = (e: PointerEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    return () => document.removeEventListener("pointerdown", onPointer);
  }, [open]);

  // After a keyboard move the card lives in another column; put focus back on its button there.
  useEffect(() => {
    if (focusMove && !busy) {
      buttonRef.current?.focus();
      onFocused();
    }
  }, [focusMove, busy, onFocused]);

  const close = () => {
    setOpen(false);
    buttonRef.current?.focus();
  };

  return (
    <div
      className="move-menu"
      ref={wrapRef}
      onKeyDown={(e) => {
        if (!open) return;
        const items = Array.from(
          wrapRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []
        );
        const index = items.indexOf(document.activeElement as HTMLElement);
        if (e.key === "Escape") {
          e.preventDefault();
          e.stopPropagation();
          close();
        } else if (e.key === "ArrowDown" || e.key === "ArrowUp") {
          e.preventDefault();
          const step = e.key === "ArrowDown" ? 1 : -1;
          items[(index + step + items.length) % items.length]?.focus();
        } else if (e.key === "Tab") {
          setOpen(false);
        }
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        className="btn btn-sm"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={`Move ${ticket.incidentNumber} to…`}
        aria-disabled={busy || undefined}
        onClick={() => !busy && setOpen((v) => !v)}
      >
        {busy ? "Moving…" : "Move to…"}
      </button>
      {open && (
        <div className="move-menu-list" role="menu" id={menuId}>
          {moves.map((move) => (
            <button
              key={move.action}
              type="button"
              role="menuitem"
              className="move-menu-item"
              onClick={() => {
                close();
                onMove(move);
              }}
            >
              <span>{STATUS_LABELS[move.to]}</span>
              <span className="field-hint">{move.label}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
