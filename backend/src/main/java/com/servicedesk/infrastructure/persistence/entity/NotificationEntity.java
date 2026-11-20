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
@Table(name = "notifications")
public class NotificationEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recipient_id", nullable = false)
  private UserEntity recipient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id")
  private IncidentEntity incident;

  @Column(name = "type", nullable = false, length = 50)
  private String type;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "message", nullable = false, columnDefinition = "TEXT")
  private String message;

  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public NotificationEntity() {}

  public NotificationEntity(
      UUID id,
      UserEntity recipient,
      IncidentEntity incident,
      String type,
      String title,
      String message,
      boolean read,
      Instant createdAt) {
    this.id = id;
    this.recipient = recipient;
    this.incident = incident;
    this.type = type;
    this.title = title;
    this.message = message;
    this.read = read;
    this.createdAt = createdAt != null ? createdAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UserEntity getRecipient() {
    return recipient;
  }

  public void setRecipient(UserEntity recipient) {
    this.recipient = recipient;
  }

  public IncidentEntity getIncident() {
    return incident;
  }

  public void setIncident(IncidentEntity incident) {
    this.incident = incident;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public boolean isRead() {
    return read;
  }

  public void setRead(boolean read) {
    this.read = read;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
