package com.servicedesk.domain.escalation;

/** Builds the standard escalation chain: team lead, then administrators, then the IT manager. */
public final class EscalationChain {

  private EscalationChain() {}

  public static EscalationHandler standard() {
    EscalationHandler head = new TeamLeadEscalationHandler();
    head.linkWith(new AdministratorEscalationHandler()).linkWith(new ItManagerEscalationHandler());
    return head;
  }
}
