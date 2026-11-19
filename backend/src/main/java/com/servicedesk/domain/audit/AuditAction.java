package com.servicedesk.domain.audit;

/**
 * Audit actions recorded in the immutable audit ledger.
 */
public enum AuditAction {
  CREATED,
  STATUS_CHANGED,
  ASSIGNED,
  PRIORITY_OVERRIDDEN,
  SLA_BREACHED,
  ESCALATED,
  COMMENTED
}
