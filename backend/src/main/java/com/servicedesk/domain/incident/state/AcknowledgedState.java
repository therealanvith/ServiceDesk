package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.util.Objects;

/**
 * The assigned technician has seen the ticket and accepted it, which meets the response SLA, but
 * has not started working on it yet.
 */
public class AcknowledgedState implements IncidentState {

  @Override
  public void startWork(Incident context, UserId technicianId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    context.transitionTo(new InProgressState());
  }

  /** Reassigning hands the ticket to someone who has not accepted it yet. */
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
  public IncidentStatus getStatus() {
    return IncidentStatus.ACKNOWLEDGED;
  }
}
