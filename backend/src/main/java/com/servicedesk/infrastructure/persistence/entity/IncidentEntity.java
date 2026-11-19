package com.servicedesk.infrastructure.persistence.entity;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "incident_number", nullable = false, unique = true, length = 20)
  private String incidentNumber;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "description", nullable = false, columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 50)
  private Category category;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private IncidentStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false, length = 30)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "impact", nullable = false, length = 30)
  private Impact impact;

  @Enumerated(EnumType.STRING)
  @Column(name = "urgency", nullable = false, length = 30)
  private Urgency urgency;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "reporter_id", nullable = false)
  private UserEntity reporter;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assignee_id")
  private UserEntity assignee;

  @Column(name = "response_deadline", nullable = false)
  private Instant responseDeadline;

  @Column(name = "resolution_deadline", nullable = false)
  private Instant resolutionDeadline;

  @Column(name = "paused_at")
  private Instant pausedAt;

  @Column(name = "paused_duration_seconds", nullable = false)
  private long pausedDurationSeconds;

  @Enumerated(EnumType.STRING)
  @Column(name = "sla_status", nullable = false, length = 30)
  private SlaStatus slaStatus;

  @Column(name = "resolution_notes", columnDefinition = "TEXT")
  private String resolutionNotes;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "service_id")
  private UUID serviceId;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "escalated_at")
  private Instant escalatedAt;

  @Column(name = "feedback_rating")
  @JdbcTypeCode(SqlTypes.SMALLINT) // 1-5 rating, stored as SMALLINT
  private Integer feedbackRating;

  @Column(name = "feedback_comment", columnDefinition = "TEXT")
  private String feedbackComment;

  public UUID getServiceId() {
    return serviceId;
  }

  public void setServiceId(UUID serviceId) {
    this.serviceId = serviceId;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(Instant respondedAt) {
    this.respondedAt = respondedAt;
  }

  public Instant getEscalatedAt() {
    return escalatedAt;
  }

  public void setEscalatedAt(Instant escalatedAt) {
    this.escalatedAt = escalatedAt;
  }

  public Integer getFeedbackRating() {
    return feedbackRating;
  }

  public void setFeedbackRating(Integer feedbackRating) {
    this.feedbackRating = feedbackRating;
  }

  public String getFeedbackComment() {
    return feedbackComment;
  }

  public void setFeedbackComment(String feedbackComment) {
    this.feedbackComment = feedbackComment;
  }

  public IncidentEntity() {}

  // Getters and setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getIncidentNumber() {
    return incidentNumber;
  }

  public void setIncidentNumber(String incidentNumber) {
    this.incidentNumber = incidentNumber;
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

  public IncidentStatus getStatus() {
    return status;
  }

  public void setStatus(IncidentStatus status) {
    this.status = status;
  }

  public Priority getPriority() {
    return priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public Impact getImpact() {
    return impact;
  }

  public void setImpact(Impact impact) {
    this.impact = impact;
  }

  public Urgency getUrgency() {
    return urgency;
  }

  public void setUrgency(Urgency urgency) {
    this.urgency = urgency;
  }

  public UserEntity getReporter() {
    return reporter;
  }

  public void setReporter(UserEntity reporter) {
    this.reporter = reporter;
  }

  public UserEntity getAssignee() {
    return assignee;
  }

  public void setAssignee(UserEntity assignee) {
    this.assignee = assignee;
  }

  public Instant getResponseDeadline() {
    return responseDeadline;
  }

  public void setResponseDeadline(Instant responseDeadline) {
    this.responseDeadline = responseDeadline;
  }

  public Instant getResolutionDeadline() {
    return resolutionDeadline;
  }

  public void setResolutionDeadline(Instant resolutionDeadline) {
    this.resolutionDeadline = resolutionDeadline;
  }

  public Instant getPausedAt() {
    return pausedAt;
  }

  public void setPausedAt(Instant pausedAt) {
    this.pausedAt = pausedAt;
  }

  public long getPausedDurationSeconds() {
    return pausedDurationSeconds;
  }

  public void setPausedDurationSeconds(long pausedDurationSeconds) {
    this.pausedDurationSeconds = pausedDurationSeconds;
  }

  public SlaStatus getSlaStatus() {
    return slaStatus;
  }

  public void setSlaStatus(SlaStatus slaStatus) {
    this.slaStatus = slaStatus;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
