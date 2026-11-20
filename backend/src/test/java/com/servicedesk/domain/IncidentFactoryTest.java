package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncidentFactoryTest {

  @Test
  @DisplayName(
      "Factory initializes incident with calculated priority, SLA window, and ReportedState")
  void testIncidentCreation() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-10T08:00:00Z"), ZoneOffset.UTC);
    IncidentFactory factory = new IncidentFactory(new MatrixPriorityStrategy(), clock, 2000L);
    UserId reporterId = UserId.generate();

    Incident incident =
        factory.createIncident(
            "Wi-Fi connection failing in Library",
            "Signal drops intermittently on floor 2",
            Category.NETWORK,
            Impact.MEDIUM,
            Urgency.HIGH,
            reporterId);

    assertNotNull(incident.getId());
    assertEquals("INC-2001", incident.getIncidentNumber());
    assertEquals("Wi-Fi connection failing in Library", incident.getTitle());
    assertEquals(Category.NETWORK, incident.getCategory());
    assertEquals(Priority.P2_HIGH, incident.getPriority()); // MEDIUM impact + HIGH urgency -> P2
    assertEquals(IncidentStatus.REPORTED, incident.getStatus());
    assertNull(incident.getAssigneeId());
    assertNotNull(incident.getSlaWindow());
    assertEquals(clock.instant(), incident.getCreatedAt());
  }
}
