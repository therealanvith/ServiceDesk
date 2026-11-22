"use client";

import { useEffect, useRef } from "react";

/** Events the server pushes on GET /events. */
export interface LiveNotification {
  type: string;
  title: string;
  message: string;
  incidentId: string | null;
  createdAt: string;
}

export interface IncidentUpdated {
  incidentId: string;
  incidentNumber: string;
  status: string;
}

export type LiveEvent =
  | { event: "ready"; data: Record<string, never> }
  | { event: "notification"; data: LiveNotification }
  | { event: "incident-updated"; data: IncidentUpdated };

const WINDOW_EVENT = "servicedesk:live";

/**
 * Reads the Server-Sent Events stream with fetch, so the token travels in the Authorization
 * header instead of the URL (EventSource can't set headers). Reconnects with backoff until
 * stopped; gives up on 401/403, when the session is no longer valid.
 */
export function startLiveEvents(url: string, token: string): () => void {
  const controller = new AbortController();
  let delay = 2000;

  const connect = async () => {
    while (!controller.signal.aborted) {
      try {
        const response = await fetch(url, {
          headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
          signal: controller.signal,
          cache: "no-store",
        });
        if (response.status === 401 || response.status === 403) return;
        if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
        delay = 2000;
        await read(response.body);
      } catch {
        if (controller.signal.aborted) return;
      }
      // Dropped or failed: wait, then try again (up to 30 s apart).
      await new Promise((resolve) => setTimeout(resolve, delay));
      delay = Math.min(delay * 2, 30_000);
    }
  };

  const read = async (body: ReadableStream<Uint8Array>) => {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");
      let boundary = buffer.indexOf("\n\n");
      while (boundary >= 0) {
        dispatch(buffer.slice(0, boundary));
        buffer = buffer.slice(boundary + 2);
        boundary = buffer.indexOf("\n\n");
      }
    }
  };

  const dispatch = (block: string) => {
    let event = "message";
    const data: string[] = [];
    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
    }
    if (data.length === 0) return; // heartbeat comments carry no data
    try {
      const detail = { event, data: JSON.parse(data.join("\n")) } as LiveEvent;
      window.dispatchEvent(new CustomEvent<LiveEvent>(WINDOW_EVENT, { detail }));
    } catch {
      // ignore malformed events
    }
  };

  void connect();
  return () => controller.abort();
}

/** Runs `handler` for each live event while the component is mounted. */
export function useLiveEvent(handler: (event: LiveEvent) => void) {
  const ref = useRef(handler);
  ref.current = handler;
  useEffect(() => {
    const listener = (e: Event) => ref.current((e as CustomEvent<LiveEvent>).detail);
    window.addEventListener(WINDOW_EVENT, listener);
    return () => window.removeEventListener(WINDOW_EVENT, listener);
  }, []);
}
