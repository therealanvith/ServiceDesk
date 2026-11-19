"use client";

import { useAuth } from "@/context/AuthContext";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const DEMO_PERSONAS = [
  { username: "employee1", label: "Prof. Alan Turing", role: "Employee (End-user)" },
  { username: "tech_network", label: "Alice Smith", role: "Network Specialist Tech" },
  { username: "tech_hardware", label: "Bob Jones", role: "Hardware Specialist Tech" },
  { username: "tech_lead", label: "Charlie Brown", role: "Lead Systems Technician" },
  { username: "admin", label: "Administrator", role: "IT System Administrator" },
];

export default function LoginPage() {
  const { user, login } = useAuth();
  const router = useRouter();

  const [username, setUsername] = useState("employee1");
  const [password, setPassword] = useState("password123");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      router.push("/");
    }
  }, [user, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Invalid credentials");
    } finally {
      setSubmitting(false);
    }
  };

  const handleQuickLogin = async (u: string) => {
    setUsername(u);
    setPassword("password123");
    setSubmitting(true);
    setError(null);
    try {
      await login(u, "password123");
      router.push("/");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to sign in");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        maxWidth: "480px",
        margin: "3rem auto",
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-color)",
        borderRadius: "8px",
        padding: "2rem",
        boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.05)",
      }}
    >
      <div style={{ textAlign: "center", marginBottom: "1.75rem" }}>
        <h1
          style={{
            fontSize: "1.5rem",
            fontWeight: 700,
            letterSpacing: "-0.02em",
            color: "var(--text-primary)",
            marginBottom: "0.25rem",
          }}
        >
          Sign In to ServiceDesk
        </h1>
        <p style={{ fontSize: "0.875rem", color: "var(--text-muted)" }}>
          Stateless JWT Authentication &bull; Role-Based Access Control
        </p>
      </div>

      {error && (
        <div
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            padding: "0.6rem 0.75rem",
            borderRadius: "4px",
            fontSize: "0.8125rem",
            marginBottom: "1.25rem",
            border: "1px solid #fecaca",
          }}
        >
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} style={{ marginBottom: "1.5rem" }}>
        <div style={{ marginBottom: "1rem" }}>
          <label
            htmlFor="login-username"
            style={{
              display: "block",
              fontSize: "0.8125rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Username
          </label>
          <input
            id="login-username"
            type="text"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            style={{
              width: "100%",
              padding: "0.55rem 0.75rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.875rem",
              backgroundColor: "var(--bg-primary)",
            }}
          />
        </div>

        <div style={{ marginBottom: "1.25rem" }}>
          <label
            htmlFor="login-password"
            style={{
              display: "block",
              fontSize: "0.8125rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Password
          </label>
          <input
            id="login-password"
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{
              width: "100%",
              padding: "0.55rem 0.75rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.875rem",
              backgroundColor: "var(--bg-primary)",
            }}
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          style={{
            width: "100%",
            padding: "0.65rem",
            backgroundColor: "var(--accent)",
            color: "white",
            fontSize: "0.875rem",
            fontWeight: 600,
            border: "none",
            borderRadius: "4px",
            cursor: "pointer",
            opacity: submitting ? 0.7 : 1,
          }}
        >
          {submitting ? "Signing in..." : "Sign In"}
        </button>
      </form>

      {/* Demo Quick Fill Personas */}
      <div
        style={{
          borderTop: "1px solid var(--border-color)",
          paddingTop: "1.25rem",
        }}
      >
        <div
          style={{
            fontSize: "0.75rem",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            color: "var(--text-muted)",
            marginBottom: "0.75rem",
          }}
        >
          Course Evaluator Demo Personas (1-Click Login)
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
          {DEMO_PERSONAS.map((p) => (
            <button
              key={p.username}
              type="button"
              onClick={() => handleQuickLogin(p.username)}
              disabled={submitting}
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                padding: "0.5rem 0.75rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                backgroundColor: "var(--bg-primary)",
                fontSize: "0.8125rem",
                cursor: "pointer",
                textAlign: "left",
              }}
            >
              <div>
                <strong>{p.label}</strong>
                <div style={{ fontSize: "0.7rem", color: "var(--text-muted)" }}>{p.role}</div>
              </div>
              <span style={{ fontSize: "0.75rem", color: "var(--accent)" }}>Select &rarr;</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
