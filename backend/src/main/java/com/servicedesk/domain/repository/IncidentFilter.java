package com.servicedesk.domain.repository;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter criteria for multi-parameter incident searches. Every criterion is optional; null means
 * "any".
 */
public record IncidentFilter(
    IncidentStatus status,
    Priority priority,
    Category category,
    UserId assigneeId,
    UserId reporterId,
    SlaStatus slaStatus,
    String searchText,
    UUID serviceId,
    Instant createdFrom,
    Instant createdTo) {

  public IncidentFilter(
      IncidentStatus status,
      Priority priority,
      Category category,
      UserId assigneeId,
      UserId reporterId,
      SlaStatus slaStatus,
      String searchText) {
    this(status, priority, category, assigneeId, reporterId, slaStatus, searchText, null, null, null);
  }

  public static IncidentFilter empty() {
    return new IncidentFilter(null, null, null, null, null, null, null);
  }

  public static IncidentFilter forReporter(UserId reporterId) {
    return new IncidentFilter(null, null, null, null, reporterId, null, null);
  }

  public static IncidentFilter forAssignee(UserId assigneeId) {
    return new IncidentFilter(null, null, null, assigneeId, null, null, null);
  }

  public IncidentFilter withReporter(UserId reporter) {
    return new IncidentFilter(status, priority, category, null, reporter, slaStatus, searchText,
        serviceId, createdFrom, createdTo);
  }

  public IncidentFilter withAssignee(UserId assignee) {
    return new IncidentFilter(status, priority, category, assignee, null, slaStatus, searchText,
        serviceId, createdFrom, createdTo);
  }
}
