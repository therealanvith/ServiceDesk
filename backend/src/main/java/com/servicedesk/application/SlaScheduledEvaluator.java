package com.servicedesk.application;

import com.servicedesk.api.dto.SlaEvaluationSummaryResponse;
import com.servicedesk.domain.audit.AuditAction;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.sla.SlaStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled background worker evaluating SLA resolution deadlines and triggering automated priority escalations.
 */
@Component
@EnableScheduling
@Transactional
public class SlaScheduledEvaluator {

  private static final Logger log = LoggerFactory.getLogger(SlaScheduledEvaluator.class);

  private final IncidentRepository incidentRepository;
  private final AuditLogRepository auditLogRepository;
  private final List<NotificationChannel> notificationChannels;
  private final Clock clock;

  public SlaScheduledEvaluator(
      IncidentRepository incidentRepository,
      AuditLogRepository auditLogRepository,
      List<NotificationChannel> notificationChannels,
      Clock clock) {
    this.incidentRepository = incidentRepository;
    this.auditLogRepository = auditLogRepository;
    this.notificationChannels = notificationChannels;
    this.clock = clock;
  }

  @Scheduled(fixedRateString = "${app.sla.evaluator-rate-ms:60000}")
  public void scheduledEvaluation() {
    evaluateAllSla();
  }

  public SlaEvaluationSummaryResponse evaluateAllSla() {
    Instant now = clock.instant();
    List<Incident> activeIncidents =
        incidentRepository.findAll().stream()
            .filter(inc -> inc.getStatus().isActive())
            .toList();

    int evaluatedCount = 0;
    int breachedCount = 0;

    for (Incident incident : activeIncidents) {
      evaluatedCount++;
      SlaStatus before = incident.getSlaWindow() != null ? incident.getSlaWindow().getStatus() : SlaStatus.WITHIN_SLA;
      SlaStatus after = incident.evaluateSla(clock);

      if (before != SlaStatus.BREACHED && after == SlaStatus.BREACHED) {
        breachedCount++;
        incidentRepository.save(incident);

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
          notifyChannels(
              NotificationMessage.of(
                  incident.getAssigneeId(),
                  incident.getId(),
                  "SLA_BREACH",
                  "CRITICAL: SLA Breached for " + incident.getIncidentNumber(),
                  "Ticket has breached its SLA resolution window and priority has been boosted."));
        }
      }
    }

    return new SlaEvaluationSummaryResponse(evaluatedCount, breachedCount, now);
  }

  private void notifyChannels(NotificationMessage message) {
    if (notificationChannels != null) {
      for (NotificationChannel channel : notificationChannels) {
        try {
          channel.send(message);
        } catch (Exception ignored) {
        }
      }
    }
  }
}
