package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.UpdateKind;
import com.servicedesk.domain.priority.Priority;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view of an issue for the "All issues" list. Never carries the reporter's identity,
 * internal notes, emails or the audit history.
 */
public record PublicIssueResponse(
    UUID id,
    String incidentNumber,
    String title,
    String description,
    IncidentStatus status,
    Priority priority,
    Category category,
    String serviceName,
    String handledBy,
    Instant createdAt,
    Instant updatedAt,
    long updateCount,
    long affectedCount,
    boolean affectedByMe,
    List<PublicUpdate> updates) {

  /** One public entry; {@code author} is "Reporter" for the person who raised the issue. */
  public record PublicUpdate(
      UUID id, UpdateKind kind, String author, String authorRole, String text, Instant createdAt) {}
}
