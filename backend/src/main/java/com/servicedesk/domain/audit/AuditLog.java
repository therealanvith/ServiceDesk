package com.servicedesk.domain.audit;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit ledger entry capturing incident lifecycle actions.
 */
public record AuditLog(
    UUID id,
    IncidentId incidentId,
    UserId actorId,
    String action,
    String oldValue,
    String newValue,
    Instant timestamp,
    String remarks) {

  public AuditLog {
    Objects.requireNonNull(id, "Audit log ID must not be null");
    Objects.requireNonNull(incidentId, "Incident ID must not be null");
    Objects.requireNonNull(action, "Action must not be null");
    Objects.requireNonNull(timestamp, "Timestamp must not be null");
  }

  public static AuditLog create(
      IncidentId incidentId,
      UserId actorId,
      String action,
      String oldValue,
      String newValue,
      Instant timestamp,
      String remarks) {
    return new AuditLog(
        UUID.randomUUID(),
        incidentId,
        actorId,
        action,
        oldValue,
        newValue,
        timestamp != null ? timestamp : Instant.now(),
        remarks);
  }
}
