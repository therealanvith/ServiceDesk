package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.User;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Decorator that keeps any assignment strategy from overloading people: technicians already at
 * their open-ticket limit are removed before the wrapped strategy chooses.
 */
public class CapacityAwareAssignmentStrategy implements AssignmentStrategy {

  private final AssignmentStrategy delegate;

  public CapacityAwareAssignmentStrategy(AssignmentStrategy delegate) {
    this.delegate = Objects.requireNonNull(delegate, "Wrapped strategy must not be null");
  }

  @Override
  public Optional<User> selectTechnician(Incident incident, List<User> availableTechnicians) {
    if (availableTechnicians == null) {
      return Optional.empty();
    }
    List<User> withCapacity = availableTechnicians.stream().filter(User::hasCapacity).toList();
    return withCapacity.isEmpty()
        ? Optional.empty()
        : delegate.selectTechnician(incident, withCapacity);
  }
}
