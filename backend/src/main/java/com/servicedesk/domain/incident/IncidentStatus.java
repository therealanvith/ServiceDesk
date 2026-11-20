package com.servicedesk.domain.incident;

/** Incident lifecycle statuses governed by the State pattern. */
public enum IncidentStatus {
  REPORTED,
  ASSIGNED,
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
}
