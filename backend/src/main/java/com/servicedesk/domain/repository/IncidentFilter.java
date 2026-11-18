package com.servicedesk.domain.repository;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.UserId;

/**
 * Filter criteria for multi-parameter incident searches.
 */
public record IncidentFilter(
    IncidentStatus status,
    Priority priority,
    Category category,
    UserId assigneeId,
    UserId reporterId,
    SlaStatus slaStatus,
    String searchText) {

  public static IncidentFilter empty() {
    return new IncidentFilter(null, null, null, null, null, null, null);
  }

  public static IncidentFilter forReporter(UserId reporterId) {
    return new IncidentFilter(null, null, null, null, reporterId, null, null);
  }

  public static IncidentFilter forAssignee(UserId assigneeId) {
    return new IncidentFilter(null, null, null, assigneeId, null, null, null);
  }
}
