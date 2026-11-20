package com.servicedesk.application;

import com.servicedesk.api.dto.ReportResponse;
import com.servicedesk.api.dto.ReportResponse.Overview;
import com.servicedesk.api.dto.ReportResponse.ServiceLoad;
import com.servicedesk.api.dto.ReportResponse.TechnicianLoad;
import com.servicedesk.api.dto.TrendsResponse;
import com.servicedesk.api.dto.TrendsResponse.CategoryResolution;
import com.servicedesk.api.dto.TrendsResponse.DailyPoint;
import com.servicedesk.api.dto.TrendsResponse.EscalationCount;
import com.servicedesk.api.dto.TrendsResponse.SelfHelp;
import com.servicedesk.api.dto.TrendsResponse.WeeklyCompliance;
import com.servicedesk.api.dto.WorkloadResponse;
import com.servicedesk.domain.audit.AuditAction;
import com.servicedesk.domain.escalation.EscalationLevel;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.User;
import com.servicedesk.infrastructure.persistence.entity.IncidentAuditLogEntity;
import com.servicedesk.infrastructure.persistence.entity.SelfHelpFeedbackEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataAuditLogJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSelfHelpFeedbackJpaRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational reporting: overview counts, distributions, technician workload, SLA compliance,
 * satisfaction, and service correlation (several open tickets on one service often means one
 * underlying outage).
 */
@Service
@Transactional(readOnly = true)
public class ReportApplicationService {

  /** Open tickets on one service within this window are reported as a possible common cause. */
  private static final Duration CORRELATION_WINDOW = Duration.ofHours(24);

  private static final int CORRELATION_THRESHOLD = 2;

  /** The trend ranges the reports page offers. */
  private static final Set<Integer> TREND_RANGES = Set.of(7, 30, 90);

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final SpringDataItServiceJpaRepository services;
  private final SpringDataAuditLogJpaRepository auditLogs;
  private final SpringDataSelfHelpFeedbackJpaRepository selfHelpFeedback;
  private final Clock clock;

  public ReportApplicationService(
      IncidentRepository incidents,
      UserRepository users,
      SpringDataItServiceJpaRepository services,
      SpringDataAuditLogJpaRepository auditLogs,
      SpringDataSelfHelpFeedbackJpaRepository selfHelpFeedback,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.services = services;
    this.auditLogs = auditLogs;
    this.selfHelpFeedback = selfHelpFeedback;
    this.clock = clock;
  }

  public ReportResponse overview(String requesterUsername) {
    requireAdmin(requesterUsername);

    Instant now = clock.instant();
    List<Incident> all = incidents.findAll();
    List<Incident> open = all.stream().filter(i -> isOpen(i.getStatus())).toList();

    long resolved =
        all.stream()
            .filter(
                i ->
                    i.getStatus() == IncidentStatus.RESOLVED
                        || i.getStatus() == IncidentStatus.CLOSED)
            .count();
    long breached = all.stream().filter(i -> slaStatus(i) == SlaStatus.BREACHED).count();
    long atRisk =
        open.stream().filter(i -> slaStatus(i) == SlaStatus.WARNING_BREACH_IMMINENT).count();
    long escalated = all.stream().filter(i -> i.getEscalatedAt() != null).count();
    long criticalOpen = open.stream().filter(i -> i.getPriority() == Priority.P1_CRITICAL).count();

    // Response SLA: of tickets that have been responded to (or are overdue for it), how many in
    // time.
    List<Incident> responseMeasured =
        all.stream()
            .filter(i -> i.getSlaWindow() != null)
            .filter(i -> i.getRespondedAt() != null || i.isResponseOverdue(now))
            .toList();
    long responseMet =
        responseMeasured.stream()
            .filter(i -> i.getRespondedAt() != null)
            .filter(i -> !i.getRespondedAt().isAfter(i.getSlaWindow().getResponseDeadline()))
            .count();
    Double responseMetPercent =
        responseMeasured.isEmpty() ? null : round1(100.0 * responseMet / responseMeasured.size());

    List<Integer> ratings =
        all.stream().map(Incident::getFeedbackRating).filter(Objects::nonNull).toList();
    Double averageRating =
        ratings.isEmpty()
            ? null
            : round1(ratings.stream().mapToInt(Integer::intValue).average().orElse(0));

    Overview overview =
        new Overview(
            all.size(),
            open.size(),
            criticalOpen,
            resolved,
            breached,
            atRisk,
            escalated,
            responseMetPercent,
            averageRating,
            ratings.size());

    Map<UUID, String> serviceNames = new HashMap<>();
    services.findAll().forEach(s -> serviceNames.put(s.getId(), s.getName()));
    Function<Incident, String> serviceOf =
        i ->
            i.getAffectedServiceId() == null
                ? "Not specified"
                : serviceNames.getOrDefault(i.getAffectedServiceId(), "Retired service");

    List<ServiceLoad> byService = serviceLoad(all, serviceOf);
    List<ServiceLoad> correlated =
        serviceLoad(
                open.stream()
                    .filter(i -> i.getAffectedServiceId() != null)
                    .filter(i -> i.getCreatedAt().isAfter(now.minus(CORRELATION_WINDOW)))
                    .toList(),
                serviceOf)
            .stream()
            .filter(load -> load.open() >= CORRELATION_THRESHOLD)
            .toList();

    return new ReportResponse(
        overview,
        countBy(all, i -> i.getStatus().name()),
        countBy(all, i -> i.getPriority().name()),
        countBy(all, i -> i.getCategory().name()),
        byService,
        technicianWorkload(all),
        correlated);
  }

