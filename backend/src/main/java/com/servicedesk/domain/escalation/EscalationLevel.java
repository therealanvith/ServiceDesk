package com.servicedesk.domain.escalation;

/** How far up the support hierarchy a ticket has been escalated. */
public enum EscalationLevel {
  NONE,
  /** L1: the response target passed without the ticket being acknowledged. */
  TEAM_LEAD,
  /** L2: still not acknowledged one more response target later; administrators reassign it. */
  ADMINISTRATOR,
  /** L3: a P1 or P2 ticket breached its resolution target. */
  IT_MANAGER;

  public boolean isAbove(EscalationLevel other) {
    return ordinal() > other.ordinal();
  }
}
