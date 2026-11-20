"use client";

import { GoogleSignInButton } from "@/components/GoogleSignInButton";
import { useAuth } from "@/context/AuthContext";
import { type AuthConfig, api } from "@/lib/apiClient";
import { ROLE_LABELS } from "@/lib/labels";
import { useEffect, useState } from "react";

export default function AccountPage() {
  const { user, loading, refreshUser } = useAuth();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [config, setConfig] = useState<AuthConfig | null>(null);

  useEffect(() => {
    api
      .getAuthConfig()
      .then(setConfig)
      .catch(() => setConfig(null));
  }, []);

  if (loading || !user) return <output className="empty">Loading…</output>;

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (next !== confirm) {
      setError("The new passwords don't match.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      await api.changePassword(current, next);
      setCurrent("");
      setNext("");
      setConfirm("");
      setMessage("Password changed.");
      await refreshUser();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Couldn’t change the password");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="auth" style={{ maxWidth: 460 }}>
      <h1>Your account</h1>
      <p>
        {user.fullName} · <span className="mono">{user.username}</span> · {ROLE_LABELS[user.role]}
      </p>

      {user.mustChangePassword && (
        <p className="alert" role="alert" style={{ marginBottom: 14 }}>
          You’re using a temporary password. Choose your own to continue.
        </p>
      )}
      {(message || error) && (
        <p
          className={`alert ${error ? "" : "alert-success"}`}
          role={error ? "alert" : "status"}
          style={{ marginBottom: 14 }}
        >
          {error ?? message}
        </p>
      )}

      <form className="card auth-form" onSubmit={changePassword}>
        <h2 className="section-title">{user.hasPassword ? "Change password" : "Set a password"}</h2>
        {user.hasPassword && (
          <label className="field">
            Current password
            <input
              type="password"
              required
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          </label>
        )}
        <label className="field">
          New password
          <input
            type="password"
            required
            minLength={12}
            maxLength={128}
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <span className="field-hint">At least 12 characters.</span>
        </label>
        <label className="field">
          Confirm new password
          <input
            type="password"
            required
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </label>
        <button type="submit" className="btn btn-primary btn-block" disabled={saving}>
          {saving ? "Saving…" : "Save password"}
        </button>
      </form>

      {config?.googleClientId && !user.mustChangePassword && (
        <section className="card auth-form" style={{ marginTop: 16 }}>
          <h2 className="section-title">Google sign-in</h2>
          {user.googleLinked ? (
            <p className="muted">Your Google account is linked. You can sign in with it.</p>
          ) : (
            <>
              <p className="muted">Link your Google account to sign in without a password.</p>
              <GoogleSignInButton
                clientId={config.googleClientId}
                onError={setError}
                onCredential={async (credential) => {
                  try {
                    await api.linkGoogle(credential);
                    setMessage("Google account linked.");
                    setError(null);
                    await refreshUser();
                  } catch (err: unknown) {
                    setError(err instanceof Error ? err.message : "Couldn’t link Google");
                  }
                }}
              />
            </>
          )}
        </section>
      )}
    </div>
  );
}
