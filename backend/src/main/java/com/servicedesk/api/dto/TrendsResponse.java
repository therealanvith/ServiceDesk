package com.servicedesk.api.dto;

import java.time.LocalDate;
import java.util.List;

/** How the desk has been doing over the last 7, 30 or 90 days. Days and weeks are UTC. */
public record TrendsResponse(
    int days,
    LocalDate from,
    LocalDate to,
    List<DailyPoint> daily,
    List<WeeklyCompliance> weeklyCompliance,
    List<CategoryResolution> resolutionByCategory,
    Double averageFirstResponseMinutes,
    long respondedCount,
    List<EscalationCount> escalationsByLevel,
    SelfHelp selfHelp) {

  /** Every day in the range is present, with zeros on quiet days. */
  public record DailyPoint(LocalDate date, long created, long resolved) {}

  /** Tickets resolved in a week (starting Monday) and how many beat their resolution target. */
  public record WeeklyCompliance(
      LocalDate weekStart, long resolvedCount, long withinSlaCount, Double compliancePercent) {}

  public record CategoryResolution(String category, long resolvedCount, double meanHours) {}

  public record EscalationCount(String level, long count) {}

  /** Answers to "did these steps fix it?" on the new-ticket form. */
  public record SelfHelp(long helped, long notHelped) {}
}
