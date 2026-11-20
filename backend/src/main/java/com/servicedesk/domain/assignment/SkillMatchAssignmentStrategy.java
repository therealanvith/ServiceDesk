package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.User;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Assignment strategy prioritizing technicians with verified skill category matches. Falls back to
 * another strategy (typically least-loaded) for ties or when no specialist is available.
 */
public class SkillMatchAssignmentStrategy implements AssignmentStrategy {

  private final AssignmentStrategy fallbackStrategy;

  public SkillMatchAssignmentStrategy() {
    this(new LeastLoadedAssignmentStrategy());
  }

  public SkillMatchAssignmentStrategy(AssignmentStrategy fallbackStrategy) {
    this.fallbackStrategy =
        Objects.requireNonNull(fallbackStrategy, "Fallback strategy must not be null");
  }

  @Override
  public Optional<User> selectTechnician(Incident incident, List<User> availableTechnicians) {
    if (availableTechnicians == null || availableTechnicians.isEmpty()) {
      return Optional.empty();
    }

    if (incident == null || incident.getCategory() == null) {
      return fallbackStrategy.selectTechnician(incident, availableTechnicians);
    }

    List<User> skilledTechnicians =
        availableTechnicians.stream()
            .filter(User::isAvailable)
            .filter(tech -> tech.hasSkill(incident.getCategory()))
            .toList();

    if (!skilledTechnicians.isEmpty()) {
      return fallbackStrategy.selectTechnician(incident, skilledTechnicians);
    }

    return fallbackStrategy.selectTechnician(incident, availableTechnicians);
  }
}
