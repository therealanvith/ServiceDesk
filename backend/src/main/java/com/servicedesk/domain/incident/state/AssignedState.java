package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/**
 * State when an incident has been assigned to a technician.
 */
public class AssignedState implements IncidentState {

  @Override
  public void startWork(Incident context, UserId technicianId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.transitionTo(new InProgressState());
  }

  @Override
  public void assign(Incident context, UserId technicianId, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Objects.requireNonNull(technicianId, "Technician ID must not be null");
    context.setAssigneeId(technicianId);
    // Remains in AssignedState with updated assignee
  }

  @Override
  public void cancel(Incident context, String reason, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.transitionTo(new CancelledState());
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.ASSIGNED;
  }
}
