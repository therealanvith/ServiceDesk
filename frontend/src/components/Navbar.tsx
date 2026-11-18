"use client";

import { useAuth } from "@/context/AuthContext";
import Link from "next/link";
import { useState } from "react";

const DEMO_ACCOUNTS = [
  { username: "employee1", label: "Alan Turing (Employee)", role: "EMPLOYEE" },
  { username: "tech_network", label: "Alice Smith (Network Tech)", role: "TECHNICIAN" },
  { username: "tech_hardware", label: "Bob Jones (Hardware Tech)", role: "TECHNICIAN" },
  { username: "tech_lead", label: "Charlie Brown (Lead Tech)", role: "TECHNICIAN" },
  { username: "admin", label: "System Administrator (Admin)", role: "ADMINISTRATOR" },
];

export function Navbar() {
  const { user, login, logout } = useAuth();
  const [switching, setSwitching] = useState(false);

  const handleQuickSwitch = async (username: string) => {
    setSwitching(true);
    try {
      await login(username, "password123");
    } finally {
      setSwitching(false);
    }
  };

  return (
    <header
      style={{
        backgroundColor: "var(--bg-surface)",
        borderBottom: "1px solid var(--border-color)",
        padding: "0.875rem 2rem",
      }}
    >
      <div
        style={{
          maxWidth: "1100px",
          margin: "0 auto",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "1.5rem",
          flexWrap: "wrap",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "2rem" }}>
          <Link
            href="/"
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: "0.4rem",
            }}
          >
            <span
              style={{
                fontSize: "1.25rem",
                fontWeight: 700,
                letterSpacing: "-0.03em",
                color: "var(--text-primary)",
              }}
            >
              ServiceDesk
            </span>
            <span
              style={{
                fontSize: "0.75rem",
                fontWeight: 600,
                color: "var(--accent)",
                padding: "0.1rem 0.4rem",
                backgroundColor: "#eff6ff",
                borderRadius: "4px",
              }}
            >
              ITSM
            </span>
          </Link>

          <nav style={{ display: "flex", alignItems: "center", gap: "1.25rem", fontSize: "0.875rem" }}>
            <Link
              href="/"
              style={{
                color: "var(--text-secondary)",
                fontWeight: 500,
              }}
            >
              Incidents
            </Link>
            <Link
              href="/incidents/new"
              style={{
                color: "var(--text-secondary)",
                fontWeight: 500,
              }}
            >
              + Report Incident
            </Link>
            {user?.role === "ADMINISTRATOR" && (
              <Link
                href="/admin"
                style={{
                  color: "var(--text-secondary)",
                  fontWeight: 500,
                }}
              >
                Administration
              </Link>
            )}
          </nav>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
          {/* Quick Account Switcher (Evaluator Demo Tool) */}
          <div style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
            <label
              htmlFor="demo-user-select"
              style={{
                fontSize: "0.75rem",
                color: "var(--text-muted)",
              }}
            >
              Demo Switch:
            </label>
            <select
              id="demo-user-select"
              value={user?.username ?? ""}
              onChange={(e) => handleQuickSwitch(e.target.value)}
              disabled={switching}
              style={{
                fontSize: "0.8125rem",
                padding: "0.3rem 0.6rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                backgroundColor: "var(--bg-primary)",
                color: "var(--text-primary)",
                cursor: "pointer",
              }}
            >
              {!user && <option value="">Select Persona...</option>}
              {DEMO_ACCOUNTS.map((acc) => (
                <option key={acc.username} value={acc.username}>
                  {acc.label}
                </option>
              ))}
            </select>
          </div>

          {/* User Status / Logout */}
          {user ? (
            <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
              <div style={{ textAlign: "right", lineHeight: 1.2 }}>
                <div style={{ fontSize: "0.8125rem", fontWeight: 600 }}>
                  {user.fullName}
                </div>
                <div
                  style={{
                    fontSize: "0.6875rem",
                    color: "var(--text-muted)",
                    fontWeight: 500,
                  }}
                >
                  {user.role}
                </div>
              </div>
              <button
                type="button"
                onClick={logout}
                style={{
                  fontSize: "0.75rem",
                  padding: "0.3rem 0.6rem",
                  borderRadius: "4px",
                  border: "1px solid var(--border-color)",
                  backgroundColor: "transparent",
                  color: "var(--text-secondary)",
                  cursor: "pointer",
                }}
              >
                Sign Out
              </button>
            </div>
          ) : (
            <Link
              href="/login"
              style={{
                fontSize: "0.8125rem",
                fontWeight: 600,
                color: "white",
                backgroundColor: "var(--accent)",
                padding: "0.4rem 0.85rem",
                borderRadius: "4px",
              }}
            >
              Sign In
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
