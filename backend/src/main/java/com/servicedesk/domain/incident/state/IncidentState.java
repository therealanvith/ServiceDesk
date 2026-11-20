package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;

/** State Pattern interface defining lifecycle transition operations on an incident. */
public interface IncidentState {

  default void assign(Incident context, UserId technicianId, UserId actorId) {
    throw new InvalidStateTransitionException("Cannot assign incident in state: " + getStatus());
  }

  default void acknowledge(Incident context, UserId technicianId) {
    throw new InvalidStateTransitionException(
        "Cannot acknowledge incident in state: " + getStatus());
  }

  default void startWork(Incident context, UserId technicianId) {
    throw new InvalidStateTransitionException(
        "Cannot start work on incident in state: " + getStatus());
  }

  default void requestInfo(Incident context, String question, UserId technicianId) {
    throw new InvalidStateTransitionException(
        "Cannot request information on incident in state: " + getStatus());
  }

  default void provideInfo(Incident context, String answer, UserId reporterId) {
    throw new InvalidStateTransitionException(
        "Cannot provide information on incident in state: " + getStatus());
  }

  default void resolve(Incident context, String resolutionSummary, UserId technicianId) {
    throw new InvalidStateTransitionException("Cannot resolve incident in state: " + getStatus());
  }

  default void close(Incident context, UserId actorId) {
    throw new InvalidStateTransitionException("Cannot close incident in state: " + getStatus());
  }

  default void cancel(Incident context, String reason, UserId actorId) {
    throw new InvalidStateTransitionException("Cannot cancel incident in state: " + getStatus());
  }

  default void reopen(Incident context, UserId actorId) {
    throw new InvalidStateTransitionException("Cannot reopen incident in state: " + getStatus());
  }

  IncidentStatus getStatus();
}
