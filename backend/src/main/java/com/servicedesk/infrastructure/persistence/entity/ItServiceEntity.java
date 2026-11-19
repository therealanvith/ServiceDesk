package com.servicedesk.infrastructure.persistence.entity;

import com.servicedesk.domain.service.BusinessCriticality;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An IT service that incidents can be reported against (e.g. payments, sign-in, Wi-Fi). */
@Entity
@Table(name = "it_services")
public class ItServiceEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false, unique = true, length = 100)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "criticality", nullable = false, length = 20)
  private BusinessCriticality criticality;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ItServiceEntity() {}

  public ItServiceEntity(
      UUID id, String name, String description, BusinessCriticality criticality, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.criticality = criticality;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BusinessCriticality getCriticality() {
    return criticality;
  }

  public void setCriticality(BusinessCriticality criticality) {
    this.criticality = criticality;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
