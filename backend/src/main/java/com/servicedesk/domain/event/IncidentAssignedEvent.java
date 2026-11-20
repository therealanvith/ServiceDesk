package com.servicedesk.domain.event;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/** Event published when an incident is assigned or reassigned to a technician. */
public record IncidentAssignedEvent(
    IncidentId incidentId, UserId technicianId, UserId actorId, Instant timestamp)
    implements DomainEvent {

  public IncidentAssignedEvent {
    Objects.requireNonNull(incidentId, "Incident ID must not be null");
    Objects.requireNonNull(technicianId, "Technician ID must not be null");
    Objects.requireNonNull(timestamp, "Timestamp must not be null");
  }

  @Override
  public Instant occurredOn() {
    return timestamp;
  }
}
