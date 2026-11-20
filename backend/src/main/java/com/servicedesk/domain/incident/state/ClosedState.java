package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentStatus;

/**
 * Terminal state when an incident has been verified and permanently closed. Strictly immutable; no
 * transitions permitted.
 */
public class ClosedState implements IncidentState {

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.CLOSED;
  }
}
