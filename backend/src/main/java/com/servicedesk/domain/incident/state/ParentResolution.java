package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import java.util.Objects;

/** Shared by every open state: the linked major incident was fixed, so this ticket is too. */
final class ParentResolution {

  private ParentResolution() {}

  static void apply(Incident context, String resolutionSummary) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Objects.requireNonNull(resolutionSummary, "Resolution summary must not be null");
    context.setResolutionSummary(resolutionSummary);
    context.setResolvedAt(context.getCurrentClockTime());
    context.transitionTo(new ResolvedState());
  }
}
