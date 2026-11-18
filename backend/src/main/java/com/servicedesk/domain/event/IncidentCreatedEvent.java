package com.servicedesk.domain.event;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a new incident is created.
 */
public record IncidentCreatedEvent(
    IncidentId incidentId,
    UserId reporterId,
    Priority priority,
    Instant timestamp)
    implements DomainEvent {

  public IncidentCreatedEvent {
    Objects.requireNonNull(incidentId, "Incident ID must not be null");
    Objects.requireNonNull(reporterId, "Reporter ID must not be null");
    Objects.requireNonNull(priority, "Priority must not be null");
    Objects.requireNonNull(timestamp, "Timestamp must not be null");
  }

  @Override
  public Instant occurredOn() {
    return timestamp;
  }
}
