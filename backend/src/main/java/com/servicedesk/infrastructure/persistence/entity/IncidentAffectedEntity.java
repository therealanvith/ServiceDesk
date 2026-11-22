package com.servicedesk.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Someone who said "this affects me too" on an open issue. */
@Entity
@Table(name = "incident_affected")
@IdClass(IncidentAffectedEntity.Key.class)
public class IncidentAffectedEntity {

  public record Key(UUID incidentId, UUID userId) implements Serializable {}

  @Id
  @Column(name = "incident_id", nullable = false)
  private UUID incidentId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IncidentAffectedEntity() {}

  public IncidentAffectedEntity(UUID incidentId, UUID userId, Instant createdAt) {
    this.incidentId = incidentId;
    this.userId = userId;
    this.createdAt = createdAt;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
