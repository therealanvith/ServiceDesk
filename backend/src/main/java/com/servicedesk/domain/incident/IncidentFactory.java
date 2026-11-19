package com.servicedesk.domain.incident;

import com.servicedesk.domain.incident.state.ReportedState;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.sla.SlaPolicyProvider;
import com.servicedesk.domain.sla.SlaWindow;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Creational Factory ensuring consistent instantiation of valid Incident aggregates.
 */
public class IncidentFactory {

  private final PriorityCalculationStrategy priorityStrategy;
  private final Clock clock;
  private final LongSupplier ticketSequence;
  private final SlaPolicyProvider slaPolicies;

  public IncidentFactory() {
    this(new MatrixPriorityStrategy(), Clock.systemUTC(), 1000L);
  }

  public IncidentFactory(PriorityCalculationStrategy priorityStrategy, Clock clock) {
    this(priorityStrategy, clock, 1000L);
  }

  public IncidentFactory(
      PriorityCalculationStrategy priorityStrategy,
      Clock clock,
      long initialSequence) {
    this(priorityStrategy, clock, new AtomicLong(initialSequence)::incrementAndGet);
  }

  public IncidentFactory(
      PriorityCalculationStrategy priorityStrategy, Clock clock, LongSupplier ticketSequence) {
    this(priorityStrategy, clock, ticketSequence, SlaPolicyProvider.defaults());
  }

  public IncidentFactory(
      PriorityCalculationStrategy priorityStrategy,
      Clock clock,
      LongSupplier ticketSequence,
      SlaPolicyProvider slaPolicies) {
    this.slaPolicies = slaPolicies != null ? slaPolicies : SlaPolicyProvider.defaults();
    this.priorityStrategy =
        priorityStrategy != null ? priorityStrategy : new MatrixPriorityStrategy();
    this.clock = clock != null ? clock : Clock.systemUTC();
    this.ticketSequence = Objects.requireNonNull(ticketSequence);
  }

  public Incident createIncident(
      String title,
      String description,
      Category category,
      Impact impact,
      Urgency urgency,
      UserId reporterId) {
    return createIncident(title, description, category, impact, urgency, reporterId, null, null);
  }

  /**
   * Creates an incident for an affected IT service; the service's business criticality is
   * factored into the priority, and the SLA comes from the policy configured for that priority.
   */
  public Incident createIncident(
      String title,
      String description,
      Category category,
      Impact impact,
      Urgency urgency,
      UserId reporterId,
      UUID affectedServiceId,
      BusinessCriticality criticality) {
    Objects.requireNonNull(title, "Title must not be null");
    Objects.requireNonNull(description, "Description must not be null");
    Objects.requireNonNull(category, "Category must not be null");
    Objects.requireNonNull(impact, "Impact must not be null");
    Objects.requireNonNull(urgency, "Urgency must not be null");
    Objects.requireNonNull(reporterId, "Reporter ID must not be null");

    IncidentId incidentId = IncidentId.generate();
    String incidentNumber = "INC-" + ticketSequence.getAsLong();
    Priority priority = priorityStrategy.calculatePriority(impact, urgency, criticality);
    Instant now = clock.instant();
    SlaWindow slaWindow = SlaWindow.calculateInitial(slaPolicies.policyFor(priority), now);
    Incident incident = new Incident(
        incidentId,
        incidentNumber,
        title,
        description,
        category,
        priority,
        impact,
        urgency,
        reporterId,
        null, // unassigned upon initial creation
        new ReportedState(),
        slaWindow,
        now,
        now,
        clock);
    incident.setAffectedServiceId(affectedServiceId);
    return incident;
  }
}
