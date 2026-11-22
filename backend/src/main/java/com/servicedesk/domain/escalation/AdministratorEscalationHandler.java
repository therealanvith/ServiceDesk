package com.servicedesk.domain.escalation;

import com.servicedesk.domain.incident.Incident;
import java.time.Duration;
import java.time.Instant;

/** L2: still not acknowledged one more response target after the deadline. */
public class AdministratorEscalationHandler extends EscalationHandler {

  @Override
  protected EscalationLevel level() {
    return EscalationLevel.ADMINISTRATOR;
  }

  @Override
  protected boolean applies(Incident incident, Instant now) {
    if (!incident.isResponseOverdue(now)) {
      return false;
    }
    Instant deadline = incident.getSlaWindow().getResponseDeadline();
    Duration target = Duration.between(incident.getCreatedAt(), deadline);
    return now.isAfter(deadline.plus(target));
  }

  @Override
  protected String reason(Incident incident) {
    return "Still not acknowledged twice the response target after it was reported";
  }
}
