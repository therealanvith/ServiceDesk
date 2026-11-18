package com.servicedesk.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_audit_logs")
public class IncidentAuditLogEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id", nullable = false)
  private IncidentEntity incident;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "actor_id")
  private UserEntity actor;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Column(name = "old_value", length = 100)
  private String oldValue;

  @Column(name = "new_value", length = 100)
  private String newValue;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public IncidentAuditLogEntity() {}

  public IncidentAuditLogEntity(
      UUID id,
      IncidentEntity incident,
      UserEntity actor,
      String action,
      String oldValue,
      String newValue,
      String remarks,
      Instant createdAt) {
    this.id = id;
    this.incident = incident;
    this.actor = actor;
    this.action = action;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.remarks = remarks;
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

  public UserEntity getActor() {
    return actor;
  }

  public void setActor(UserEntity actor) {
    this.actor = actor;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getOldValue() {
    return oldValue;
  }

  public void setOldValue(String oldValue) {
    this.oldValue = oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  public String getRemarks() {
    return remarks;
  }

  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
