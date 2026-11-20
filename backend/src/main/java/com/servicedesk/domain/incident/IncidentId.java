package com.servicedesk.domain.incident;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Value object representing a unique incident identity. */
public final class IncidentId implements Serializable {

  private final UUID value;

  private IncidentId(UUID value) {
    this.value = Objects.requireNonNull(value, "Incident ID UUID must not be null");
  }

  public static IncidentId of(UUID value) {
    return new IncidentId(value);
  }

  public static IncidentId generate() {
    return new IncidentId(UUID.randomUUID());
  }

  public UUID getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IncidentId that = (IncidentId) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
