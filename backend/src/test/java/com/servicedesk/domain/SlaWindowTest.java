package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.sla.SlaWindow;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlaWindowTest {

  @Test
  @DisplayName("Initial SLA deadline calculation matches priority policy")
  void testInitialDeadlines() {
    Instant start = Instant.parse("2026-11-10T12:00:00Z");

    SlaWindow p1 = SlaWindow.calculateInitial(Priority.P1_CRITICAL, start);
    assertEquals(start.plus(Duration.ofMinutes(15)), p1.getResponseDeadline());
    assertEquals(start.plus(Duration.ofHours(2)), p1.getResolutionDeadline());

    SlaWindow p2 = SlaWindow.calculateInitial(Priority.P2_HIGH, start);
    assertEquals(start.plus(Duration.ofMinutes(30)), p2.getResponseDeadline());
    assertEquals(start.plus(Duration.ofHours(4)), p2.getResolutionDeadline());

    SlaWindow p3 = SlaWindow.calculateInitial(Priority.P3_MEDIUM, start);
    assertEquals(start.plus(Duration.ofHours(2)), p3.getResponseDeadline());
    assertEquals(start.plus(Duration.ofHours(8)), p3.getResolutionDeadline());

    SlaWindow p4 = SlaWindow.calculateInitial(Priority.P4_LOW, start);
    assertEquals(start.plus(Duration.ofHours(4)), p4.getResponseDeadline());
    assertEquals(start.plus(Duration.ofHours(24)), p4.getResolutionDeadline());
  }

  @Test
  @DisplayName("Pausing and resuming SLA correctly shifts resolution deadline")
  void testPauseAndResume() {
    Instant start = Instant.parse("2026-11-10T12:00:00Z");
    SlaWindow window = SlaWindow.calculateInitial(Priority.P1_CRITICAL, start);
    Instant originalResolutionDeadline = window.getResolutionDeadline();

    // Pause after 30 minutes
    Instant pauseTime = start.plus(Duration.ofMinutes(30));
    window.pause(pauseTime);
    assertTrue(window.isPaused());

    // Resume 45 minutes later
    Instant resumeTime = pauseTime.plus(Duration.ofMinutes(45));
    window.resume(resumeTime);
    assertFalse(window.isPaused());

    assertEquals(Duration.ofMinutes(45), window.getPausedDuration());
    assertEquals(originalResolutionDeadline.plus(Duration.ofMinutes(45)), window.getResolutionDeadline());
  }

  @Test
  @DisplayName("Breach evaluation accurately identifies compliance, warning, and breached states")
  void testBreachEvaluation() {
    Instant start = Instant.parse("2026-11-10T12:00:00Z");
    SlaWindow window = SlaWindow.calculateInitial(Priority.P1_CRITICAL, start); // 2 hour resolution deadline

    // At 1 hour in (60 mins remaining > 30 mins) -> WITHIN_SLA
    assertEquals(SlaStatus.WITHIN_SLA, window.checkBreach(start.plus(Duration.ofHours(1))));

    // At 1 hour 45 min in (15 mins remaining <= 30 mins) -> WARNING_BREACH_IMMINENT
    assertEquals(
        SlaStatus.WARNING_BREACH_IMMINENT,
        window.checkBreach(start.plus(Duration.ofHours(1)).plus(Duration.ofMinutes(45))));

    // At 2 hours 1 minute in -> BREACHED
    assertEquals(
        SlaStatus.BREACHED,
        window.checkBreach(start.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(1))));
  }
}
