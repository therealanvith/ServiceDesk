package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncidentStateTest {

  private IncidentFactory factory;
  private Clock fixedClock;
  private UserId reporterId;
  private UserId tech1;
  private UserId tech2;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC);
    factory = new IncidentFactory(new MatrixPriorityStrategy(), fixedClock);
    reporterId = UserId.generate();
    tech1 = UserId.generate();
    tech2 = UserId.generate();
  }

  private Incident createSampleIncident() {
    return factory.createIncident(
        "Network outage in Lab 101",
        "Switch offline after reboot",
        Category.NETWORK,
        Impact.HIGH,
        Urgency.HIGH,
        reporterId);
  }

  @Test
  @DisplayName(
      "Full happy-path lifecycle: REPORTED -> ASSIGNED -> IN_PROGRESS -> PENDING_INFO -> IN_PROGRESS -> RESOLVED -> CLOSED")
  void testFullLifecycleHappyPath() {
    Incident incident = createSampleIncident();
    assertEquals(IncidentStatus.REPORTED, incident.getStatus());

    // Assign
    incident.assign(tech1, reporterId);
    assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
    assertEquals(tech1, incident.getAssigneeId());

    // Start work
    incident.startWork(tech1);
    assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

    // Request info (pauses SLA)
    incident.requestInfo("Please verify cable connection", tech1);
    assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
    assertNotNull(incident.getSlaWindow().getPausedAt());

    // Provide info (resumes SLA)
    incident.provideInfo("Cable re-plugged, green LED on", reporterId);
    assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());

    // Resolve
    incident.resolve("Switch rebooted and port reconfigured", tech1);
    assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
    assertEquals("Switch rebooted and port reconfigured", incident.getResolutionSummary());

    // Close
    incident.close(reporterId);
    assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    assertNotNull(incident.getClosedAt());
  }

  @Test
  @DisplayName("Reopening a resolved incident transitions back to IN_PROGRESS")
  void testReopenResolvedIncident() {
    Incident incident = createSampleIncident();
    incident.assign(tech1, reporterId);
    incident.startWork(tech1);
    incident.resolve("Resolved issue", tech1);
    assertEquals(IncidentStatus.RESOLVED, incident.getStatus());

    incident.reopen(reporterId);
    assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
  }

  @Test
  @DisplayName("Cancellation is allowed from REPORTED and ASSIGNED states")
  void testCancellation() {
    Incident incident1 = createSampleIncident();
    incident1.cancel("Duplicate ticket", reporterId);
    assertEquals(IncidentStatus.CANCELLED, incident1.getStatus());

    Incident incident2 = createSampleIncident();
    incident2.assign(tech1, reporterId);
    incident2.cancel("No longer needed", reporterId);
    assertEquals(IncidentStatus.CANCELLED, incident2.getStatus());
  }

  @Test
  @DisplayName("Reassignment is allowed in ASSIGNED and IN_PROGRESS states")
  void testReassignment() {
    Incident incident = createSampleIncident();
    incident.assign(tech1, reporterId);
    assertEquals(tech1, incident.getAssigneeId());

    // Reassign while ASSIGNED
    incident.assign(tech2, reporterId);
    assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
    assertEquals(tech2, incident.getAssigneeId());

    // Reassign while IN_PROGRESS transitions back to ASSIGNED
    incident.startWork(tech2);
    assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    incident.assign(tech1, reporterId);
    assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
    assertEquals(tech1, incident.getAssigneeId());
  }

  @Test
  @DisplayName("Forbidden transitions throw InvalidStateTransitionException")
  void testForbiddenTransitions() {
    Incident incident = createSampleIncident();

    // Cannot start work directly from REPORTED
    assertThrows(InvalidStateTransitionException.class, () -> incident.startWork(tech1));

    // Cannot resolve directly from REPORTED
    assertThrows(InvalidStateTransitionException.class, () -> incident.resolve("Done", tech1));

    // Cannot close directly from REPORTED
    assertThrows(InvalidStateTransitionException.class, () -> incident.close(reporterId));

    // Transition to CLOSED terminal state
    incident.assign(tech1, reporterId);
    incident.startWork(tech1);
    incident.resolve("Done", tech1);
    incident.close(reporterId);
    assertEquals(IncidentStatus.CLOSED, incident.getStatus());

    // CLOSED still rejects actions other than reopening
    assertThrows(InvalidStateTransitionException.class, () -> incident.assign(tech2, reporterId));
    assertThrows(InvalidStateTransitionException.class, () -> incident.startWork(tech1));
    assertThrows(InvalidStateTransitionException.class, () -> incident.resolve("New", tech1));
    assertThrows(
        InvalidStateTransitionException.class, () -> incident.cancel("Reason", reporterId));
  }

  @Test
  @DisplayName("A closed incident can be reopened within 14 days, not after")
  void testReopenClosedIncidentWithinWindow() {
    Incident incident = createSampleIncident();
    incident.assign(tech1, reporterId);
    incident.startWork(tech1);
    incident.resolve("Done", tech1);
    incident.close(reporterId);
    incident.recordFeedback(2, "Didn't last");

    incident.reopen(reporterId);
    assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    assertNull(incident.getClosedAt());
    assertNull(incident.getResolvedAt());
    assertNull(incident.getResolutionSummary());
    assertNull(incident.getFeedbackRating());
    assertNull(incident.getFeedbackComment());

    Incident old = createSampleIncident();
    old.assign(tech1, reporterId);
    old.startWork(tech1);
    old.resolve("Done", tech1);
    old.close(reporterId);
    Instant cutoff = fixedClock.instant().minus(java.time.Duration.ofDays(14));
    old.setClosedAt(cutoff.minusNanos(1));
    assertThrows(InvalidStateTransitionException.class, () -> old.reopen(reporterId));
    assertEquals(IncidentStatus.CLOSED, old.getStatus());
    assertEquals("Done", old.getResolutionSummary());

    old.setClosedAt(null);
    assertThrows(InvalidStateTransitionException.class, () -> old.reopen(reporterId));

    old.setClosedAt(cutoff);
    old.reopen(reporterId);
    assertEquals(IncidentStatus.IN_PROGRESS, old.getStatus());
  }
}
