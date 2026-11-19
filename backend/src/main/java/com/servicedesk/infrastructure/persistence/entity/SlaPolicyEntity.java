package com.servicedesk.infrastructure.persistence.entity;

import com.servicedesk.domain.priority.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Response and resolution targets, in minutes, for one priority level. */
@Entity
@Table(name = "sla_policies")
public class SlaPolicyEntity {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false, length = 30)
  private Priority priority;

  @Column(name = "response_minutes", nullable = false)
  private int responseMinutes;

  @Column(name = "resolution_minutes", nullable = false)
  private int resolutionMinutes;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected SlaPolicyEntity() {}

  public SlaPolicyEntity(Priority priority, int responseMinutes, int resolutionMinutes, Instant updatedAt) {
    this.priority = priority;
    this.responseMinutes = responseMinutes;
    this.resolutionMinutes = resolutionMinutes;
    this.updatedAt = updatedAt;
  }

  public Priority getPriority() {
    return priority;
  }

  public int getResponseMinutes() {
    return responseMinutes;
  }

  public int getResolutionMinutes() {
    return resolutionMinutes;
  }

  public void update(int responseMinutes, int resolutionMinutes, Instant updatedAt) {
    this.responseMinutes = responseMinutes;
    this.resolutionMinutes = resolutionMinutes;
    this.updatedAt = updatedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
