package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.User;
import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for automated technician assignment.
 */
public interface AssignmentStrategy {

  Optional<User> selectTechnician(Incident incident, List<User> availableTechnicians);
}
