package com.servicedesk.domain.event;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an incident transitions between lifecycle states.
 */
public record IncidentStatusChangedEvent(
    IncidentId incidentId,
    IncidentStatus oldStatus,
    IncidentStatus newStatus,
    UserId actorId,
    Instant timestamp)
    implements DomainEvent {

  public IncidentStatusChangedEvent {
    Objects.requireNonNull(incidentId, "Incident ID must not be null");
    Objects.requireNonNull(oldStatus, "Old status must not be null");
    Objects.requireNonNull(newStatus, "New status must not be null");
    Objects.requireNonNull(timestamp, "Timestamp must not be null");
  }

  @Override
  public Instant occurredOn() {
    return timestamp;
  }
}