  /**
   * Daily volume, weekly resolution-SLA compliance, resolve time by category, first response,
   * escalations and self-help answers over the last {@code days} UTC days, today included.
   */
  public TrendsResponse trends(int days, String requesterUsername) {
    requireAdmin(requesterUsername);
    if (!TREND_RANGES.contains(days)) {
      throw new IllegalArgumentException("Trends cover 7, 30 or 90 days");
    }

    LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    LocalDate from = to.minusDays(days - 1L);
    Instant since = from.atStartOfDay(ZoneOffset.UTC).toInstant();

    List<Incident> all = incidents.findAll();
    List<Incident> created = all.stream().filter(i -> onOrAfter(i.getCreatedAt(), since)).toList();
    List<Incident> resolved =
        all.stream().filter(i -> onOrAfter(i.getResolvedAt(), since)).toList();

    Map<LocalDate, Long> createdPerDay = countByDay(created, Incident::getCreatedAt);
    Map<LocalDate, Long> resolvedPerDay = countByDay(resolved, Incident::getResolvedAt);
    List<DailyPoint> daily =
        from.datesUntil(to.plusDays(1))
            .map(
                day ->
                    new DailyPoint(
                        day,
                        createdPerDay.getOrDefault(day, 0L),
                        resolvedPerDay.getOrDefault(day, 0L)))
            .toList();

    // Measured against the resolution deadline, which a pause for missing info pushes back.
    Map<LocalDate, List<Incident>> resolvedPerWeek =
        resolved.stream().collect(Collectors.groupingBy(i -> weekStart(i.getResolvedAt())));
    List<WeeklyCompliance> weekly = new ArrayList<>();
    for (LocalDate week = weekStart(since); !week.isAfter(to); week = week.plusWeeks(1)) {
      List<Incident> inWeek = resolvedPerWeek.getOrDefault(week, List.of());
      long within =
          inWeek.stream()
              .filter(i -> i.getSlaWindow() != null)
              .filter(i -> !i.getResolvedAt().isAfter(i.getSlaWindow().getResolutionDeadline()))
              .count();
      weekly.add(
          new WeeklyCompliance(
              week,
              inWeek.size(),
              within,
              inWeek.isEmpty() ? null : round1(100.0 * within / inWeek.size())));
    }

    // Wall-clock hours from report to resolve, so time spent waiting on the reporter counts too.
    List<CategoryResolution> byCategory =
        resolved.stream()
            .collect(Collectors.groupingBy(i -> i.getCategory().name()))
            .entrySet()
            .stream()
            .map(
                e ->
                    new CategoryResolution(
                        e.getKey(),
                        e.getValue().size(),
                        round1(averageMinutes(e.getValue(), Incident::getResolvedAt) / 60.0)))
            .sorted(
                Comparator.comparingDouble(CategoryResolution::meanHours)
                    .reversed()
                    .thenComparing(CategoryResolution::category))
            .toList();

    List<Incident> responded = created.stream().filter(i -> i.getRespondedAt() != null).toList();
    Double firstResponseMinutes =
        responded.isEmpty() ? null : round1(averageMinutes(responded, Incident::getRespondedAt));

    Map<String, Long> escalated =
        auditLogs
            .findByActionAndCreatedAtGreaterThanEqual(AuditAction.ESCALATED.name(), since)
            .stream()
            .filter(entry -> entry.getNewValue() != null)
            .collect(
                Collectors.groupingBy(IncidentAuditLogEntity::getNewValue, Collectors.counting()));
    List<EscalationCount> escalations =
        Arrays.stream(EscalationLevel.values())
            .filter(level -> level != EscalationLevel.NONE)
            .map(
                level ->
                    new EscalationCount(level.name(), escalated.getOrDefault(level.name(), 0L)))
            .toList();

    List<SelfHelpFeedbackEntity> answers = selfHelpFeedback.findByCreatedAtGreaterThanEqual(since);
    long helped = answers.stream().filter(SelfHelpFeedbackEntity::isHelped).count();

    return new TrendsResponse(
        days,
        from,
        to,
        daily,
        weekly,
        byCategory,
        firstResponseMinutes,
        responded.size(),
        escalations,
        new SelfHelp(helped, answers.size() - helped));
  }

