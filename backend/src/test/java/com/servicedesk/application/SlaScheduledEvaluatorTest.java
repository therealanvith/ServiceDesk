package com.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.servicedesk.domain.incident.*;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SlaScheduledEvaluatorTest {
  @Test
  void resolvedTicketsDoNotBreachWhileWaitingForClosure() {
    Instant start = Instant.parse("2026-11-19T00:00:00Z");
    Incident incident =
        new IncidentFactory(new MatrixPriorityStrategy(), Clock.fixed(start, ZoneOffset.UTC))
            .createIncident(
                "Test",
                "Description",
                Category.NETWORK,
                Impact.LOW,
                Urgency.LOW,
                UserId.generate());
    UserId tech = UserId.generate();
    incident.assign(tech, tech);
    incident.startWork(tech);
    incident.resolve("Fixed", tech);
    Clock afterDeadline = Clock.fixed(start.plusSeconds(172800), ZoneOffset.UTC);
    assertThat(incident.evaluateSla(afterDeadline)).isEqualTo(SlaStatus.WITHIN_SLA);
    IncidentRepository repository = mock(IncidentRepository.class);
    when(repository.findAll()).thenReturn(List.of(incident));
    var result =
        new SlaScheduledEvaluator(
                repository,
                mock(AuditLogRepository.class),
                List.of(),
                afterDeadline,
                mock(UserRepository.class))
            .evaluateAllSla();
    assertThat(result.evaluatedCount()).isZero();
    verify(repository, never()).save(any());
  }

  @Test
  void persistsWarningsAndEscalatesOnlyOnFirstBreach() {
    Instant start = Instant.parse("2026-11-19T00:00:00Z");
    Incident incident =
        new IncidentFactory(new MatrixPriorityStrategy(), Clock.fixed(start, ZoneOffset.UTC))
            .createIncident(
                "Test",
                "Description",
                Category.NETWORK,
                Impact.LOW,
                Urgency.LOW,
                UserId.generate());
    incident.markResponded(start); // keep this scenario about the resolution target only
    IncidentRepository repository = mock(IncidentRepository.class);
    AuditLogRepository audits = mock(AuditLogRepository.class);
    when(repository.findAll()).thenReturn(List.of(incident));
    when(repository.findById(incident.getId())).thenReturn(Optional.of(incident));
    Clock warning =
        Clock.fixed(
            incident.getSlaWindow().getResolutionDeadline().minusSeconds(600), ZoneOffset.UTC);
    new SlaScheduledEvaluator(repository, audits, List.of(), warning, mock(UserRepository.class))
        .evaluateAllSla();
    assertThat(incident.getSlaWindow().getStatus()).isEqualTo(SlaStatus.WARNING_BREACH_IMMINENT);
    verify(repository).save(incident);
    Clock breach =
        Clock.fixed(incident.getSlaWindow().getResolutionDeadline().plusSeconds(1), ZoneOffset.UTC);
    SlaScheduledEvaluator evaluator =
        new SlaScheduledEvaluator(
            repository, audits, List.of(), breach, mock(UserRepository.class));
    evaluator.evaluateAllSla();
    var priority = incident.getPriority();
    evaluator.evaluateAllSla();
    assertThat(incident.getPriority()).isEqualTo(priority);
    verify(audits, times(1)).save(any());
    verify(repository, times(2)).save(incident);
  }

  @Test
  void escalatesOnceWhenNobodyRespondsInTime() {
    Instant start = Instant.parse("2026-11-19T00:00:00Z");
    Incident incident =
        new IncidentFactory(new MatrixPriorityStrategy(), Clock.fixed(start, ZoneOffset.UTC))
            .createIncident(
                "Test",
                "Description",
                Category.NETWORK,
                Impact.HIGH,
                Urgency.HIGH,
                UserId.generate());
    IncidentRepository repository = mock(IncidentRepository.class);
    AuditLogRepository audits = mock(AuditLogRepository.class);
    when(repository.findAll()).thenReturn(List.of(incident));
    when(repository.findById(incident.getId())).thenReturn(Optional.of(incident));
    Clock late =
        Clock.fixed(incident.getSlaWindow().getResponseDeadline().plusSeconds(60), ZoneOffset.UTC);
    SlaScheduledEvaluator evaluator =
        new SlaScheduledEvaluator(repository, audits, List.of(), late, mock(UserRepository.class));

    evaluator.evaluateAllSla();
    evaluator.evaluateAllSla();

    assertThat(incident.getEscalatedAt()).isEqualTo(late.instant());
    verify(audits, times(1)).save(any());
  }
}
