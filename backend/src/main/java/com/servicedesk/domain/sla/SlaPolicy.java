package com.servicedesk.domain.sla;

import com.servicedesk.domain.priority.Priority;
import java.time.Duration;
import java.util.Objects;

/** Response and resolution targets for one priority level. */
public record SlaPolicy(Priority priority, Duration responseTarget, Duration resolutionTarget) {

  public SlaPolicy {
    Objects.requireNonNull(priority, "Priority must not be null");
    Objects.requireNonNull(responseTarget, "Response target must not be null");
    Objects.requireNonNull(resolutionTarget, "Resolution target must not be null");
    if (responseTarget.isNegative() || responseTarget.isZero()) {
      throw new IllegalArgumentException("Response target must be positive");
    }
    if (resolutionTarget.compareTo(responseTarget) < 0) {
      throw new IllegalArgumentException(
          "Resolution target cannot be shorter than response target");
    }
  }

  /** Default targets, used until an administrator changes them. */
  public static SlaPolicy defaultFor(Priority priority) {
    return switch (priority) {
      case P1_CRITICAL -> new SlaPolicy(priority, Duration.ofMinutes(5), Duration.ofMinutes(30));
      case P2_HIGH -> new SlaPolicy(priority, Duration.ofMinutes(15), Duration.ofHours(2));
      case P3_MEDIUM -> new SlaPolicy(priority, Duration.ofHours(1), Duration.ofHours(8));
      case P4_LOW -> new SlaPolicy(priority, Duration.ofHours(4), Duration.ofHours(24));
    };
  }
}
