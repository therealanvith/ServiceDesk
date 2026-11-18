package com.servicedesk.domain.priority;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import java.util.Objects;

/**
 * 3x3 Impact x Urgency matrix priority calculation strategy.
 *
 * <pre>
 * Impact \ Urgency | HIGH        | MEDIUM      | LOW
 * -----------------+-------------+-------------+------------
 * HIGH             | P1_CRITICAL | P1_CRITICAL | P2_HIGH
 * MEDIUM           | P2_HIGH     | P3_MEDIUM   | P3_MEDIUM
 * LOW              | P3_MEDIUM   | P4_LOW      | P4_LOW
 * </pre>
 */
public class MatrixPriorityStrategy implements PriorityCalculationStrategy {

  @Override
  public Priority calculatePriority(Impact impact, Urgency urgency) {
    Objects.requireNonNull(impact, "Impact must not be null");
    Objects.requireNonNull(urgency, "Urgency must not be null");

    return switch (impact) {
      case HIGH -> switch (urgency) {
        case HIGH, MEDIUM -> Priority.P1_CRITICAL;
        case LOW -> Priority.P2_HIGH;
      };
      case MEDIUM -> switch (urgency) {
        case HIGH -> Priority.P2_HIGH;
        case MEDIUM, LOW -> Priority.P3_MEDIUM;
      };
      case LOW -> switch (urgency) {
        case HIGH -> Priority.P3_MEDIUM;
        case MEDIUM, LOW -> Priority.P4_LOW;
      };
    };
  }
}
