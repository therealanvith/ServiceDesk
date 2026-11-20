"use client";

import { Modal } from "@/components/TransitionDialog";
import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

/** True while someone is typing, so single-key shortcuts never steal their keystrokes. */
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.isContentEditable ||
    target.tagName === "INPUT" ||
    target.tagName === "TEXTAREA" ||
    target.tagName === "SELECT"
  );
}

/**
 * Single-key shortcuts for signed-in users: "/" search, "n" new ticket, "b" board (staff), "?"
 * this list. Pages opt in to "/" by marking their search input with data-shortcut="search".
 */
export function KeyboardShortcuts() {
  const { user } = useAuth();
  const router = useRouter();
  const [helpOpen, setHelpOpen] = useState(false);
  const helpOpenRef = useRef(helpOpen);
  helpOpenRef.current = helpOpen;
  const staff = Boolean(user && user.role !== "EMPLOYEE");

  useEffect(() => {
    if (!user) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.defaultPrevented || e.ctrlKey || e.metaKey || e.altKey || isTyping(e.target)) return;
      // While a dialog is open (another one, or this list) only "?" to close the list applies.
      const dialogOpen = Boolean(document.querySelector("dialog[open]"));
      if (e.key === "?") {
        if (dialogOpen && !helpOpenRef.current) return;
        e.preventDefault();
        setHelpOpen((open) => !open);
        return;
      }
      if (dialogOpen) return;
      if (e.key === "/") {
        const search = document.querySelector<HTMLElement>('[data-shortcut="search"]');
        if (search) {
          e.preventDefault();
          search.focus();
        }
      } else if (e.key === "n") {
        router.push("/incidents/new");
      } else if (e.key === "b" && staff) {
        router.push("/board");
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [user, staff, router]);

  if (!user || !helpOpen) return null;

  const shortcuts: [string, string][] = [
    ["/", "Search this page"],
    ["n", "Report an issue"],
    ...(staff ? ([["b", "Open the board"]] as [string, string][]) : []),
    ["?", "Show or hide this list"],
    ["Esc", "Close a dialog"],
  ];

  return (
    <Modal title="Keyboard shortcuts" onClose={() => setHelpOpen(false)}>
      <dl className="shortcut-list">
        {shortcuts.map(([key, label]) => (
          <div key={key}>
            <dt>
              <kbd>{key}</kbd>
            </dt>
            <dd>{label}</dd>
          </div>
        ))}
      </dl>
      <div className="row-end">
        <button type="button" className="btn" onClick={() => setHelpOpen(false)}>
          Done
        </button>
      </div>
    </Modal>
  );
}
