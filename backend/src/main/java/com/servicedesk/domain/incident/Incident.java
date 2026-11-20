package com.servicedesk.domain.incident;

import com.servicedesk.domain.escalation.EscalationLevel;
import com.servicedesk.domain.exception.DomainValidationException;
import com.servicedesk.domain.incident.state.IncidentState;
import com.servicedesk.domain.incident.state.ReportedState;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.sla.SlaWindow;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aggregate root representing an IT service desk incident ticket. */
public class Incident {

  private final IncidentId id;
  private final String incidentNumber;
  private String title;
  private String description;
  private Category category;
  private Priority priority;
  private final Impact impact;
  private final Urgency urgency;
  private final UserId reporterId;
  private UserId assigneeId;
  private IncidentState currentState;
  private SlaWindow slaWindow;
  private String resolutionSummary;
  private final Instant createdAt;
  private Instant updatedAt;
  private Instant resolvedAt;
  private Instant closedAt;
  private UUID affectedServiceId;
  private Instant respondedAt;
  private Instant escalatedAt;
  private EscalationLevel escalationLevel = EscalationLevel.NONE;
  private IncidentId parentIncidentId;
  private Integer feedbackRating;
  private String feedbackComment;
  private Clock clock;

  public Incident(
      IncidentId id,
      String incidentNumber,
      String title,
      String description,
      Category category,
      Priority priority,
      Impact impact,
      Urgency urgency,
      UserId reporterId,
      UserId assigneeId,
      IncidentState currentState,
      SlaWindow slaWindow,
      Instant createdAt,
      Instant updatedAt,
      Clock clock) {
    this.id = Objects.requireNonNull(id, "Incident ID must not be null");
    this.incidentNumber =
        Objects.requireNonNull(incidentNumber, "Incident number must not be null");
    this.title = Objects.requireNonNull(title, "Title must not be null");
    this.description = Objects.requireNonNull(description, "Description must not be null");
    this.category = Objects.requireNonNull(category, "Category must not be null");
    this.priority = Objects.requireNonNull(priority, "Priority must not be null");
    this.impact = Objects.requireNonNull(impact, "Impact must not be null");
    this.urgency = Objects.requireNonNull(urgency, "Urgency must not be null");
    this.reporterId = Objects.requireNonNull(reporterId, "Reporter ID must not be null");
    this.assigneeId = assigneeId;
    this.currentState = currentState != null ? currentState : new ReportedState();
    this.slaWindow = slaWindow;
    this.clock = clock != null ? clock : Clock.systemUTC();
    this.createdAt = createdAt != null ? createdAt : this.clock.instant();
    this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
  }

  // --- State Pattern Delegations ---

  public void assign(UserId technicianId, UserId actorId) {
    this.currentState.assign(this, technicianId, actorId);
    this.updatedAt = getCurrentClockTime();
  }

  public void resolveWithParent(String resolutionSummary) {
    this.currentState.resolveWithParent(this, resolutionSummary);
    this.updatedAt = getCurrentClockTime();
  }

  /** The major incident this ticket is part of, or null. */
  public IncidentId getParentIncidentId() {
    return parentIncidentId;
  }

  /** Links this ticket under a major incident (null unlinks it). */
  public void linkToParent(IncidentId parentId) {
    if (parentId != null && parentId.equals(this.id)) {
      throw new DomainValidationException("A ticket can't be linked to itself");
    }
    this.parentIncidentId = parentId;
    this.updatedAt = getCurrentClockTime();
  }

  /** Restores the link when loading from storage, without touching the update time. */
  public void restoreParent(IncidentId parentId) {
    this.parentIncidentId = parentId;
  }

  public void acknowledge(UserId technicianId) {
    this.currentState.acknowledge(this, technicianId);
    this.updatedAt = getCurrentClockTime();
  }

  public void startWork(UserId technicianId) {
    this.currentState.startWork(this, technicianId);
    this.updatedAt = getCurrentClockTime();
  }

  public void requestInfo(String question, UserId technicianId) {
    this.currentState.requestInfo(this, question, technicianId);
    this.updatedAt = getCurrentClockTime();
  }

  public void provideInfo(String answer, UserId reporterId) {
    this.currentState.provideInfo(this, answer, reporterId);
    this.updatedAt = getCurrentClockTime();
  }

  public void resolve(String resolutionSummary, UserId technicianId) {
    this.currentState.resolve(this, resolutionSummary, technicianId);
    this.updatedAt = getCurrentClockTime();
  }

  public void close(UserId actorId) {
    this.currentState.close(this, actorId);
    this.updatedAt = getCurrentClockTime();
  }

  public void cancel(String reason, UserId actorId) {
    this.currentState.cancel(this, reason, actorId);
    this.updatedAt = getCurrentClockTime();
  }

