package com.servicedesk.domain.service;

/**
 * How important an IT service is to the organisation. Feeds into incident priority: an outage of
 * a critical service (payments, sign-in) outranks the same symptoms on a low-criticality one.
 */
public enum BusinessCriticality {
  LOW(-1),
  MEDIUM(0),
  HIGH(1),
  CRITICAL(2);

  private final int priorityAdjustment;

  BusinessCriticality(int priorityAdjustment) {
    this.priorityAdjustment = priorityAdjustment;
  }

  /** Points added to the impact/urgency score; MEDIUM is neutral. */
  public int getPriorityAdjustment() {
    return priorityAdjustment;
  }
}
