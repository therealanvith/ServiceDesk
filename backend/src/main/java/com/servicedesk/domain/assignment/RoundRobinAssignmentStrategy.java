package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequential round-robin assignment strategy using an atomic counter.
 */
public class RoundRobinAssignmentStrategy implements AssignmentStrategy {

  private final AtomicInteger counter;

  public RoundRobinAssignmentStrategy() {
    this(new AtomicInteger(0));
  }

  public RoundRobinAssignmentStrategy(AtomicInteger counter) {
    this.counter = counter != null ? counter : new AtomicInteger(0);
  }

  @Override
  public Optional<User> selectTechnician(Incident incident, List<User> availableTechnicians) {
    if (availableTechnicians == null || availableTechnicians.isEmpty()) {
      return Optional.empty();
    }

    List<User> activeCandidates = availableTechnicians.stream()
        .filter(User::isAvailable)
        .toList();

    if (activeCandidates.isEmpty()) {
      return Optional.empty();
    }

    int index = Math.floorMod(counter.getAndIncrement(), activeCandidates.size());
    return Optional.of(activeCandidates.get(index));
  }
}
