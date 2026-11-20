package com.servicedesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.assignment.CapacityAwareAssignmentStrategy;
import com.servicedesk.domain.assignment.LeastLoadedAssignmentStrategy;
import com.servicedesk.domain.escalation.EscalationChain;
import com.servicedesk.domain.escalation.EscalationDecision;
import com.servicedesk.domain.escalation.EscalationHandler;
import com.servicedesk.domain.escalation.EscalationLevel;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EscalationAndCapacityTest {

  private static final Instant START = Instant.parse("2026-11-19T09:00:00Z");
  private final UserId tech = UserId.generate();
  private final EscalationHandler chain = EscalationChain.standard();

  /** A P1 ticket: 15-minute response target, 2-hour resolution target. */
  private Incident critical() {
    return new IncidentFactory(new MatrixPriorityStrategy(), Clock.fixed(START, ZoneOffset.UTC))
        .createIncident(
            "Core switch down",
            "Whole floor offline",
            Category.NETWORK,
            Impact.HIGH,
            Urgency.HIGH,
            UserId.generate());
  }

  private static User technician(String name, int active, int limit) {
    return new User(
        UserId.generate(),
        name,
        name,
        null,
        Role.TECHNICIAN,
        Set.of(Category.NETWORK),
        active,
        true,
        limit,
        false);
  }

  private Optional<EscalationLevel> escalate(Incident incident, Instant now) {
    Optional<EscalationDecision> decision = chain.handle(incident, now);
    decision.ifPresent(d -> incident.escalateTo(d.level(), now));
    return decision.map(EscalationDecision::level);
  }

  private static Instant responseDeadline(Incident incident) {
    return incident.getSlaWindow().getResponseDeadline();
  }

  @Test
  void acknowledgeSitsBetweenAssignedAndInProgress() {
    Incident incident = critical();
    assertThatThrownBy(() -> incident.acknowledge(tech))
        .isInstanceOf(InvalidStateTransitionException.class);

    incident.assign(tech, tech);
    incident.acknowledge(tech);
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACKNOWLEDGED);
    assertThatThrownBy(() -> incident.acknowledge(tech))
        .isInstanceOf(InvalidStateTransitionException.class);

    incident.startWork(tech);
    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.IN_PROGRESS);
  }

  @Test
  void reassigningAnAcknowledgedTicketNeedsTheNewTechnicianToAcceptIt() {
    Incident incident = critical();
    incident.assign(tech, tech);
    incident.acknowledge(tech);
    UserId other = UserId.generate();

    incident.assign(other, tech);

    assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(incident.getAssigneeId()).isEqualTo(other);
  }

  @Test
  void nothingEscalatesBeforeTheResponseDeadline() {
    assertThat(escalate(critical(), START.plusSeconds(60))).isEmpty();
  }

  @Test
  void chainClimbsOneLevelAtATime() {
    Incident incident = critical();
    Instant deadline = responseDeadline(incident);
    Duration target = Duration.between(START, deadline);

    assertThat(escalate(incident, deadline.plusSeconds(1))).contains(EscalationLevel.TEAM_LEAD);
    // Same moment again: already at L1 and L2 is not due yet.
    assertThat(escalate(incident, deadline.plusSeconds(1))).isEmpty();

    Instant muchLater = deadline.plus(target).plusSeconds(1);
    assertThat(escalate(incident, muchLater)).contains(EscalationLevel.ADMINISTRATOR);
    assertThat(incident.getEscalatedAt()).isEqualTo(deadline.plusSeconds(1));
  }

  @Test
  void acknowledgedTicketsDoNotEscalateForResponse() {
    Incident incident = critical();
    incident.assign(tech, tech);
    incident.acknowledge(tech);
    incident.markResponded(START.plusSeconds(60));

    assertThat(escalate(incident, responseDeadline(incident).plusSeconds(3600))).isEmpty();
  }

  @Test
  void breachedCriticalTicketGoesToTheItManager() {
    Incident incident = critical();
    incident.assign(tech, tech);
    incident.acknowledge(tech);
    incident.markResponded(START.plusSeconds(60));
    Instant breached = incident.getSlaWindow().getResolutionDeadline().plusSeconds(1);
    incident.evaluateSla(Clock.fixed(breached, ZoneOffset.UTC));

    assertThat(escalate(incident, breached)).contains(EscalationLevel.IT_MANAGER);
    assertThat(escalate(incident, breached)).isEmpty();
  }

  @Test
  void capacityDecoratorSkipsTechniciansAtTheirLimit() {
    User full = technician("full", 5, 5);
    User busy = technician("busy", 3, 5);
    User idle = technician("idle", 4, 10);
    var strategy = new CapacityAwareAssignmentStrategy(new LeastLoadedAssignmentStrategy());

    assertThat(strategy.selectTechnician(critical(), List.of(full, busy, idle))).contains(busy);
    assertThat(strategy.selectTechnician(critical(), List.of(full))).isEmpty();
  }
}
