package com.servicedesk.domain.escalation;

import com.servicedesk.domain.incident.Incident;
import java.time.Instant;

/** L1: nobody acknowledged the ticket by its response deadline. */
public class TeamLeadEscalationHandler extends EscalationHandler {

  @Override
  protected EscalationLevel level() {
    return EscalationLevel.TEAM_LEAD;
  }

  @Override
  protected boolean applies(Incident incident, Instant now) {
    return incident.isResponseOverdue(now);
  }

  @Override
  protected String reason(Incident incident) {
    return incident.getAssigneeId() == null
        ? "No technician acknowledged it within the response target"
        : "The assigned technician did not acknowledge it within the response target";
  }
}
