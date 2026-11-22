"use client";

import { GoogleSignInButton } from "@/components/GoogleSignInButton";
import { useAuth } from "@/context/AuthContext";
import { type AuthConfig, api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export default function LoginPage() {
  const { user, login, loginWithGoogle } = useAuth();
  const router = useRouter();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [config, setConfig] = useState<AuthConfig | null>(null);
  const cooldown = useCooldown();

  useEffect(() => {
    if (user) router.replace("/");
  }, [user, router]);

  useEffect(() => {
    api
      .getAuthConfig()
      .then(setConfig)
      .catch(() => setConfig({ googleClientId: null, registrationEnabled: false }));
  }, []);

  const run = async (action: () => Promise<void>) => {
    setSubmitting(true);
    setError(null);
    try {
      await action();
      router.replace("/");
    } catch (err: unknown) {
      cooldown.trigger(err);
      setError(err instanceof Error ? err.message : "Sign-in failed");
      setPassword("");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth">
      <h1>Sign in to ServiceDesk</h1>
      <p>Report IT problems and follow them through to a fix.</p>

      {error && (
        <p className="alert" role="alert" style={{ marginBottom: 14 }}>
          {error}
        </p>
      )}

      {config?.googleClientId && (
        <>
          <GoogleSignInButton
            clientId={config.googleClientId}
            onCredential={(credential) => run(() => loginWithGoogle(credential))}
            onError={setError}
          />
          <p className="divider">or use your username</p>
        </>
      )}

      <form
        className="card auth-form"
        onSubmit={(e) => {
          e.preventDefault();
          run(() => login(username.trim(), password));
        }}
      >
        <label className="field">
          Username
          <input
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>
        <label className="field">
          Password
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={submitting || cooldown.coolingDown}
        >
          {submitting
            ? "Signing in…"
            : cooldown.coolingDown
              ? `Try again in ${cooldown.secondsLeft} s`
              : "Sign in"}
        </button>
      </form>

      {config?.registrationEnabled && (
        <p className="auth-footer">
          New here?{" "}
          <Link href="/register" className="link">
            Create an account
          </Link>
        </p>
      )}
    </div>
  );
}
