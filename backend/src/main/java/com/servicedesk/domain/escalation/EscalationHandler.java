package com.servicedesk.domain.escalation;

import com.servicedesk.domain.incident.Incident;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Chain of Responsibility: each handler owns one escalation level. A handler that applies to the
 * ticket answers; otherwise it passes the ticket to the next handler. A ticket already at or above
 * a handler's level is passed on, so each evaluation raises it by at most one step.
 */
public abstract class EscalationHandler {

  private EscalationHandler next;

  /** Links the next handler and returns it, so chains read left to right. */
  public EscalationHandler linkWith(EscalationHandler next) {
    this.next = Objects.requireNonNull(next, "Next handler must not be null");
    return next;
  }

  public Optional<EscalationDecision> handle(Incident incident, Instant now) {
    if (level().isAbove(incident.getEscalationLevel()) && applies(incident, now)) {
      return Optional.of(new EscalationDecision(level(), reason(incident)));
    }
    return next == null ? Optional.empty() : next.handle(incident, now);
  }

  protected abstract EscalationLevel level();

  protected abstract boolean applies(Incident incident, Instant now);

  protected abstract String reason(Incident incident);
}
