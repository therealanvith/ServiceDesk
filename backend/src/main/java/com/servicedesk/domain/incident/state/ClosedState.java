package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Terminal state when an incident has been verified and closed. Nothing changes it, except that the
 * reporter may reopen it within {@link #REOPEN_WINDOW} if the fix turns out not to hold.
 */
public class ClosedState implements IncidentState {

  /** How long after closing a ticket can still be reopened instead of reported again. */
  public static final Duration REOPEN_WINDOW = Duration.ofDays(14);

  @Override
  public void reopen(Incident context, UserId actorId) {
    Objects.requireNonNull(context, "Incident context must not be null");
    Instant closedAt = context.getClosedAt();
    if (closedAt == null || context.getCurrentClockTime().isAfter(closedAt.plus(REOPEN_WINDOW))) {
      throw new InvalidStateTransitionException(
          "This ticket was closed more than "
              + REOPEN_WINDOW.toDays()
              + " days ago. Please report a new issue instead.");
    }
    context.setClosedAt(null);
    context.setResolvedAt(null);
    context.setResolutionSummary(null);
    // The rating was for a fix that didn't hold; the audit trail keeps it.
    context.recordFeedback(null, null);
    context.transitionTo(new InProgressState());
  }

  @Override
  public IncidentStatus getStatus() {
    return IncidentStatus.CLOSED;
  }
}
