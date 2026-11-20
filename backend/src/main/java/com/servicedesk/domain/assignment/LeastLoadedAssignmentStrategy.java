package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.User;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Assignment strategy selecting the available technician with the fewest active tickets. */
public class LeastLoadedAssignmentStrategy implements AssignmentStrategy {

  @Override
  public Optional<User> selectTechnician(Incident incident, List<User> availableTechnicians) {
    if (availableTechnicians == null || availableTechnicians.isEmpty()) {
      return Optional.empty();
    }

    return availableTechnicians.stream()
        .filter(User::isAvailable)
        .min(Comparator.comparingInt(User::getActiveTicketsCount));
  }
}
