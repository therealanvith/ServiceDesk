package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/**
 * State when an incident is awaiting information from the reporter. SLA resolution timer is paused
 * in this state.
 */
public class PendingInfoState implements IncidentState {

  @Override
  public void provideInfo(Incident context, String answer, UserId reporterId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    if (context.getSlaWindow() != null) {
      context.getSlaWindow().resume(context.getCurrentClockTime());
    }
    context.transitionTo(new InProgressState());
  }

  /** Stops the paused SLA clock before resolving, so the pause is counted correctly. */
  @Override
  public void resolveWithParent(Incident context, String resolutionSummary) {
    if (context.getSlaWindow() != null) {
      context.getSlaWindow().resume(context.getCurrentClockTime());
    }
    ParentResolution.apply(context, resolutionSummary);
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.PENDING_INFO;
  }
}
