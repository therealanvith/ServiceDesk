"use client";

import { ServiceCatalogSettings } from "@/components/settings/ServiceCatalogSettings";
import { SlaPolicySettings } from "@/components/settings/SlaPolicySettings";
import { useAuth } from "@/context/AuthContext";
import {
  type Category,
  type SlaEvaluationSummary,
  type TechnicianItem,
  api,
} from "@/lib/apiClient";
import { CATEGORY_LABELS, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";

const STRATEGIES = [
  {
    id: "SKILL_MATCH",
    name: "Match skills",
    description:
      "Send tickets to a technician with the matching skill. If several match, the least busy one gets it.",
  },
  {
    id: "LEAST_LOADED",
    name: "Least busy",
    description: "Send tickets to whoever has the fewest open tickets.",
  },
  {
    id: "ROUND_ROBIN",
    name: "Take turns",
    description: "Rotate through technicians in order.",
  },
] as const;

const ALL_CATEGORIES = Object.keys(CATEGORY_LABELS) as Category[];
const LIMIT_OPTIONS = [1, 2, 3, 4, 5, 6, 8, 10, 15, 20];

export default function AdminPage() {
  const { user, loading: authLoading } = useAuth();

  const [activeStrategy, setActiveStrategy] = useState<string | null>(null);
  const [selectedStrategy, setSelectedStrategy] = useState<string | null>(null);
  const [technicians, setTechnicians] = useState<TechnicianItem[]>([]);
  const [techniciansFailed, setTechniciansFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  // Technicians with a skill save in flight, and a counter of saves, so a slower list refresh
  // can never put back skills that were changed after it started.
  const [pendingSkills, setPendingSkills] = useState<Set<string>>(new Set());
  const skillSaves = useRef(0);
  const [loading, setLoading] = useState(true);
  const [savingStrategy, setSavingStrategy] = useState(false);
  const [evaluatingSla, setEvaluatingSla] = useState(false);
  const [slaResult, setSlaResult] = useState<SlaEvaluationSummary | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // biome-ignore lint/correctness/useExhaustiveDependencies: reloadKey re-runs the load for "Try again"
  useEffect(() => {
    if (!isAdmin(user?.role)) return;
    let isMounted = true;
    setLoading(true);
    setError(null);
    Promise.allSettled([api.getAssignmentConfig(), api.listTechnicians()])
      .then(([configRes, techRes]) => {
        if (!isMounted) return;
        if (configRes.status === "rejected") {
          setError("Couldn’t load the current assignment rule.");
        }
        setTechniciansFailed(techRes.status === "rejected");
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
  }, [user, reloadKey]);

  const handleSaveStrategy = async () => {
    setSavingStrategy(true);
    setError(null);
    setMessage(null);
    try {
      if (!selectedStrategy) return;
      const res = await api.updateAssignmentConfig(selectedStrategy);
      setActiveStrategy(res.activeStrategy);
      const name = STRATEGIES.find((s) => s.id === res.activeStrategy)?.name ?? res.activeStrategy;
      setMessage(`New tickets will now be assigned using “${name}”.`);
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
      setSlaResult(await api.triggerSlaEvaluation());
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to run SLA check");
      setEvaluatingSla(false);
      return;
    }
    setEvaluatingSla(false);

    // Refresh workloads; keep skills that were edited (or are being saved) since it started.
    const savesAtStart = skillSaves.current;
    try {
      const fresh = await api.listTechnicians();
      setTechnicians((current) =>
        fresh.map((tech) => {
          const mine = current.find((t) => t.id === tech.id);
          const editedSince = skillSaves.current !== savesAtStart;
          return mine && (editedSince || pendingSkills.has(tech.id))
            ? { ...tech, skills: mine.skills }
            : tech;
        })
      );
      setTechniciansFailed(false);
    } catch {
      setError("The SLA check ran, but the technician list couldn’t be refreshed.");
    }
  };

  // One skill save per technician at a time: each PUT replaces the whole list, so overlapping
  // saves could undo each other. The buttons for that technician are disabled meanwhile.
  const handleToggleSkill = async (tech: TechnicianItem, cat: Category) => {
    if (pendingSkills.has(tech.id)) return;
    const hasSkill = tech.skills.includes(cat);
    const newSkills = hasSkill ? tech.skills.filter((s) => s !== cat) : [...tech.skills, cat];

    skillSaves.current += 1;
    setPendingSkills((prev) => new Set(prev).add(tech.id));
    try {
      const updated = await api.updateTechnicianSkills(tech.id, newSkills);
      setTechnicians((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update skills");
    } finally {
      setPendingSkills((prev) => {
        const next = new Set(prev);
        next.delete(tech.id);
        return next;
      });
    }
  };

  // Ticket limit and team-lead flag share the per-technician lock with skills.
  const handleCapacity = async (
    tech: TechnicianItem,
    maxActiveTickets: number,
    teamLead: boolean
  ) => {
    if (pendingSkills.has(tech.id)) return;
    skillSaves.current += 1;
    setPendingSkills((prev) => new Set(prev).add(tech.id));
    try {
      const updated = await api.updateCapacity(tech.id, maxActiveTickets, teamLead);
      setTechnicians((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to update the ticket limit");
    } finally {
      setPendingSkills((prev) => {
        const next = new Set(prev);
        next.delete(tech.id);
        return next;
      });
    }
  };

  if (authLoading || (isAdmin(user?.role) && loading)) {
    return <output className="empty">Loading settings…</output>;
  }

  if (!isAdmin(user?.role)) {
    return (
      <div className="card empty">
        <h2>Admins only</h2>
        <p>Sign in as an administrator to change these settings.</p>
        <Link className="btn" href="/">
          Back to incidents
        </Link>
      </div>
    );
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Settings</h1>
          <p>How tickets are assigned and who can work on what.</p>
        </div>
      </div>

      {(message || error) && (
        <p
          className={`alert ${error ? "" : "alert-success"}`}
          role={error ? "alert" : "status"}
          style={{ marginBottom: 20 }}
        >
          {error ?? message}
        </p>
      )}

      <section className="settings-section">
        <div>
          <h2>Automatic assignment</h2>
          <p>Used when a ticket is assigned without picking a technician. Applies immediately.</p>
        </div>
        <div>
          <fieldset className="card radio-list">
            <legend className="sr-only">Assignment rule</legend>
            {STRATEGIES.map((strat) => (
              <label key={strat.id} className="radio-row">
                <input
                  type="radio"
                  name="strategy"
                  value={strat.id}
                  checked={selectedStrategy === strat.id}
                  onChange={(e) => setSelectedStrategy(e.target.value)}
                />
                <span>
                  <span className="radio-title">{strat.name}</span>
                  <br />
                  <span className="radio-desc">{strat.description}</span>
                </span>
                {activeStrategy === strat.id && <span className="tag">Current</span>}
              </label>
            ))}
          </fieldset>
          <div className="row-end" style={{ marginTop: 12 }}>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSaveStrategy}
              disabled={!activeStrategy || savingStrategy || selectedStrategy === activeStrategy}
            >
              {savingStrategy ? "Saving…" : "Save"}
            </button>
          </div>
        </div>
      </section>

      <section className="settings-section">
        <div>
          <h2>SLA targets</h2>
          <p>
            How quickly each priority must get a first response and a fix. Changes apply to new
            tickets; existing deadlines stay as promised.
          </p>
        </div>
        <SlaPolicySettings onError={setError} />
      </section>

      <section className="settings-section">
        <div>
          <h2>IT services</h2>
          <p>
            What people can report problems against. Criticality raises or lowers the priority of
            tickets on that service. Retire a service instead of deleting it to keep its history.
          </p>
        </div>
        <ServiceCatalogSettings onError={setError} />
      </section>

      <section className="settings-section">
        <div>
          <h2>SLA check</h2>
          <p>
            Runs every minute in the background. Tickets nearing their deadline warn the assignee,
            overdue tickets are raised one priority level, and tickets with no response by the
            response target are escalated to administrators.
          </p>
        </div>
        <div>
          <button type="button" className="btn" onClick={handleTriggerSla} disabled={evaluatingSla}>
            {evaluatingSla ? "Checking…" : "Run check now"}
          </button>
          {slaResult && (
            <div className="stats">
              <div>
                <span className="stat-value">{slaResult.evaluatedCount}</span>
                <span className="stat-label">Open tickets checked</span>
              </div>
              <div>
                <span
                  className="stat-value"
                  style={{ color: slaResult.breachedCount > 0 ? "var(--red)" : undefined }}
                >
                  {slaResult.breachedCount}
                </span>
                <span className="stat-label">Newly overdue</span>
              </div>
              <div>
                <span className="stat-value">
                  {new Date(slaResult.evaluatedAt).toLocaleTimeString(undefined, {
                    hour: "numeric",
                    minute: "2-digit",
                  })}
                </span>
                <span className="stat-label">Last run</span>
              </div>
            </div>
          )}
        </div>
      </section>

      <section className="settings-section">
        <div>
          <h2>Technicians</h2>
          <p>Click a skill to turn it on or off. Skills decide who gets matched to a ticket.</p>
        </div>
        {techniciansFailed ? (
          <div className="card empty" role="alert">
            <h2>Couldn’t load technicians</h2>
            <button type="button" className="btn" onClick={() => setReloadKey((k) => k + 1)}>
              Try again
            </button>
          </div>
        ) : (
          <div className="card table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Open / limit</th>
                  <th scope="col">Skills</th>
                  <th scope="col">Team lead</th>
                </tr>
              </thead>
              <tbody>
                {technicians.map((tech) => (
                  <tr key={tech.id}>
                    <td>
                      <span style={{ fontWeight: 550 }}>{tech.fullName}</span>
                      <span className="props-sub mono">{tech.username}</span>
                    </td>
                    <td className="load col-nowrap">
                      <span
                        className={
                          tech.activeTicketsCount >= tech.maxActiveTickets ? "text-red" : undefined
                        }
                      >
                        {tech.activeTicketsCount}
                      </span>{" "}
                      /{" "}
                      <select
                        className="filter-select"
                        aria-label={`Ticket limit for ${tech.fullName}`}
                        value={tech.maxActiveTickets}
                        disabled={pendingSkills.has(tech.id)}
                        onChange={(e) =>
                          handleCapacity(tech, Number(e.target.value), tech.teamLead)
                        }
                      >
                        {LIMIT_OPTIONS.includes(tech.maxActiveTickets) ? null : (
                          <option value={tech.maxActiveTickets}>{tech.maxActiveTickets}</option>
                        )}
                        {LIMIT_OPTIONS.map((n) => (
                          <option key={n} value={n}>
                            {n}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <div className="row" style={{ gap: 4 }}>
                        {ALL_CATEGORIES.map((cat) => (
                          <button
                            key={cat}
                            type="button"
                            className="skill-toggle"
                            aria-pressed={tech.skills.includes(cat)}
                            disabled={pendingSkills.has(tech.id)}
                            onClick={() => handleToggleSkill(tech, cat)}
                          >
                            {CATEGORY_LABELS[cat]}
                          </button>
                        ))}
                      </div>
                    </td>
                    <td>
                      <label className="checkbox">
                        <input
                          type="checkbox"
                          checked={tech.teamLead}
                          disabled={pendingSkills.has(tech.id)}
                          onChange={(e) =>
                            handleCapacity(tech, tech.maxActiveTickets, e.target.checked)
                          }
                        />
                        <span className="sr-only">{tech.fullName} is a team lead</span>
                      </label>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
