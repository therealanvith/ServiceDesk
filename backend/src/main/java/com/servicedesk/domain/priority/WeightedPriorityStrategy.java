package com.servicedesk.domain.priority;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.service.BusinessCriticality;
import java.util.Objects;

/**
 * Weighted scoring that combines impact, urgency and business criticality.
 *
 * <pre>
 * score = 2 x impact + urgency + criticality adjustment + affected-users bonus
 *   impact / urgency: HIGH = 3, MEDIUM = 2, LOW = 1
 *   criticality:      CRITICAL +2, HIGH +1, MEDIUM 0, LOW -1
 *   affected users:   3 or more +1, 10 or more +2 (a single person adds nothing)
 *
 * score >= 8 -> P1,  7 -> P2,  5..6 -> P3,  <= 4 -> P4
 * </pre>
 *
 * <p>Impact is weighted double because it measures how many people are affected, which matters more
 * than how quickly one person needs a fix. With a MEDIUM-criticality service the result is
 * identical to {@link MatrixPriorityStrategy}, so criticality only ever shifts a ticket relative to
 * that well-understood baseline.
 */
public class WeightedPriorityStrategy implements PriorityCalculationStrategy {

  @Override
  public Priority calculatePriority(Impact impact, Urgency urgency) {
    return calculatePriority(impact, urgency, BusinessCriticality.MEDIUM);
  }

  @Override
  public Priority calculatePriority(
      Impact impact, Urgency urgency, BusinessCriticality criticality) {
    return calculatePriority(impact, urgency, criticality, 1);
  }

  @Override
  public Priority calculatePriority(
      Impact impact, Urgency urgency, BusinessCriticality criticality, int affectedUsers) {
    Objects.requireNonNull(impact, "Impact must not be null");
    Objects.requireNonNull(urgency, "Urgency must not be null");
    BusinessCriticality effective = criticality != null ? criticality : BusinessCriticality.MEDIUM;

    int score =
        2 * weight(impact.name())
            + weight(urgency.name())
            + effective.getPriorityAdjustment()
            + affectedBonus(affectedUsers);
    if (score >= 8) {
      return Priority.P1_CRITICAL;
    }
    if (score == 7) {
      return Priority.P2_HIGH;
    }
    if (score >= 5) {
      return Priority.P3_MEDIUM;
    }
    return Priority.P4_LOW;
  }

  private static int affectedBonus(int affectedUsers) {
    if (affectedUsers >= 10) {
      return 2;
    }
    return affectedUsers >= 3 ? 1 : 0;
  }

  private static int weight(String level) {
    return switch (level) {
      case "HIGH" -> 3;
      case "MEDIUM" -> 2;
      default -> 1;
    };
  }
}
