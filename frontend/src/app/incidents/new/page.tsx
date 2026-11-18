"use client";

import {
  PriorityMatrixPreview,
  calculateMatrixPriority,
} from "@/components/PriorityMatrixPreview";
import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type Impact,
  type Urgency,
  api,
} from "@/lib/apiClient";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const DEMO_PRESETS = [
  {
    name: "Lab 304 Switch Outage (Demo Scenario 1 & 3)",
    title: "Lab 304 workstations cannot reach local gateway",
    description:
      "All desktops in Computer Lab 304 show IP autoconfiguration 169.254.x.x and cannot reach the default gateway after power maintenance. Switch port trunking appears inactive.",
    category: "NETWORK" as Category,
    impact: "HIGH" as Impact,
    urgency: "HIGH" as Urgency,
  },
  {
    name: "Wi-Fi Disconnects (Clarification Loop Demo)",
    title: "Intermittent Wi-Fi drops on Library 2nd floor",
    description:
      "Students reporting frequent packet loss and drops when roaming between APs near reading room 2B.",
    category: "NETWORK" as Category,
    impact: "MEDIUM" as Impact,
    urgency: "HIGH" as Urgency,
  },
  {
    name: "Lecture Hall Projector (Hardware Demo)",
    title: "Lecture Hall 2 projector bulb failed before class",
    description:
      "The ceiling mounted Epson projector shows a flashing red lamp indicator and does not turn on.",
    category: "HARDWARE" as Category,
    impact: "MEDIUM" as Impact,
    urgency: "MEDIUM" as Urgency,
  },
];

