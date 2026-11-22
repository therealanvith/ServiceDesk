"use client";

import { useEffect, useRef } from "react";

interface GoogleIdApi {
  initialize: (config: {
    client_id: string;
    callback: (response: { credential?: string }) => void;
    ux_mode?: "popup" | "redirect";
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: { theme?: string; size?: string; width?: number; text?: string; shape?: string }
  ) => void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdApi } };
  }
}

const SCRIPT_SRC = "https://accounts.google.com/gsi/client";

function loadScript(): Promise<void> {
  if (window.google?.accounts?.id) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
    const script = existing ?? document.createElement("script");
    script.addEventListener("load", () => resolve(), { once: true });
    script.addEventListener("error", () => reject(new Error("Google sign-in is unavailable")), {
      once: true,
    });
    if (!existing) {
      script.src = SCRIPT_SRC;
      script.async = true;
      document.head.appendChild(script);
    }
  });
}

/** Renders Google's own "Sign in with Google" button and hands back the ID token. */
export function GoogleSignInButton({
  clientId,
  onCredential,
  onError,
  text = "continue_with",
}: {
  clientId: string;
  onCredential: (credential: string) => void;
  onError: (message: string) => void;
  /** Google's button wording, e.g. "signup_with" on the registration page. */
  text?: "continue_with" | "signin_with" | "signup_with";
}) {
  const container = useRef<HTMLDivElement>(null);
  const handlers = useRef({ onCredential, onError });
  handlers.current = { onCredential, onError };

  useEffect(() => {
    let cancelled = false;
    loadScript()
      .then(() => {
        const google = window.google?.accounts.id;
        if (cancelled || !google || !container.current) return;
        google.initialize({
          client_id: clientId,
          ux_mode: "popup",
          callback: (response) => {
            if (response.credential) handlers.current.onCredential(response.credential);
            else handlers.current.onError("Google sign-in was cancelled.");
          },
        });
        const dark =
          document.documentElement.dataset.theme === "dark" ||
          (!document.documentElement.dataset.theme &&
            window.matchMedia("(prefers-color-scheme: dark)").matches);
        google.renderButton(container.current, {
          theme: dark ? "filled_black" : "outline",
          size: "large",
          text,
          shape: "rectangular",
          width: Math.min(340, container.current.clientWidth || 340),
        });
      })
      .catch((err: Error) => !cancelled && handlers.current.onError(err.message));
    return () => {
      cancelled = true;
    };
  }, [clientId, text]);

  return <div ref={container} className="google-button" />;
}
