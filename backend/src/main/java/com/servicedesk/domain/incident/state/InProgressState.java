package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/** State representing active remediation work on an incident. */
public class InProgressState implements IncidentState {

  @Override
  public void requestInfo(Incident context, String question, UserId technicianId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    if (context.getSlaWindow() != null) {
      context.getSlaWindow().pause(context.getCurrentClockTime());
    }
    context.transitionTo(new PendingInfoState());
  }

  @Override
  public void resolve(Incident context, String resolutionSummary, UserId technicianId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Objects.requireNonNull(resolutionSummary, "Resolution summary must not be null");
    context.setResolutionSummary(resolutionSummary);
    context.setResolvedAt(context.getCurrentClockTime());
    context.transitionTo(new ResolvedState());
  }

  @Override
  public void assign(Incident context, UserId technicianId, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Objects.requireNonNull(technicianId, "Technician ID must not be null");
    context.setAssigneeId(technicianId);
    context.transitionTo(new AssignedState());
  }

  @Override
  public void resolveWithParent(Incident context, String resolutionSummary) {
    ParentResolution.apply(context, resolutionSummary);
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.IN_PROGRESS;
  }
}
