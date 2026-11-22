"use client";

import { Icon } from "@/components/Icon";
import { api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import { useEffect, useRef, useState } from "react";

interface Message {
  fromUser: boolean;
  text: string;
}

const SUGGESTIONS = ["My Wi-Fi keeps dropping", "I forgot my password", "Where is my ticket?"];
const GREETING: Message = {
  fromUser: false,
  text: "Hi! I can help with everyday IT problems and tell you where your tickets stand. What’s going on?",
};
/** The server accepts at most 12 messages per request; keep the most recent ones. */
const MAX_SENT = 12;

/** Floating help chat, answered by Gemini with the on-device knowledge base as its fallback. */
export function ChatWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([GREETING]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cooldown = useCooldown();
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: scroll whenever a message arrives
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: "end" });
  }, [messages, busy]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  const send = async (text: string) => {
    const clean = text.trim();
    if (!clean || busy || cooldown.coolingDown) return;
    const next = [...messages, { fromUser: true, text: clean }];
    setMessages(next);
    setDraft("");
    setBusy(true);
    setError(null);
    try {
      // The greeting is the widget's, not the model's: leave it out of the request.
      const history = next.slice(1).slice(-MAX_SENT);
      const reply = await api.chat(history);
      setMessages([...next, { fromUser: false, text: reply.reply }]);
    } catch (err: unknown) {
      cooldown.trigger(err);
      setError(err instanceof Error ? err.message : "The assistant isn’t available right now.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="chat">
      {open && (
        <section className="chat-panel" aria-label="Help assistant">
          <header className="chat-head">
            <span className="chat-title">Help assistant</span>
            <button
              type="button"
              className="chat-close"
              aria-label="Close assistant"
              onClick={() => {
                setOpen(false);
                buttonRef.current?.focus();
              }}
            >
              <Icon name="close" size={16} />
            </button>
          </header>
          <div className="chat-log" role="log" aria-live="polite" aria-busy={busy}>
            {messages.map((m, i) => (
              <p
                // biome-ignore lint/suspicious/noArrayIndexKey: messages are append-only
                key={i}
                className={m.fromUser ? "chat-msg chat-msg-user" : "chat-msg"}
              >
                {m.text}
              </p>
            ))}
            {busy && <p className="chat-msg chat-typing">Thinking…</p>}
            {error && (
              <p className="chat-error" role="alert">
                {error}
              </p>
            )}
            <div ref={endRef} />
          </div>
          {messages.length === 1 && (
            <div className="chat-suggestions">
              {SUGGESTIONS.map((s) => (
                <button key={s} type="button" className="chip" onClick={() => send(s)}>
                  {s}
                </button>
              ))}
            </div>
          )}
          <form
            className="chat-form"
            onSubmit={(e) => {
              e.preventDefault();
              send(draft);
            }}
          >
            <input
              ref={inputRef}
              value={draft}
              maxLength={1000}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={
                cooldown.coolingDown
                  ? `Try again in ${cooldown.secondsLeft} s`
                  : "Ask about a problem…"
              }
              aria-label="Message"
            />
            <button
              type="submit"
              className="btn btn-primary btn-sm"
              disabled={busy || cooldown.coolingDown || !draft.trim()}
              aria-label="Send"
            >
              <Icon name="send" size={14} />
            </button>
          </form>
        </section>
      )}
      <button
        ref={buttonRef}
        type="button"
        className="chat-fab"
        aria-expanded={open}
        aria-label={open ? "Close assistant" : "Ask the assistant"}
        onClick={() => setOpen((v) => !v)}
      >
        <Icon name={open ? "close" : "chat"} size={22} />
      </button>
    </div>
  );
}
