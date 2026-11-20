package com.servicedesk.domain.event;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.priority.Priority;
import java.time.Instant;
import java.util.Objects;

/** Event published when an incident resolution deadline is breached. */
public record SlaBreachedEvent(IncidentId incidentId, Priority priority, Instant timestamp)
    implements DomainEvent {

  public SlaBreachedEvent {
    Objects.requireNonNull(incidentId, "Incident ID must not be null");
    Objects.requireNonNull(priority, "Priority must not be null");
    Objects.requireNonNull(timestamp, "Timestamp must not be null");
  }

  @Override
  public Instant occurredOn() {
    return timestamp;
  }
}
