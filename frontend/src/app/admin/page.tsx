"use client";

import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type SlaEvaluationSummary,
  type TechnicianItem,
  api,
} from "@/lib/apiClient";
import Link from "next/link";
import { useEffect, useState } from "react";

const STRATEGY_DESCRIPTIONS = {
  ROUND_ROBIN:
    "Distributes incidents sequentially among available technicians using an atomic counter.",
  LEAST_LOADED:
    "Evaluates active (non-closed) workloads and assigns to the technician currently holding the lowest burden.",
  SKILL_MATCH:
    "Matches the incident category with verified technician skills (e.g. NETWORK, HARDWARE), falling back to least-loaded if tied or when no specialist is available.",
};

const ALL_CATEGORIES: Category[] = ["NETWORK", "HARDWARE", "SOFTWARE", "ACCESS"];

export default function AdminPage() {
  const { user } = useAuth();

  const [activeStrategy, setActiveStrategy] = useState("SKILL_MATCH");
  const [selectedStrategy, setSelectedStrategy] = useState("SKILL_MATCH");
  const [technicians, setTechnicians] = useState<TechnicianItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [savingStrategy, setSavingStrategy] = useState(false);
  const [evaluatingSla, setEvaluatingSla] = useState(false);
  const [slaResult, setSlaResult] = useState<SlaEvaluationSummary | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;
    Promise.allSettled([api.getAssignmentConfig(), api.listTechnicians()])
      .then(([configRes, techRes]) => {
        if (!isMounted) return;
        if (configRes.status === "fulfilled") {
          setActiveStrategy(configRes.value.activeStrategy);
          setSelectedStrategy(configRes.value.activeStrategy);
        }
        if (techRes.status === "fulfilled") {
          setTechnicians(techRes.value);
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleSaveStrategy = async () => {
    setSavingStrategy(true);
    setError(null);
    setMessage(null);
    try {
      const res = await api.updateAssignmentConfig(selectedStrategy);
      setActiveStrategy(res.activeStrategy);
      setMessage(`Assignment strategy updated to ${res.activeStrategy} successfully.`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update strategy");
    } finally {
      setSavingStrategy(false);
    }
  };

  const handleTriggerSla = async () => {
    setEvaluatingSla(true);
    setError(null);
    try {
      const res = await api.triggerSlaEvaluation();
      setSlaResult(res);
      // Reload technicians to reflect updated ticket loads or priorities
      const updatedTechs = await api.listTechnicians();
      setTechnicians(updatedTechs);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to trigger SLA evaluation");
    } finally {
      setEvaluatingSla(false);
    }
  };

  const handleToggleSkill = async (tech: TechnicianItem, cat: Category) => {
    const hasSkill = tech.skills.includes(cat);
    const newSkills = hasSkill ? tech.skills.filter((s) => s !== cat) : [...tech.skills, cat];

    try {
      const updated = await api.updateTechnicianSkills(tech.id, newSkills);
      setTechnicians((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update skills");
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "3rem", textAlign: "center", color: "var(--text-muted)" }}>
        Loading administration settings...
      </div>
    );
  }

  if (user?.role !== "ADMINISTRATOR") {
    return (
      <div style={{ maxWidth: "600px", margin: "3rem auto", textAlign: "center" }}>
        <h2 style={{ fontSize: "1.25rem", fontWeight: 700, marginBottom: "0.5rem" }}>
          Administrator Privileges Required
        </h2>
        <p style={{ fontSize: "0.875rem", color: "var(--text-muted)", marginBottom: "1.25rem" }}>
          You must be logged in as an Administrator to view and update system dispatch rules.
        </p>
        <Link
          href="/"
          style={{
            fontSize: "0.875rem",
            color: "var(--accent)",
            fontWeight: 600,
          }}
        >
          &larr; Return to Dashboard or use Demo Switch in header
        </Link>
      </div>
    );
  }

  return (
    <div
      style={{
        maxWidth: "900px",
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        gap: "2rem",
      }}
    >
      <div>
        <h1
          style={{
            fontSize: "1.5rem",
            fontWeight: 700,
            letterSpacing: "-0.03em",
            color: "var(--text-primary)",
            marginBottom: "0.25rem",
          }}
        >
          System Administration & Strategy Engine
        </h1>
        <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
          Dynamically swap GoF Strategy implementations, trigger SLA monitoring, and configure
          technician rosters.
        </p>
      </div>

      {message && (
        <div
          style={{
            backgroundColor: "#ecfdf5",
            color: "#047857",
            padding: "0.75rem 1rem",
            borderRadius: "6px",
            border: "1px solid #a7f3d0",
            fontSize: "0.875rem",
          }}
        >
          {message}
        </div>
      )}

      {error && (
        <div
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            padding: "0.75rem 1rem",
            borderRadius: "6px",
            border: "1px solid #fecaca",
            fontSize: "0.875rem",
          }}
        >
          {error}
        </div>
      )}

      {/* Strategy Engine Card */}
      <section
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.5rem",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: "1rem",
          }}
        >
          <div>
            <h2 style={{ fontSize: "1.125rem", fontWeight: 700, color: "var(--text-primary)" }}>
              Automated Assignment Strategy Engine
            </h2>
            <p style={{ fontSize: "0.8125rem", color: "var(--text-muted)" }}>
              Changes take effect immediately at runtime without server restart (Strategy Pattern).
            </p>
          </div>
          <span
            style={{
              padding: "0.2rem 0.6rem",
              borderRadius: "9999px",
              backgroundColor: "#dbeafe",
              color: "#1e40af",
              fontSize: "0.75rem",
              fontWeight: 700,
            }}
          >
            ACTIVE: {activeStrategy}
          </span>
        </div>

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "0.75rem",
            marginBottom: "1.25rem",
          }}
        >
          {(["SKILL_MATCH", "LEAST_LOADED", "ROUND_ROBIN"] as const).map((strat) => (
            <label
              key={strat}
              style={{
                display: "flex",
                alignItems: "flex-start",
                gap: "0.75rem",
                padding: "0.875rem",
                borderRadius: "6px",
                border: "1px solid",
                borderColor: selectedStrategy === strat ? "var(--accent)" : "var(--border-color)",
                backgroundColor: selectedStrategy === strat ? "#eff6ff" : "var(--bg-primary)",
                cursor: "pointer",
              }}
            >
              <input
                type="radio"
                name="strategy"
                value={strat}
                checked={selectedStrategy === strat}
                onChange={(e) => setSelectedStrategy(e.target.value)}
                style={{ marginTop: "0.2rem" }}
              />
              <div>
                <strong style={{ fontSize: "0.875rem", color: "var(--text-primary)" }}>
                  {strat}
                </strong>
                <p
                  style={{
                    fontSize: "0.8125rem",
                    color: "var(--text-secondary)",
                    marginTop: "0.15rem",
                  }}
                >
                  {STRATEGY_DESCRIPTIONS[strat]}
                </p>
              </div>
            </label>
          ))}
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <button
            type="button"
            onClick={handleSaveStrategy}
            disabled={savingStrategy || selectedStrategy === activeStrategy}
            style={{
              padding: "0.55rem 1.1rem",
              backgroundColor: "var(--accent)",
              color: "white",
              borderRadius: "4px",
              fontSize: "0.875rem",
              fontWeight: 600,
              border: "none",
              cursor: "pointer",
              opacity: savingStrategy || selectedStrategy === activeStrategy ? 0.6 : 1,
            }}
          >
            {savingStrategy ? "Saving..." : "Apply Strategy"}
          </button>
        </div>
      </section>

      {/* SLA Evaluator Section */}
      <section
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.5rem",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "1rem",
          }}
        >
          <div>
            <h2 style={{ fontSize: "1.125rem", fontWeight: 700, color: "var(--text-primary)" }}>
              SLA Deadline Monitoring & Escalation Worker
            </h2>
            <p style={{ fontSize: "0.8125rem", color: "var(--text-muted)" }}>
              The background daemon automatically runs every 60s. Use this button to trigger
              immediate evaluation during demo evaluation.
            </p>
          </div>

          <button
            type="button"
            onClick={handleTriggerSla}
            disabled={evaluatingSla}
            style={{
              padding: "0.55rem 1.1rem",
              backgroundColor: "#f59e0b",
              color: "white",
              borderRadius: "4px",
              fontSize: "0.875rem",
              fontWeight: 600,
              border: "none",
              cursor: "pointer",
            }}
          >
            {evaluatingSla ? "Evaluating..." : "⚡ Run SLA Breach Evaluation Now"}
          </button>
        </div>

        {slaResult && (
          <div
            style={{
              marginTop: "1rem",
              backgroundColor: "#f8fafc",
              border: "1px solid var(--border-color)",
              borderRadius: "4px",
              padding: "0.75rem 1rem",
              fontSize: "0.8125rem",
              display: "flex",
              gap: "2rem",
            }}
          >
            <div>
              Active Incidents Evaluated: <strong>{slaResult.evaluatedCount}</strong>
            </div>
            <div>
              New Breaches Escalated:{" "}
              <strong style={{ color: slaResult.breachedCount > 0 ? "#b91c1c" : "#047857" }}>
                {slaResult.breachedCount}
              </strong>
            </div>
            <div>
              Evaluated At:{" "}
              <span style={{ fontFamily: "var(--font-mono)" }}>
                {new Date(slaResult.evaluatedAt).toLocaleTimeString()}
              </span>
            </div>
          </div>
        )}
      </section>

      {/* Technician Roster & Skills Table */}
      <section
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.5rem",
        }}
      >
        <h2
          style={{
            fontSize: "1.125rem",
            fontWeight: 700,
            color: "var(--text-primary)",
            marginBottom: "0.25rem",
          }}
        >
          Technician Capacity & Skill Category Matrix
        </h2>
        <p style={{ fontSize: "0.8125rem", color: "var(--text-muted)", marginBottom: "1rem" }}>
          Click category pills to assign or remove skills from technicians in real time.
        </p>

        <div style={{ overflowX: "auto" }}>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              fontSize: "0.875rem",
              textAlign: "left",
            }}
          >
            <thead>
              <tr
                style={{
                  borderBottom: "1px solid var(--border-color)",
                  color: "var(--text-muted)",
                  fontSize: "0.75rem",
                  fontWeight: 600,
                  textTransform: "uppercase",
                }}
              >
                <th style={{ padding: "0.6rem 0.75rem" }}>Technician</th>
                <th style={{ padding: "0.6rem 0.75rem" }}>Email</th>
                <th style={{ padding: "0.6rem 0.75rem" }}>Active Workload</th>
                <th style={{ padding: "0.6rem 0.75rem" }}>Skill Category Tags</th>
              </tr>
            </thead>
            <tbody>
              {technicians.map((tech) => (
                <tr key={tech.id} style={{ borderBottom: "1px solid var(--border-color)" }}>
                  <td style={{ padding: "0.6rem 0.75rem", fontWeight: 600 }}>{tech.fullName}</td>
                  <td
                    style={{
                      padding: "0.6rem 0.75rem",
                      color: "var(--text-secondary)",
                      fontSize: "0.8125rem",
                    }}
                  >
                    {tech.email}
                  </td>
                  <td style={{ padding: "0.6rem 0.75rem" }}>
                    <span
                      style={{
                        padding: "0.15rem 0.45rem",
                        borderRadius: "9999px",
                        fontSize: "0.75rem",
                        fontWeight: 700,
                        backgroundColor: tech.activeTicketsCount === 0 ? "#ecfdf5" : "#eff6ff",
                        color: tech.activeTicketsCount === 0 ? "#047857" : "#1d4ed8",
                      }}
                    >
                      {tech.activeTicketsCount} active tickets
                    </span>
                  </td>
                  <td style={{ padding: "0.6rem 0.75rem" }}>
                    <div style={{ display: "flex", gap: "0.35rem", flexWrap: "wrap" }}>
                      {ALL_CATEGORIES.map((cat) => {
                        const has = tech.skills.includes(cat);
                        return (
                          <button
                            key={cat}
                            type="button"
                            onClick={() => handleToggleSkill(tech, cat)}
                            style={{
                              padding: "0.15rem 0.45rem",
                              borderRadius: "4px",
                              fontSize: "0.7rem",
                              fontWeight: 600,
                              cursor: "pointer",
                              border: `1px solid ${has ? "#93c5fd" : "var(--border-color)"}`,
                              backgroundColor: has ? "#dbeafe" : "var(--bg-primary)",
                              color: has ? "#1e40af" : "var(--text-muted)",
                            }}
                          >
                            {cat} {has ? "✓" : "+"}
                          </button>
                        );
                      })}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
