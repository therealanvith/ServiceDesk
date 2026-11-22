package com.servicedesk.application;

import com.servicedesk.api.dto.SlaEvaluationSummaryResponse;
import com.servicedesk.domain.assignment.AssignmentStrategy;
import com.servicedesk.domain.assignment.CapacityAwareAssignmentStrategy;
import com.servicedesk.domain.assignment.LeastLoadedAssignmentStrategy;
import com.servicedesk.domain.audit.AuditAction;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.escalation.EscalationChain;
import com.servicedesk.domain.escalation.EscalationDecision;
import com.servicedesk.domain.escalation.EscalationHandler;
import com.servicedesk.domain.escalation.EscalationLevel;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scheduled background worker evaluating SLA resolution deadlines and triggering automated priority
 * escalations.
 *
 * <p>Each incident is evaluated in its own short transaction against a freshly loaded copy, so a
 * user changing a ticket mid-run is never overwritten and one failing ticket does not roll back the
 * rest of the run.
 */
@Component
@EnableScheduling
public class SlaScheduledEvaluator {

  private static final Logger log = LoggerFactory.getLogger(SlaScheduledEvaluator.class);

  private final IncidentRepository incidentRepository;
  private final AuditLogRepository auditLogRepository;
  private final List<NotificationChannel> notificationChannels;
  private final Clock clock;
  private final UserRepository userRepository;
  private TransactionTemplate transactionTemplate;
  private final EscalationHandler escalationChain = EscalationChain.standard();
  // Reassigning a neglected ticket goes to whoever has the most room, never past their limit.
  private final AssignmentStrategy reassignment =
      new CapacityAwareAssignmentStrategy(new LeastLoadedAssignmentStrategy());

  public SlaScheduledEvaluator(
      IncidentRepository incidentRepository,
      AuditLogRepository auditLogRepository,
      List<NotificationChannel> notificationChannels,
      Clock clock,
      UserRepository userRepository) {
    this.userRepository = userRepository;
    this.incidentRepository = incidentRepository;
    this.auditLogRepository = auditLogRepository;
    this.notificationChannels = notificationChannels;
    this.clock = clock;
  }

  @Autowired(required = false)
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Scheduled(fixedRateString = "${app.sla.evaluator-rate-ms:60000}")
  public void scheduledEvaluation() {
    evaluateAllSla();
  }

  public SlaEvaluationSummaryResponse evaluateAllSla() {
    Instant now = clock.instant();
    List<IncidentId> candidates =
        incidentRepository.findAll().stream()
            .filter(inc -> isEvaluated(inc.getStatus()))
            .map(Incident::getId)
            .toList();

    int evaluatedCount = 0;
    int breachedCount = 0;

    for (IncidentId id : candidates) {
      try {
        Outcome outcome = inTransaction(() -> evaluateOne(id, now));
        if (outcome != Outcome.SKIPPED) {
          evaluatedCount++;
        }
        if (outcome == Outcome.NEWLY_BREACHED) {
          breachedCount++;
        }
      } catch (RuntimeException ex) {
        // Typically an optimistic-lock conflict with a concurrent user edit; retried next run.
        log.warn("SLA evaluation skipped for incident {}: {}", id, ex.getMessage());
      }
    }

    return new SlaEvaluationSummaryResponse(evaluatedCount, breachedCount, now);
  }

  private enum Outcome {
    SKIPPED,
    EVALUATED,
    NEWLY_BREACHED
  }

  private static boolean isEvaluated(IncidentStatus status) {
    return status.isActive() && status != IncidentStatus.RESOLVED;
  }

  private Outcome evaluateOne(IncidentId id, Instant now) {
    Incident incident = incidentRepository.findById(id).orElse(null);
    if (incident == null || !isEvaluated(incident.getStatus())) {
      return Outcome.SKIPPED;
    }

    SlaStatus before =
        incident.getSlaWindow() != null
            ? incident.getSlaWindow().getStatus()
            : SlaStatus.WITHIN_SLA;
    SlaStatus after = incident.evaluateSla(clock);

    // Escalation chain: team lead, then administrators, then the IT manager, one step per run.
    EscalationLevel previousLevel = incident.getEscalationLevel();
    Optional<EscalationDecision> decision = escalationChain.handle(incident, now);
    decision.ifPresent(d -> incident.escalateTo(d.level(), now));
    UserId previousAssignee = incident.getAssigneeId();
    Optional<User> reassignedTo =
        decision
            .filter(d -> d.level() == EscalationLevel.ADMINISTRATOR)
            .flatMap(d -> reassign(incident));

    if (before == after && decision.isEmpty()) {
      return Outcome.EVALUATED;
    }
    incidentRepository.save(incident);

    decision.ifPresent(
        d -> escalate(incident, d, previousLevel, previousAssignee, reassignedTo, now));
    if (before != after
        && after == SlaStatus.WARNING_BREACH_IMMINENT
        && incident.getAssigneeId() != null) {
      NotificationDispatch.afterCommit(
          notificationChannels,
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "SLA_WARNING",
              "SLA at risk: " + incident.getIncidentNumber(),
              "Resolution is due " + incident.getSlaWindow().getResolutionDeadline() + "."));
    }

