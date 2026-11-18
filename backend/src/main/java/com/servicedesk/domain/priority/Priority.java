package com.servicedesk.domain.priority;

/**
 * Incident priority classification levels.
 */
public enum Priority {
  P1_CRITICAL(1),
  P2_HIGH(2),
  P3_MEDIUM(3),
  P4_LOW(4);

  private final int rank;

  Priority(int rank) {
    this.rank = rank;
  }

  public int getRank() {
    return rank;
  }

  /**
   * Escalates priority by one level towards P1_CRITICAL.
   * If already P1_CRITICAL, remains P1_CRITICAL.
   */
  public Priority escalate() {
    return switch (this) {
      case P4_LOW -> P3_MEDIUM;
      case P3_MEDIUM -> P2_HIGH;
      case P2_HIGH, P1_CRITICAL -> P1_CRITICAL;
    };
  }
}
