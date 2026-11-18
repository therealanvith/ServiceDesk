package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentStatus;

/**
 * Terminal state when an incident has been cancelled.
 * Strictly immutable; no transitions permitted.
 */
public class CancelledState implements IncidentState {

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.CANCELLED;
  }
}
