package com.servicedesk.api.dto;

import java.util.List;
import java.util.Map;

/** Operational dashboard data for administrators. */
public record ReportResponse(
    Overview overview,
    Map<String, Long> byStatus,
    Map<String, Long> byPriority,
    Map<String, Long> byCategory,
    List<ServiceLoad> byService,
    List<TechnicianLoad> technicianWorkload,
    List<ServiceLoad> correlatedServices) {

  public record Overview(
      long total,
      long open,
      long criticalOpen,
      long resolved,
      long slaBreached,
      long slaAtRisk,
      long escalated,
      Double responseSlaMetPercent,
      Double averageRating,
      long ratedCount) {}

  public record ServiceLoad(String service, long total, long open) {}

  public record TechnicianLoad(
      String username, String fullName, long active, long breached, long resolved, boolean available) {}
}