  public void reopen(UserId actorId) {
    this.currentState.reopen(this, actorId);
    this.updatedAt = getCurrentClockTime();
  }

  public void overridePriority(Priority newPriority, String reason, UserId actorId) {
    Objects.requireNonNull(newPriority, "New priority must not be null");
    this.priority = newPriority;
    this.updatedAt = getCurrentClockTime();
  }

  public SlaStatus evaluateSla(Clock evaluationClock) {
    Clock effectiveClock = evaluationClock != null ? evaluationClock : this.clock;
    Instant now = effectiveClock.instant();

    if (this.slaWindow == null) {
      return SlaStatus.WITHIN_SLA;
    }

    // Closed or cancelled tickets are not breached further
    if (this.currentState.getStatus().isTerminal()
        || this.currentState.getStatus() == IncidentStatus.RESOLVED) {
      return this.slaWindow.getStatus();
    }

    SlaStatus previous = this.slaWindow.getStatus();
    SlaStatus evaluated = this.slaWindow.checkBreach(now, warningWindow());
    if (previous != SlaStatus.BREACHED
        && evaluated == SlaStatus.BREACHED
        && this.priority != Priority.P1_CRITICAL) {
      this.priority = this.priority.escalate();
    }
    this.updatedAt = now;
    return evaluated;
  }

  /** Warn when the last quarter of the (pause-adjusted) resolution window is reached. */
  private Duration warningWindow() {
    Duration total =
        Duration.between(createdAt, slaWindow.getResolutionDeadline())
            .minus(slaWindow.getPausedDuration());
    return total.isNegative() ? Duration.ZERO : total.dividedBy(4);
  }

  public void transitionTo(IncidentState newState) {
    this.currentState = Objects.requireNonNull(newState, "New state must not be null");
  }

  public Instant getCurrentClockTime() {
    return this.clock != null ? this.clock.instant() : Instant.now();
  }

  // --- Getters & Internal Setters ---

  public IncidentId getId() {
    return id;
  }

  public String getIncidentNumber() {
    return incidentNumber;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public Priority getPriority() {
    return priority;
  }

  public Impact getImpact() {
    return impact;
  }

  public Urgency getUrgency() {
    return urgency;
  }

  public UserId getReporterId() {
    return reporterId;
  }

  public UserId getAssigneeId() {
    return assigneeId;
  }

  public void setAssigneeId(UserId assigneeId) {
    this.assigneeId = assigneeId;
  }

  public IncidentState getCurrentState() {
    return currentState;
  }

  public IncidentStatus getStatus() {
    return currentState.getStatus();
  }

  public SlaWindow getSlaWindow() {
    return slaWindow;
  }

  public void setSlaWindow(SlaWindow slaWindow) {
    this.slaWindow = slaWindow;
  }

  public String getResolutionSummary() {
    return resolutionSummary;
  }

  public void setResolutionSummary(String resolutionSummary) {
    this.resolutionSummary = resolutionSummary;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public UUID getAffectedServiceId() {
    return affectedServiceId;
  }

  public void setAffectedServiceId(UUID affectedServiceId) {
    this.affectedServiceId = affectedServiceId;
  }

  /** First time a technician acted on the ticket; measured against the response target. */
  public Instant getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(Instant respondedAt) {
    this.respondedAt = respondedAt;
  }

  public void markResponded(Instant at) {
    if (this.respondedAt == null) {
      this.respondedAt = at;
    }
  }

  public boolean isResponseOverdue(Instant now) {
    return respondedAt == null
        && slaWindow != null
        && currentState.getStatus().isActive()
        && now.isAfter(slaWindow.getResponseDeadline());
  }

  public Instant getEscalatedAt() {
    return escalatedAt;
  }

  public void setEscalatedAt(Instant escalatedAt) {
    this.escalatedAt = escalatedAt;
  }

  public EscalationLevel getEscalationLevel() {
    return escalationLevel;
  }

  /** Records a new escalation level; the first escalation also stamps {@code escalatedAt}. */
  public void escalateTo(EscalationLevel level, Instant at) {
    this.escalationLevel = Objects.requireNonNull(level, "Escalation level must not be null");
    if (this.escalatedAt == null && level != EscalationLevel.NONE) {
      this.escalatedAt = at;
    }
  }

  public Integer getFeedbackRating() {
    return feedbackRating;
  }

  public String getFeedbackComment() {
    return feedbackComment;
  }

  /** Reporter's satisfaction after closure: 1 (poor) to 5 (excellent). */
  public void recordFeedback(Integer rating, String comment) {
    if (rating != null && (rating < 1 || rating > 5)) {
      throw new IllegalArgumentException("Rating must be between 1 and 5");
    }
    this.feedbackRating = rating;
    this.feedbackComment = comment;
  }

  public Clock getClock() {
    return clock;
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Incident incident = (Incident) o;
    return Objects.equals(id, incident.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
