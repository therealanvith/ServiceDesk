package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatrixPriorityStrategyTest {

  private final MatrixPriorityStrategy strategy = new MatrixPriorityStrategy();

  @Test
  @DisplayName("Verifies all 9 cells of the 3x3 Impact x Urgency matrix")
  void testAllPriorityMatrixCells() {
    // High Impact
    assertEquals(Priority.P1_CRITICAL, strategy.calculatePriority(Impact.HIGH, Urgency.HIGH));
    assertEquals(Priority.P1_CRITICAL, strategy.calculatePriority(Impact.HIGH, Urgency.MEDIUM));
    assertEquals(Priority.P2_HIGH, strategy.calculatePriority(Impact.HIGH, Urgency.LOW));

    // Medium Impact
    assertEquals(Priority.P2_HIGH, strategy.calculatePriority(Impact.MEDIUM, Urgency.HIGH));
    assertEquals(Priority.P3_MEDIUM, strategy.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM));
    assertEquals(Priority.P3_MEDIUM, strategy.calculatePriority(Impact.MEDIUM, Urgency.LOW));

    // Low Impact
    assertEquals(Priority.P3_MEDIUM, strategy.calculatePriority(Impact.LOW, Urgency.HIGH));
    assertEquals(Priority.P4_LOW, strategy.calculatePriority(Impact.LOW, Urgency.MEDIUM));
    assertEquals(Priority.P4_LOW, strategy.calculatePriority(Impact.LOW, Urgency.LOW));
  }
}
