package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/** Initial state of an incident upon creation. */
public class ReportedState implements IncidentState {

  @Override
  public void assign(Incident context, UserId technicianId, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Objects.requireNonNull(technicianId, "Technician ID must not be null");
    context.setAssigneeId(technicianId);
    context.transitionTo(new AssignedState());
  }

  @Override
  public void cancel(Incident context, String reason, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.transitionTo(new CancelledState());
  }

  @Override
  public void resolveWithParent(Incident context, String resolutionSummary) {
    ParentResolution.apply(context, resolutionSummary);
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.REPORTED;
  }
}
