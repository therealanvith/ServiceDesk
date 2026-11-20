"use client";

import { useAuth } from "@/context/AuthContext";
import { type ServiceHealth, type ServiceStatus, api } from "@/lib/apiClient";
import { CRITICALITY_LABELS, SERVICE_HEALTH_LABELS } from "@/lib/labels";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";

/** Affected services first, so a problem is never below the fold. */
const SEVERITY: Record<ServiceHealth, number> = { OUTAGE: 0, DEGRADED: 1, OPERATIONAL: 2 };

/** Health of every service, from open tickets (counts only), for every signed-in user. */
export default function ServiceStatusPage() {
  const { user, loading: authLoading } = useAuth();
  const [services, setServices] = useState<ServiceStatus[] | null>(null);
  const [failed, setFailed] = useState(false);
  const requestVersion = useRef(0);

  // Keeps the current rows on screen while it refreshes; only the latest answer is shown.
  const load = useCallback(() => {
    const version = ++requestVersion.current;
    api
      .getServiceStatus()
      .then((res) => {
        if (version !== requestVersion.current) return;
        setServices(res);
        setFailed(false);
      })
      .catch(() => version === requestVersion.current && setFailed(true));
  }, []);

  useEffect(() => {
    if (user) load();
  }, [user, load]);

  // Any ticket change can move a service between states; reconnecting may have missed some.
  useLiveEvent((e) => {
    if (e.event === "incident-updated" || e.event === "ready") load();
  });

  if (authLoading || !user) return <output className="empty">Loading…</output>;

  const sorted = services
    ? [...services].sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status])
    : null;
  const affected = sorted?.filter((s) => s.status !== "OPERATIONAL").length ?? 0;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>Service status</h1>
          <p>Worked out from open tickets. Updates live.</p>
        </div>
        <Link className="btn" href="/issues">
          All issues
        </Link>
      </div>

      <section className="card" aria-label="Service status">
        {failed && !sorted ? (
          <div className="empty" role="alert">
            <h2>Couldn’t load service status</h2>
            <button type="button" className="btn" onClick={load}>
              Try again
            </button>
          </div>
        ) : !sorted ? (
          <output className="empty">Loading…</output>
        ) : sorted.length === 0 ? (
          <div className="empty">
            <h2>No services yet</h2>
            <p>Administrators add services in Settings.</p>
          </div>
        ) : (
          <>
            <output className={`status-summary ${affected ? "is-affected" : ""}`}>
              {affected === 0
                ? "All services operational"
                : `${affected} service${affected === 1 ? "" : "s"} affected`}
              {failed && <span className="muted"> · Couldn’t refresh, showing the last check</span>}
            </output>
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Service</th>
                    <th scope="col">Status</th>
                    <th scope="col" className="hide-sm">
                      Criticality
                    </th>
                    <th scope="col" className="col-num">
                      Open tickets
                    </th>
                    <th scope="col" className="col-num">
                      Major incidents
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {sorted.map((s) => (
                    <tr key={s.serviceId}>
                      <td>
                        {s.status === "OPERATIONAL" ? (
                          s.name
                        ) : (
                          <Link
                            className="link"
                            href={`/issues?${new URLSearchParams({ serviceId: s.serviceId })}`}
                          >
                            {s.name}
                          </Link>
                        )}
                      </td>
                      <td>
                        <ServiceHealthChip status={s.status} />
                      </td>
                      <td className="hide-sm muted">{CRITICALITY_LABELS[s.criticality]}</td>
                      <td className="col-num">{s.openTickets}</td>
                      <td className="col-num">{s.openMajorIncidents}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
    </>
  );
}

/** Colour plus a text label, so the state never depends on colour alone. */
function ServiceHealthChip({ status }: { status: ServiceHealth }) {
  return (
    <span className={`health health-${status.toLowerCase()}`}>{SERVICE_HEALTH_LABELS[status]}</span>
  );
}
