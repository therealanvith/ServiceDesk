package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/**
 * State when an incident has been resolved and is pending reporter verification or timeout closure.
 */
public class ResolvedState implements IncidentState {

  @Override
  public void close(Incident context, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.setClosedAt(context.getCurrentClockTime());
    context.transitionTo(new ClosedState());
  }

  @Override
  public void reopen(Incident context, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.setResolvedAt(null);
    context.setResolutionSummary(null);
    context.transitionTo(new InProgressState());
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.RESOLVED;
  }
}
