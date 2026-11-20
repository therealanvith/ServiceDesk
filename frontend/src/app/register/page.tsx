"use client";

import { GoogleSignInButton } from "@/components/GoogleSignInButton";
import { useAuth } from "@/context/AuthContext";
import { type AuthConfig, api } from "@/lib/apiClient";
import { useCooldown } from "@/lib/useCooldown";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const USERNAME_PATTERN = "[a-z0-9._\\-]{3,50}";

export default function RegisterPage() {
  const { user, register, loginWithGoogle } = useAuth();
  const router = useRouter();

  const [form, setForm] = useState({ username: "", fullName: "", email: "", password: "" });
  const [confirm, setConfirm] = useState("");
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
      .catch(() => setConfig({ googleClientId: null, registrationEnabled: true }));
  }, []);

  // A first Google sign-in creates the employee account, so it doubles as sign-up.
  const signUpWithGoogle = async (credential: string) => {
    setSubmitting(true);
    setError(null);
    try {
      await loginWithGoogle(credential);
      router.replace("/");
    } catch (err: unknown) {
      cooldown.trigger(err);
      setError(err instanceof Error ? err.message : "Couldn't sign up with Google");
    } finally {
      setSubmitting(false);
    }
  };

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (form.password !== confirm) {
      setError("The passwords don't match.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await register({
        username: form.username.trim().toLowerCase(),
        fullName: form.fullName.trim(),
        email: form.email.trim() || undefined,
        password: form.password,
      });
      router.replace("/");
    } catch (err: unknown) {
      cooldown.trigger(err);
      setError(err instanceof Error ? err.message : "Couldn't create the account");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth">
      <h1>Create an account</h1>
      <p>New accounts can report issues. IT staff access is granted by an administrator.</p>

      {config?.googleClientId && (
        <>
          <GoogleSignInButton
            clientId={config.googleClientId}
            text="signup_with"
            onCredential={signUpWithGoogle}
            onError={setError}
          />
          <p className="divider">or create a username and password</p>
        </>
      )}

      <form className="card auth-form" onSubmit={handleSubmit}>
        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}
        <label className="field">
          Full name
          <input
            required
            maxLength={100}
            autoComplete="name"
            value={form.fullName}
            onChange={set("fullName")}
          />
        </label>
        <label className="field">
          Username
          <input
            required
            pattern={USERNAME_PATTERN}
            title="3-50 characters: lowercase letters, digits, '.', '_' or '-'"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={form.username}
            onChange={set("username")}
          />
          <span className="field-hint">
            Lowercase letters, digits, dots, dashes or underscores.
          </span>
        </label>
        <label className="field">
          Email <span className="field-hint">(optional)</span>
          <input
            type="email"
            maxLength={100}
            autoComplete="email"
            value={form.email}
            onChange={set("email")}
          />
        </label>
        <label className="field">
          Password
          <input
            type="password"
            required
            minLength={12}
            maxLength={128}
            autoComplete="new-password"
            value={form.password}
            onChange={set("password")}
          />
          <span className="field-hint">At least 12 characters.</span>
        </label>
        <label className="field">
          Confirm password
          <input
            type="password"
            required
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </label>
        <button
          type="submit"
          className="btn btn-primary btn-block"
          disabled={submitting || cooldown.coolingDown}
        >
          {submitting
            ? "Creating account…"
            : cooldown.coolingDown
              ? `Try again in ${cooldown.secondsLeft} s`
              : "Create account"}
        </button>
      </form>

      <p className="auth-footer">
        Already have an account?{" "}
        <Link href="/login" className="link">
          Sign in
        </Link>
      </p>
    </div>
  );
}
