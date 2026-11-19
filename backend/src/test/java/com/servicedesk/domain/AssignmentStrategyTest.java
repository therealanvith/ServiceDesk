package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.servicedesk.domain.assignment.LeastLoadedAssignmentStrategy;
import com.servicedesk.domain.assignment.RoundRobinAssignmentStrategy;
import com.servicedesk.domain.assignment.SkillMatchAssignmentStrategy;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssignmentStrategyTest {

  private IncidentFactory factory;
  private User techNetwork;
  private User techHardware;
  private User techGeneral;

  @BeforeEach
  void setUp() {
    factory = new IncidentFactory(new MatrixPriorityStrategy(), Clock.systemUTC());

    techNetwork = new User(
        UserId.generate(),
        "tech_net",
        "Network Technician",
        "tech_net@example.invalid",
        Role.TECHNICIAN,
        Set.of(Category.NETWORK),
        3, // 3 active tickets
        true);

    techHardware = new User(
        UserId.generate(),
        "tech_hw",
        "Hardware Technician",
        "tech_hw@example.invalid",
        Role.TECHNICIAN,
        Set.of(Category.HARDWARE),
        1, // 1 active ticket
        true);

    techGeneral = new User(
        UserId.generate(),
        "tech_lead",
        "Lead Technician",
        "tech_lead@example.invalid",
        Role.TECHNICIAN,
        Set.of(Category.NETWORK, Category.HARDWARE, Category.SOFTWARE, Category.ACCESS),
        5, // 5 active tickets
        true);
  }

  @Test
  @DisplayName("RoundRobinAssignmentStrategy distributes sequentially")
  void testRoundRobin() {
    RoundRobinAssignmentStrategy strategy = new RoundRobinAssignmentStrategy();
    Incident incident = factory.createIncident("Test", "Desc", Category.NETWORK, Impact.LOW, Urgency.LOW, UserId.generate());

    List<User> list = List.of(techNetwork, techHardware, techGeneral);

    Optional<User> first = strategy.selectTechnician(incident, list);
    Optional<User> second = strategy.selectTechnician(incident, list);
    Optional<User> third = strategy.selectTechnician(incident, list);
    Optional<User> fourth = strategy.selectTechnician(incident, list);

    assertTrue(first.isPresent());
    assertTrue(second.isPresent());
    assertTrue(third.isPresent());
    assertTrue(fourth.isPresent());

    assertEquals(techNetwork, first.get());
    assertEquals(techHardware, second.get());
    assertEquals(techGeneral, third.get());
    assertEquals(techNetwork, fourth.get()); // wraps around
  }

  @Test
  @DisplayName("LeastLoadedAssignmentStrategy picks technician with fewest active tickets")
  void testLeastLoaded() {
    LeastLoadedAssignmentStrategy strategy = new LeastLoadedAssignmentStrategy();
    Incident incident = factory.createIncident("Test", "Desc", Category.NETWORK, Impact.LOW, Urgency.LOW, UserId.generate());

    Optional<User> selected = strategy.selectTechnician(incident, List.of(techNetwork, techHardware, techGeneral));
    assertTrue(selected.isPresent());
    assertEquals(techHardware, selected.get()); // tech_hw has 1 active ticket vs 3 and 5
  }

  @Test
  @DisplayName("SkillMatchAssignmentStrategy prioritizes specialist before falling back")
  void testSkillMatch() {
    SkillMatchAssignmentStrategy strategy = new SkillMatchAssignmentStrategy();

    // Incident requiring NETWORK skill
    Incident netIncident = factory.createIncident("Switch Port", "VLAN mismatch", Category.NETWORK, Impact.HIGH, Urgency.HIGH, UserId.generate());

    // tech_net (3 tickets) and tech_lead (5 tickets) have NETWORK skill. tech_hw does not.
    Optional<User> netSpecialist = strategy.selectTechnician(netIncident, List.of(techNetwork, techHardware, techGeneral));
    assertTrue(netSpecialist.isPresent());
    assertEquals(techNetwork, netSpecialist.get()); // tech_net chosen over tech_lead because 3 < 5

    // Incident requiring ACCESS skill (only tech_lead has it)
    Incident accessIncident = factory.createIncident("Password Reset", "LDAP lock", Category.ACCESS, Impact.LOW, Urgency.LOW, UserId.generate());
    Optional<User> accessSpecialist = strategy.selectTechnician(accessIncident, List.of(techNetwork, techHardware, techGeneral));
    assertTrue(accessSpecialist.isPresent());
    assertEquals(techGeneral, accessSpecialist.get());
  }
}
