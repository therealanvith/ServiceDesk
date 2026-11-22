package com.servicedesk.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Whether an incident category is currently offered to reporters. */
@Entity
@Table(name = "category_settings")
public class CategorySettingEntity {

  @Id
  @Column(name = "code", nullable = false, length = 30)
  private String code;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected CategorySettingEntity() {}

  public CategorySettingEntity(String code, boolean active) {
    this.code = code;
    this.active = active;
  }

  public String getCode() {
    return code;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