export default function NewIncidentPage() {
  const { user } = useAuth();
  const router = useRouter();

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState<Category>("NETWORK");
  const [impact, setImpact] = useState<Impact>("HIGH");
  const [urgency, setUrgency] = useState<Urgency>("HIGH");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const applyPreset = (preset: (typeof DEMO_PRESETS)[0]) => {
    setTitle(preset.title);
    setDescription(preset.description);
    setCategory(preset.category);
    setImpact(preset.impact);
    setUrgency(preset.urgency);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !description.trim()) {
      setError("Please provide both title and description.");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const created = await api.createIncident({
        title: title.trim(),
        description: description.trim(),
        category,
        impact,
        urgency,
      });
      router.push(`/incidents/${created.id}`);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to report incident");
    } finally {
      setSubmitting(false);
    }
  };

  const currentPriority = calculateMatrixPriority(impact, urgency);

  return (
    <div style={{ maxWidth: "800px", margin: "0 auto" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <Link
          href="/"
          style={{
            fontSize: "0.8125rem",
            color: "var(--accent)",
            fontWeight: 500,
            display: "inline-block",
            marginBottom: "0.5rem",
          }}
        >
          &larr; Back to Incidents
        </Link>
        <h1
          style={{
            fontSize: "1.5rem",
            fontWeight: 700,
            letterSpacing: "-0.03em",
            color: "var(--text-primary)",
          }}
        >
          Report New Incident
        </h1>
        <p style={{ fontSize: "0.875rem", color: "var(--text-secondary)" }}>
          Submit issue details for automated technician assignment and SLA tracking.
        </p>
      </div>

      {/* Preset Buttons for Demo Ease */}
      <div
        style={{
          backgroundColor: "#f8fafc",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1rem",
          marginBottom: "1.5rem",
        }}
      >
        <div
          style={{
            fontSize: "0.75rem",
            fontWeight: 600,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            color: "var(--text-muted)",
            marginBottom: "0.5rem",
          }}
        >
          Demo Walkthrough Presets (1-Click Fill)
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: "0.5rem" }}>
          {DEMO_PRESETS.map((preset) => (
            <button
              key={preset.name}
              type="button"
              onClick={() => applyPreset(preset)}
              style={{
                fontSize: "0.75rem",
                padding: "0.35rem 0.65rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                backgroundColor: "white",
                color: "var(--text-primary)",
                cursor: "pointer",
                fontWeight: 500,
              }}
            >
              {preset.name}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div
          style={{
            backgroundColor: "#fef2f2",
            color: "#b91c1c",
            padding: "0.6rem 0.85rem",
            borderRadius: "4px",
            fontSize: "0.8125rem",
            marginBottom: "1.25rem",
            border: "1px solid #fecaca",
          }}
        >
          {error}
        </div>
      )}

      <form
        onSubmit={handleSubmit}
        style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-color)",
          borderRadius: "6px",
          padding: "1.5rem",
          display: "flex",
          flexDirection: "column",
          gap: "1.25rem",
        }}
      >
        {/* Title */}
        <div>
          <label
            htmlFor="incident-title"
            style={{
              display: "block",
              fontSize: "0.8125rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Incident Title *
          </label>
          <input
            id="incident-title"
            type="text"
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Switch offline in Lab 304"
            style={{
              width: "100%",
              padding: "0.55rem 0.75rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.875rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
            }}
          />
        </div>

        {/* Description */}
        <div>
          <label
            htmlFor="incident-desc"
            style={{
              display: "block",
              fontSize: "0.8125rem",
              fontWeight: 600,
              color: "var(--text-secondary)",
              marginBottom: "0.25rem",
            }}
          >
            Description & Diagnostic Symptoms *
          </label>
          <textarea
            id="incident-desc"
            rows={4}
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe what occurred, impacted rooms or devices, and troubleshooting steps attempted..."
            style={{
              width: "100%",
              padding: "0.55rem 0.75rem",
              borderRadius: "4px",
              border: "1px solid var(--border-color)",
              fontSize: "0.875rem",
              backgroundColor: "var(--bg-primary)",
              color: "var(--text-primary)",
              fontFamily: "inherit",
            }}
          />
        </div>

        {/* Category, Impact, Urgency row */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
            gap: "1rem",
          }}
        >
          {/* Category */}
          <div>
            <label
              htmlFor="incident-category"
              style={{
                display: "block",
                fontSize: "0.8125rem",
                fontWeight: 600,
                color: "var(--text-secondary)",
                marginBottom: "0.25rem",
              }}
            >
              Category
            </label>
            <select
              id="incident-category"
              value={category}
              onChange={(e) => setCategory(e.target.value as Category)}
              style={{
                width: "100%",
                padding: "0.5rem 0.75rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                fontSize: "0.875rem",
                backgroundColor: "var(--bg-primary)",
                color: "var(--text-primary)",
              }}
            >
              <option value="NETWORK">NETWORK (Routers, Switches, Wi-Fi)</option>
              <option value="HARDWARE">HARDWARE (Desktops, Printers, Servers)</option>
              <option value="SOFTWARE">SOFTWARE (OS, Applications, ERP)</option>
              <option value="ACCESS">ACCESS (Accounts, Permissions, VPN)</option>
            </select>
          </div>

          {/* Impact */}
          <div>
            <label
              htmlFor="incident-impact"
              style={{
                display: "block",
                fontSize: "0.8125rem",
                fontWeight: 600,
                color: "var(--text-secondary)",
                marginBottom: "0.25rem",
              }}
            >
              Impact (Scope of disruption)
            </label>
            <select
              id="incident-impact"
              value={impact}
              onChange={(e) => setImpact(e.target.value as Impact)}
              style={{
                width: "100%",
                padding: "0.5rem 0.75rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                fontSize: "0.875rem",
                backgroundColor: "var(--bg-primary)",
                color: "var(--text-primary)",
              }}
            >
              <option value="HIGH">HIGH (Campus-wide / Multiple Labs)</option>
              <option value="MEDIUM">MEDIUM (Department / Workgroup)</option>
              <option value="LOW">LOW (Single User)</option>
            </select>
          </div>

          {/* Urgency */}
          <div>
            <label
              htmlFor="incident-urgency"
              style={{
                display: "block",
                fontSize: "0.8125rem",
                fontWeight: 600,
                color: "var(--text-secondary)",
                marginBottom: "0.25rem",
              }}
            >
              Urgency (Time criticality)
            </label>
            <select
              id="incident-urgency"
              value={urgency}
              onChange={(e) => setUrgency(e.target.value as Urgency)}
              style={{
                width: "100%",
                padding: "0.5rem 0.75rem",
                borderRadius: "4px",
                border: "1px solid var(--border-color)",
                fontSize: "0.875rem",
                backgroundColor: "var(--bg-primary)",
                color: "var(--text-primary)",
              }}
            >
              <option value="HIGH">HIGH (Critical deadline / Exam in progress)</option>
              <option value="MEDIUM">MEDIUM (Work degraded)</option>
              <option value="LOW">LOW (Workaround available)</option>
            </select>
          </div>
        </div>

        {/* Live Priority Matrix Calculator Display */}
        <PriorityMatrixPreview impact={impact} urgency={urgency} />

        {/* Submit button */}
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            alignItems: "center",
            gap: "1rem",
            marginTop: "0.5rem",
          }}
        >
          <span style={{ fontSize: "0.8125rem", color: "var(--text-muted)" }}>
            Will create ticket with priority <strong>{currentPriority}</strong>
          </span>
          <button
            type="submit"
            disabled={submitting}
            style={{
              padding: "0.6rem 1.25rem",
              backgroundColor: "var(--accent)",
              color: "white",
              borderRadius: "4px",
              fontSize: "0.875rem",
              fontWeight: 600,
              border: "none",
              cursor: "pointer",
              opacity: submitting ? 0.7 : 1,
            }}
          >
            {submitting ? "Submitting Incident..." : "Submit Incident"}
          </button>
        </div>
      </form>
    </div>
  );
}
