package com.servicedesk.domain.escalation;

import java.util.Objects;

/** The level a ticket should be raised to, and why. */
public record EscalationDecision(EscalationLevel level, String reason) {

  public EscalationDecision {
    Objects.requireNonNull(level, "Escalation level must not be null");
    Objects.requireNonNull(reason, "Escalation reason must not be null");
  }
}
