package com.servicedesk.domain.user;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a unique user identity.
 */
public final class UserId implements Serializable {

  private final UUID value;

  private UserId(UUID value) {
    this.value = Objects.requireNonNull(value, "User ID UUID must not be null");
  }

  public static UserId of(UUID value) {
    return new UserId(value);
  }

  public static UserId generate() {
    return new UserId(UUID.randomUUID());
  }

  public UUID getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    UserId userId = (UserId) o;
    return Objects.equals(value, userId.value);
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
