package com.servicedesk.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Whether the "Try this first" steps solved someone's problem without a ticket. */
@Entity
@Table(name = "self_help_feedback")
public class SelfHelpFeedbackEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "helped", nullable = false)
  private boolean helped;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SelfHelpFeedbackEntity() {}

  public SelfHelpFeedbackEntity(UUID id, UUID userId, boolean helped, Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.helped = helped;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isHelped() {
    return helped;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
