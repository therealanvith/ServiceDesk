"use client";

import { useAuth } from "@/context/AuthContext";
import { liveEventsUrl, readAuthToken } from "@/lib/apiClient";
import { startLiveEvents, useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

interface Toast {
  id: number;
  title: string;
  message?: string;
  href?: string;
  tone?: "info" | "success" | "error";
}

const TOAST_EVENT = "servicedesk:toast";
const TOAST_MS = 6000;

/** Shows a short confirmation from anywhere, e.g. after an action succeeds. */
export function showToast(toast: Omit<Toast, "id">) {
  window.dispatchEvent(new CustomEvent(TOAST_EVENT, { detail: toast }));
}

/**
 * Keeps the live-updates stream open while someone is signed in and shows incoming notifications
 * as toasts. Toasts sit in a polite live region so screen readers announce them without
 * interrupting.
 */
export function LiveUpdates() {
  const { user } = useAuth();
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  useEffect(() => {
    const token = user ? readAuthToken() : null;
    if (!token) return;
    return startLiveEvents(liveEventsUrl(), token);
  }, [user]);

  const timers = useRef(new Map<number, number>());

  const dismiss = useCallback((id: number) => {
    window.clearTimeout(timers.current.get(id));
    timers.current.delete(id);
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  // Each toast closes itself after a while, but the countdown stops while it is hovered or holds
  // keyboard focus, so its link stays usable for as long as someone needs it.
  const startTimer = useCallback(
    (id: number) => {
      window.clearTimeout(timers.current.get(id));
      timers.current.set(
        id,
        window.setTimeout(() => dismiss(id), TOAST_MS)
      );
    },
    [dismiss]
  );

  const pauseTimer = useCallback((id: number) => {
    window.clearTimeout(timers.current.get(id));
    timers.current.delete(id);
  }, []);

  const push = useCallback(
    (toast: Omit<Toast, "id">) => {
      const id = nextId.current++;
      setToasts((current) => [...current.slice(-3), { ...toast, id }]);
      startTimer(id);
    },
    [startTimer]
  );

  useLiveEvent((e) => {
    if (e.event === "notification") {
      push({
        title: e.data.title,
        message: e.data.message,
        href: e.data.incidentId ? `/incidents/${e.data.incidentId}` : "/notifications",
      });
    }
  });

  useEffect(() => {
    const listener = (e: Event) => push((e as CustomEvent<Omit<Toast, "id">>).detail);
    window.addEventListener(TOAST_EVENT, listener);
    return () => window.removeEventListener(TOAST_EVENT, listener);
  }, [push]);

  return (
    <section className="toasts" aria-live="polite" aria-label="Notifications">
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`toast toast-${t.tone ?? "info"}`}
          onMouseEnter={() => pauseTimer(t.id)}
          onMouseLeave={(e) => {
            if (!e.currentTarget.contains(document.activeElement)) startTimer(t.id);
          }}
          onFocus={() => pauseTimer(t.id)}
          onBlur={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) startTimer(t.id);
          }}
        >
          <div className="toast-body">
            <p className="toast-title">
              {t.href ? (
                <Link href={t.href} className="link">
                  {t.title}
                </Link>
              ) : (
                t.title
              )}
            </p>
            {t.message && <p className="toast-message">{t.message}</p>}
          </div>
          <button
            type="button"
            className="toast-close"
            aria-label="Dismiss"
            onClick={() => dismiss(t.id)}
          >
            ×
          </button>
        </div>
      ))}
    </section>
  );
}
