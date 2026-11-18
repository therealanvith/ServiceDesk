package com.servicedesk.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.servicedesk.domain.intelligence.JaccardSimilarityStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JaccardSimilarityStrategyTest {

  private final JaccardSimilarityStrategy strategy = new JaccardSimilarityStrategy();

  @Test
  @DisplayName("Identical texts yield 1.0 similarity")
  void testIdenticalTexts() {
    String text = "Lab 304 switch port trunking issue after reboot";
    assertEquals(1.0, strategy.calculateSimilarity(text, text), 0.001);
  }

  @Test
  @DisplayName("Completely disjoint texts yield 0.0 similarity")
  void testDisjointTexts() {
    String a = "printer paper jam tray 2";
    String b = "vpn connection handshake timeout error";
    assertEquals(0.0, strategy.calculateSimilarity(a, b), 0.001);
  }

  @Test
  @DisplayName("Calculates accurate Jaccard token overlap")
  void testPartialOverlap() {
    String a = "Lab 304 workstations cannot reach gateway";
    String b = "Lab 304 switch port trunking failure gateway";
    // Tokens A: lab, 304, workstations, cannot, reach, gateway (6 tokens)
    // Tokens B: lab, 304, switch, port, trunking, failure, gateway (7 tokens)
    // Intersection: lab, 304, gateway (3 tokens)
    // Union: lab, 304, workstations, cannot, reach, switch, port, trunking, failure, gateway (10 tokens)
    // Score: 3 / 10 = 0.30
    double score = strategy.calculateSimilarity(a, b);
    assertEquals(0.30, score, 0.01);
  }

  @Test
  @DisplayName("Handles null and empty string inputs gracefully")
  void testEdgeCases() {
    assertEquals(0.0, strategy.calculateSimilarity(null, "text"));
    assertEquals(0.0, strategy.calculateSimilarity("text", null));
    assertEquals(0.0, strategy.calculateSimilarity("", "   "));
  }
}
