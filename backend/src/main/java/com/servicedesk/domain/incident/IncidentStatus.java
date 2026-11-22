package com.servicedesk.domain.incident;

import java.util.EnumSet;
import java.util.Set;

/** Incident lifecycle statuses governed by the State pattern. */
public enum IncidentStatus {
  REPORTED,
  ASSIGNED,
  ACKNOWLEDGED,
  IN_PROGRESS,
  PENDING_INFO,
  RESOLVED,
  CLOSED,
  CANCELLED;

  public boolean isTerminal() {
    return this == CLOSED || this == CANCELLED;
  }

  public boolean isActive() {
    return this != CLOSED && this != CANCELLED;
  }

  /**
   * Statuses that do not count towards a technician's workload: the work is done, or the ticket
   * only waits for the reporter to confirm the fix.
   */
  public static Set<IncidentStatus> notWorkload() {
    return EnumSet.of(RESOLVED, CLOSED, CANCELLED);
  }
}
