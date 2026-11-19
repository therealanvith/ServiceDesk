package com.servicedesk.application;

import com.servicedesk.api.dto.ReportResponse;
import com.servicedesk.api.dto.ReportResponse.Overview;
import com.servicedesk.api.dto.ReportResponse.ServiceLoad;
import com.servicedesk.api.dto.ReportResponse.TechnicianLoad;
import com.servicedesk.api.dto.WorkloadResponse;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.User;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final SpringDataItServiceJpaRepository services;
  private final Clock clock;

  public ReportApplicationService(
      IncidentRepository incidents,
      UserRepository users,
      SpringDataItServiceJpaRepository services,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.services = services;
    this.clock = clock;
  }

  public ReportResponse overview(String requesterUsername) {
    User requester = loadUser(requesterUsername);
    if (!requester.getRole().hasAdminRights()) {
      throw new UnauthorizedOperationException("Only an administrator can view reports");
    }

    Instant now = clock.instant();
    List<Incident> all = incidents.findAll();
    List<Incident> open = all.stream().filter(i -> isOpen(i.getStatus())).toList();

    long resolved =
        all.stream()
            .filter(i -> i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
            .count();
    long breached = all.stream().filter(i -> slaStatus(i) == SlaStatus.BREACHED).count();
    long atRisk = open.stream().filter(i -> slaStatus(i) == SlaStatus.WARNING_BREACH_IMMINENT).count();
    long escalated = all.stream().filter(i -> i.getEscalatedAt() != null).count();
    long criticalOpen = open.stream().filter(i -> i.getPriority() == Priority.P1_CRITICAL).count();

    // Response SLA: of tickets that have been responded to (or are overdue for it), how many in time.
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
        ratings.isEmpty() ? null : round1(ratings.stream().mapToInt(Integer::intValue).average().orElse(0));

    Overview overview =
        new Overview(
            all.size(), open.size(), criticalOpen, resolved, breached, atRisk, escalated,
            responseMetPercent, averageRating, ratings.size());

    Map<UUID, String> serviceNames = new HashMap<>();
    services.findAll().forEach(s -> serviceNames.put(s.getId(), s.getName()));
    Function<Incident, String> serviceOf =
        i -> i.getAffectedServiceId() == null
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
                  theirs.stream().filter(i -> isOpen(i.getStatus()) && slaStatus(i) == SlaStatus.BREACHED).count(),
                  theirs.stream()
                      .filter(i -> i.getStatus() == IncidentStatus.RESOLVED || i.getStatus() == IncidentStatus.CLOSED)
                      .count(),
                  tech.isAvailable());
            })
        .sorted(Comparator.comparingLong(TechnicianLoad::active).reversed().thenComparing(TechnicianLoad::fullName))
        .toList();
  }

  private static List<ServiceLoad> serviceLoad(List<Incident> list, Function<Incident, String> serviceOf) {
    Map<String, List<Incident>> grouped = list.stream().collect(Collectors.groupingBy(serviceOf));
    return grouped.entrySet().stream()
        .map(e -> new ServiceLoad(e.getKey(), e.getValue().size(),
            e.getValue().stream().filter(i -> isOpen(i.getStatus())).count()))
        .sorted(Comparator.comparingLong(ServiceLoad::total).reversed().thenComparing(ServiceLoad::service))
        .toList();
  }

  private static Map<String, Long> countBy(List<Incident> list, Function<Incident, String> key) {
    return list.stream()
        .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
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

  private User loadUser(String username) {
    return users
        .findByUsername(username)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
  }
}
