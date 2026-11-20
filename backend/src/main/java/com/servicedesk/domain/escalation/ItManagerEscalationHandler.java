package com.servicedesk.domain.escalation;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import java.time.Instant;

/** L3: a critical or high-priority ticket breached its resolution target. */
public class ItManagerEscalationHandler extends EscalationHandler {

  @Override
  protected EscalationLevel level() {
    return EscalationLevel.IT_MANAGER;
  }

  @Override
  protected boolean applies(Incident incident, Instant now) {
    // The breach bumps P3 to P2; judge by the priority the ticket had when it breached.
    Priority priority =
        incident.getPriorityAtBreach() != null
            ? incident.getPriorityAtBreach()
            : incident.getPriority();
    return incident.getStatus().isActive()
        && incident.getSlaWindow() != null
        && incident.getSlaWindow().getStatus() == SlaStatus.BREACHED
        && (priority == Priority.P1_CRITICAL || priority == Priority.P2_HIGH);
  }

  @Override
  protected String reason(Incident incident) {
    return incident.getPriority() + " ticket breached its resolution target";
  }
}
