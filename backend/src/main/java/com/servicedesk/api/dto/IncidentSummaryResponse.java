package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import java.time.Instant;
import java.util.UUID;

public record IncidentSummaryResponse(
    UUID id,
    String incidentNumber,
    String title,
    Category category,
    IncidentStatus status,
    Priority priority,
    String reporterName,
    String assigneeName,
    SlaStatus slaStatus,
    Instant createdAt,
    String serviceName,
    /** Lets the board and list tell the assignee apart without comparing names. */
    UUID assigneeId,
    Instant resolvedAt,
    /** True when the viewer follows this issue ("affects me too") but did not report it. */
    boolean joined) {

  /** The same row shown to someone who only follows the issue: the reporter stays hidden. */
  public IncidentSummaryResponse asJoined() {
    return new IncidentSummaryResponse(
        id,
        incidentNumber,
        title,
        category,
        status,
        priority,
        "Another user",
        assigneeName,
        slaStatus,
        createdAt,
        serviceName,
        assigneeId,
        resolvedAt,
        true);
  }
}