    if (before == SlaStatus.BREACHED || after != SlaStatus.BREACHED) {
      return Outcome.EVALUATED;
    }

    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            null,
            AuditAction.SLA_BREACHED.name(),
            before.name(),
            after.name(),
            now,
            "SLA breached; priority automatically escalated to " + incident.getPriority()));

    log.warn(
        "[SLA BREACH] Incident {} breached resolution deadline! Escalated to {}",
        incident.getIncidentNumber(),
        incident.getPriority());

    if (incident.getAssigneeId() != null) {
      NotificationDispatch.afterCommit(
          notificationChannels,
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "SLA_BREACH",
              "CRITICAL: SLA Breached for " + incident.getIncidentNumber(),
              "Ticket has breached its SLA resolution window and priority has been boosted."));
    }
    return Outcome.NEWLY_BREACHED;
  }

  /** Hands an unacknowledged ticket to another technician who still has room. */
  private Optional<User> reassign(Incident incident) {
    IncidentStatus status = incident.getStatus();
    if (status != IncidentStatus.REPORTED && status != IncidentStatus.ASSIGNED) {
      return Optional.empty();
    }
    List<User> others =
        userRepository.findTechnicians().stream()
            .filter(User::isAvailable)
            .filter(t -> !t.getId().equals(incident.getAssigneeId()))
            .toList();
    Optional<User> chosen = reassignment.selectTechnician(incident, others);
    chosen.ifPresent(tech -> incident.assign(tech.getId(), null));
    return chosen;
  }

  private void escalate(
      Incident incident,
      EscalationDecision decision,
      EscalationLevel previousLevel,
      UserId previousAssignee,
      Optional<User> reassignedTo,
      Instant now) {
    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            null,
            AuditAction.ESCALATED.name(),
            previousLevel.name(),
            decision.level().name(),
            now,
            decision.reason() + "; escalated to " + describe(decision.level())));
    log.warn(
        "[ESCALATION] Incident {} escalated to {}: {}",
        incident.getIncidentNumber(),
        decision.level(),
        decision.reason());

    reassignedTo.ifPresent(
        tech -> {
          auditLogRepository.save(
              AuditLog.create(
                  incident.getId(),
                  null,
                  AuditAction.ASSIGNED.name(),
                  null,
                  tech.getUsername(),
                  now,
                  "Reassigned automatically after escalation to administrators"));
          NotificationDispatch.afterCommit(
              notificationChannels,
              NotificationMessage.of(
                  tech.getId(),
                  incident.getId(),
                  "ASSIGNMENT",
                  "Escalated ticket assigned to you: " + incident.getIncidentNumber(),
                  incident.getTitle() + " was not picked up in time and is now yours."));
        });

    List<UserId> recipients = new ArrayList<>(recipientsFor(decision.level()));
    if (previousAssignee != null && !recipients.contains(previousAssignee)) {
      recipients.add(previousAssignee);
    }
    String detail =
        incident.getTitle()
            + " ("
            + incident.getPriority()
            + "): "
            + decision.reason()
            + reassignedTo.map(t -> ". Reassigned to " + t.getFullName() + ".").orElse(".");
    for (UserId recipient : recipients) {
      NotificationDispatch.afterCommit(
          notificationChannels,
          NotificationMessage.of(
              recipient,
              incident.getId(),
              "ESCALATION",
              "Escalated to " + describe(decision.level()) + ": " + incident.getIncidentNumber(),
              detail));
    }
  }

  /** Who hears about an escalation at each level; falls back upwards if nobody holds a role. */
  private List<UserId> recipientsFor(EscalationLevel level) {
    List<User> everyone = userRepository.findAll().stream().filter(User::isAvailable).toList();
    Predicate<User> admins = u -> u.getRole() == Role.ADMINISTRATOR;
    Predicate<User> root = u -> u.getRole() == Role.ROOT;
    Predicate<User> teamLeads = u -> u.getRole() == Role.TECHNICIAN && u.isTeamLead();
    List<Predicate<User>> order =
        switch (level) {
          case TEAM_LEAD -> List.of(teamLeads, admins, root);
          case ADMINISTRATOR -> List.of(admins, root);
          case IT_MANAGER, NONE -> List.of(root, admins);
        };
    for (Predicate<User> group : order) {
      List<UserId> ids = everyone.stream().filter(group).map(User::getId).toList();
      if (!ids.isEmpty()) {
        return ids;
      }
    }
    return List.of();
  }

  private static String describe(EscalationLevel level) {
    return switch (level) {
      case TEAM_LEAD -> "the team lead";
      case ADMINISTRATOR -> "administrators";
      case IT_MANAGER -> "the IT manager";
      case NONE -> "nobody";
    };
  }

  private <T> T inTransaction(Supplier<T> work) {
    return transactionTemplate != null
        ? transactionTemplate.execute(status -> work.get())
        : work.get();
  }
}
