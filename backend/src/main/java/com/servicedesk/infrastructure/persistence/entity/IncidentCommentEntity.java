package com.servicedesk.infrastructure.persistence.entity;

import com.servicedesk.domain.incident.UpdateKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_comments")
public class IncidentCommentEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private IncidentEntity incident;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "author_id", nullable = false)
  private UserEntity author;

  @Column(name = "comment_text", nullable = false, columnDefinition = "TEXT")
  private String commentText;

  @Column(name = "is_internal", nullable = false)
  private boolean internal = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20)
  private UpdateKind kind = UpdateKind.COMMENT;

  public UpdateKind getKind() {
    return kind;
  }

  public void setKind(UpdateKind kind) {
    this.kind = kind;
  }

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public IncidentCommentEntity() {}

  public IncidentCommentEntity(
      UUID id,
      IncidentEntity incident,
      UserEntity author,
      String commentText,
      boolean internal,
      Instant createdAt) {
    this.id = id;
    this.incident = incident;
    this.author = author;
    this.commentText = commentText;
    this.internal = internal;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public IncidentEntity getIncident() {
    return incident;
  }

  public void setIncident(IncidentEntity incident) {
    this.incident = incident;
  }

  public UserEntity getAuthor() {
    return author;
  }

  public void setAuthor(UserEntity author) {
    this.author = author;
  }

  public String getCommentText() {
    return commentText;
  }

  public void setCommentText(String commentText) {
    this.commentText = commentText;
  }

  public boolean isInternal() {
    return internal;
  }

  public void setInternal(boolean internal) {
    this.internal = internal;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
