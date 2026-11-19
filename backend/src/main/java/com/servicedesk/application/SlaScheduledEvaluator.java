package com.servicedesk.application;

import com.servicedesk.api.dto.SlaEvaluationSummaryResponse;
import com.servicedesk.domain.audit.AuditAction;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * Scheduled background worker evaluating SLA resolution deadlines and triggering automated priority escalations.
 *
 * <p>Each incident is evaluated in its own short transaction against a freshly loaded copy, so a
 * user changing a ticket mid-run is never overwritten and one failing ticket does not roll back
 * the rest of the run.
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
        incident.getSlaWindow() != null ? incident.getSlaWindow().getStatus() : SlaStatus.WITHIN_SLA;
    SlaStatus after = incident.evaluateSla(clock);

    // Automatic escalation: nobody has picked the ticket up by its response deadline.
    boolean escalate = incident.isResponseOverdue(now) && incident.getEscalatedAt() == null;
    if (escalate) {
      incident.setEscalatedAt(now);
    }

    if (before == after && !escalate) {
      return Outcome.EVALUATED;
    }
    incidentRepository.save(incident);

    if (escalate) {
      escalate(incident, now);
    }
    if (before != after && after == SlaStatus.WARNING_BREACH_IMMINENT && incident.getAssigneeId() != null) {
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

  private void escalate(Incident incident, Instant now) {
    String who = incident.getAssigneeId() == null ? "No technician is assigned" : "The assigned technician has not started";
    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            null,
            AuditAction.ESCALATED.name(),
            incident.getStatus().name(),
            "ADMINISTRATORS",
            now,
            who + " within the response target; escalated to administrators"));
    log.warn("[ESCALATION] Incident {} missed its response target", incident.getIncidentNumber());

    List<UserId> recipients =
        userRepository.findAll().stream()
            .filter(u -> u.getRole().hasAdminRights() && u.isAvailable())
            .map(User::getId)
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    if (incident.getAssigneeId() != null && !recipients.contains(incident.getAssigneeId())) {
      recipients.add(incident.getAssigneeId());
    }
    for (UserId recipient : recipients) {
      NotificationDispatch.afterCommit(
          notificationChannels,
          NotificationMessage.of(
              recipient,
              incident.getId(),
              "ESCALATION",
              "Escalated: " + incident.getIncidentNumber() + " has had no response",
              incident.getTitle() + " (" + incident.getPriority() + ") missed its response target."));
    }
  }

  private <T> T inTransaction(Supplier<T> work) {
    return transactionTemplate != null ? transactionTemplate.execute(status -> work.get()) : work.get();
  }
}