  /** A technician's own queue summary. */
  public WorkloadResponse myWorkload(String requesterUsername) {
    User me = loadUser(requesterUsername);
    Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
    List<Incident> mine =
        incidents.findAll().stream().filter(i -> me.getId().equals(i.getAssigneeId())).toList();
    List<Incident> active = mine.stream().filter(i -> isOpen(i.getStatus())).toList();
    return new WorkloadResponse(
        active.size(),
        active.stream().filter(i -> i.getStatus() == IncidentStatus.IN_PROGRESS).count(),
        active.stream().filter(i -> i.getStatus() == IncidentStatus.PENDING_INFO).count(),
        active.stream().filter(i -> slaStatus(i) == SlaStatus.WARNING_BREACH_IMMINENT).count(),
        active.stream().filter(i -> slaStatus(i) == SlaStatus.BREACHED).count(),
        mine.stream()
            .filter(i -> i.getResolvedAt() != null && i.getResolvedAt().isAfter(weekAgo))
            .count());
  }

  private List<TechnicianLoad> technicianWorkload(List<Incident> all) {
    Map<Object, List<Incident>> byAssignee =
        all.stream()
            .filter(i -> i.getAssigneeId() != null)
            .collect(Collectors.groupingBy(Incident::getAssigneeId));
    return users.findTechnicians().stream()
        .map(
            tech -> {
              List<Incident> theirs = byAssignee.getOrDefault(tech.getId(), List.of());
              return new TechnicianLoad(
                  tech.getUsername(),
                  tech.getFullName(),
                  theirs.stream().filter(i -> isOpen(i.getStatus())).count(),
                  theirs.stream()
                      .filter(i -> isOpen(i.getStatus()) && slaStatus(i) == SlaStatus.BREACHED)
                      .count(),
                  theirs.stream()
                      .filter(
                          i ->
                              i.getStatus() == IncidentStatus.RESOLVED
                                  || i.getStatus() == IncidentStatus.CLOSED)
                      .count(),
                  tech.isAvailable());
            })
        .sorted(
            Comparator.comparingLong(TechnicianLoad::active)
                .reversed()
                .thenComparing(TechnicianLoad::fullName))
        .toList();
  }

  private static List<ServiceLoad> serviceLoad(
      List<Incident> list, Function<Incident, String> serviceOf) {
    Map<String, List<Incident>> grouped = list.stream().collect(Collectors.groupingBy(serviceOf));
    return grouped.entrySet().stream()
        .map(
            e ->
                new ServiceLoad(
                    e.getKey(),
                    e.getValue().size(),
                    e.getValue().stream().filter(i -> isOpen(i.getStatus())).count()))
        .sorted(
            Comparator.comparingLong(ServiceLoad::total)
                .reversed()
                .thenComparing(ServiceLoad::service))
        .toList();
  }

  private static Map<String, Long> countBy(List<Incident> list, Function<Incident, String> key) {
    return list.stream()
        .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
  }

  private static Map<LocalDate, Long> countByDay(
      List<Incident> list, Function<Incident, Instant> when) {
    return list.stream()
        .collect(
            Collectors.groupingBy(
                i -> LocalDate.ofInstant(when.apply(i), ZoneOffset.UTC), Collectors.counting()));
  }

  /** Mean minutes from each ticket's report to the given moment. */
  private static double averageMinutes(List<Incident> list, Function<Incident, Instant> until) {
    return list.stream()
            .mapToLong(i -> Duration.between(i.getCreatedAt(), until.apply(i)).toSeconds())
            .average()
            .orElse(0)
        / 60.0;
  }

  private static boolean onOrAfter(Instant when, Instant since) {
    return when != null && !when.isBefore(since);
  }

  private static LocalDate weekStart(Instant when) {
    return LocalDate.ofInstant(when, ZoneOffset.UTC)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static boolean isOpen(IncidentStatus status) {
    return status.isActive() && status != IncidentStatus.RESOLVED;
  }

  private static SlaStatus slaStatus(Incident i) {
    return i.getSlaWindow() != null ? i.getSlaWindow().getStatus() : null;
  }

  private static double round1(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private void requireAdmin(String username) {
    if (!loadUser(username).getRole().hasAdminRights()) {
      throw new UnauthorizedOperationException("Only an administrator can view reports");
    }
  }

  private User loadUser(String username) {
    return users
        .findByUsername(username)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
  }
}
