package com.servicedesk.domain.sla;

/**
 * Service Level Agreement compliance status.
 */
public enum SlaStatus {
  WITHIN_SLA,
  WARNING_BREACH_IMMINENT,
  BREACHED
}
