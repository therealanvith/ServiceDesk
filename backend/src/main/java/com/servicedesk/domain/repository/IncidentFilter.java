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
 * "any". {@code participantId} matches tickets the user is assigned to <em>or</em> reported.
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
    Instant createdTo,
    UserId participantId) {

  public IncidentFilter(
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
    this(
        status,
        priority,
        category,
        assigneeId,
        reporterId,
        slaStatus,
        searchText,
        serviceId,
        createdFrom,
        createdTo,
        null);
  }

  public IncidentFilter(
      IncidentStatus status,
      Priority priority,
      Category category,
      UserId assigneeId,
      UserId reporterId,
      SlaStatus slaStatus,
      String searchText) {
    this(
        status,
        priority,
        category,
        assigneeId,
        reporterId,
        slaStatus,
        searchText,
        null,
        null,
        null);
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

  /** Only tickets this user reported (the caller's own assignee/reporter criteria are dropped). */
  public IncidentFilter withReporter(UserId reporter) {
    return scoped(null, reporter, null);
  }

  /** The same criteria without any reporter, assignee or participant restriction. */
  public IncidentFilter unscoped() {
    return scoped(null, null, null);
  }

  /** Only tickets assigned to this user. */
  public IncidentFilter withAssignee(UserId assignee) {
    return scoped(assignee, null, null);
  }

  /** Only tickets this user is assigned to or reported. */
  public IncidentFilter withParticipant(UserId participant) {
    return scoped(null, null, participant);
  }

  private IncidentFilter scoped(UserId assignee, UserId reporter, UserId participant) {
    return new IncidentFilter(
        status,
        priority,
        category,
        assignee,
        reporter,
        slaStatus,
        searchText,
        serviceId,
        createdFrom,
        createdTo,
        participant);
  }
}
