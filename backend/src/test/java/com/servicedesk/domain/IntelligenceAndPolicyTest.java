package com.servicedesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.intelligence.KeywordCategoryClassifier;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.WeightedPriorityStrategy;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.user.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IntelligenceAndPolicyTest {

  private final WeightedPriorityStrategy weighted = new WeightedPriorityStrategy();

  @Test
  @DisplayName("Weighted priority matches the impact/urgency matrix for a medium-criticality service")
  void weightedMatchesMatrixAtMediumCriticality() {
    MatrixPriorityStrategy matrix = new MatrixPriorityStrategy();
    for (Impact impact : Impact.values()) {
      for (Urgency urgency : Urgency.values()) {
        assertThat(weighted.calculatePriority(impact, urgency, BusinessCriticality.MEDIUM))
            .as("%s/%s", impact, urgency)
            .isEqualTo(matrix.calculatePriority(impact, urgency));
      }
    }
  }

  @Test
  @DisplayName("Business criticality raises or lowers priority at the boundaries")
  void criticalityShiftsPriority() {
    // Medium/medium is P3 normally, P1 on a critical service, P4 on a low-criticality one.
    assertThat(weighted.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.CRITICAL))
        .isEqualTo(Priority.P1_CRITICAL);
    assertThat(weighted.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.HIGH))
        .isEqualTo(Priority.P2_HIGH);
    assertThat(weighted.calculatePriority(Impact.LOW, Urgency.LOW, BusinessCriticality.LOW))
        .isEqualTo(Priority.P4_LOW);
    assertThat(weighted.calculatePriority(Impact.HIGH, Urgency.HIGH, BusinessCriticality.LOW))
        .isEqualTo(Priority.P1_CRITICAL);
    assertThat(weighted.calculatePriority(Impact.MEDIUM, Urgency.LOW, null))
        .isEqualTo(Priority.P3_MEDIUM);
  }

  @Test
  @DisplayName("Factory uses the configured SLA policy and records the affected service")
  void factoryUsesConfiguredSla() {
    Instant now = Instant.parse("2026-11-19T09:00:00Z");
    UUID service = UUID.randomUUID();
    IncidentFactory factory =
        new IncidentFactory(
            weighted,
            Clock.fixed(now, ZoneOffset.UTC),
            new AtomicLong(1000)::incrementAndGet,
            p -> new SlaPolicy(p, Duration.ofMinutes(10), Duration.ofMinutes(45)));

    Incident incident =
        factory.createIncident("Payments down", "Checkout fails", Category.APPLICATION,
            Impact.MEDIUM, Urgency.MEDIUM, UserId.generate(), service, BusinessCriticality.CRITICAL);

    assertThat(incident.getPriority()).isEqualTo(Priority.P1_CRITICAL);
    assertThat(incident.getAffectedServiceId()).isEqualTo(service);
    assertThat(incident.getSlaWindow().getResponseDeadline()).isEqualTo(now.plus(Duration.ofMinutes(10)));
    assertThat(incident.getSlaWindow().getResolutionDeadline()).isEqualTo(now.plus(Duration.ofMinutes(45)));
  }

  @Test
  @DisplayName("SLA policies reject impossible targets")
  void slaPolicyValidation() {
    assertThatThrownBy(() -> new SlaPolicy(Priority.P1_CRITICAL, Duration.ZERO, Duration.ofMinutes(30)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new SlaPolicy(Priority.P1_CRITICAL, Duration.ofMinutes(30), Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SlaPolicy.defaultFor(Priority.P2_HIGH).resolutionTarget()).isEqualTo(Duration.ofHours(2));
  }

  @Test
  @DisplayName("Category suggestion picks the dominant domain and explains why")
  void suggestsCategoryFromText() {
    KeywordCategoryClassifier classifier = new KeywordCategoryClassifier();

    var network = classifier.suggest("Wi-Fi keeps dropping", "Cannot reach the internet over VPN either");
    assertThat(network).isPresent();
    assertThat(network.get().category()).isEqualTo(Category.NETWORK);
    assertThat(network.get().matchedKeywords()).contains("wi-fi", "internet", "vpn");

    var access = classifier.suggest("Account locked", "Password reset link does not work, MFA fails");
    assertThat(access.map(s -> s.category())).contains(Category.ACCESS);

    assertThat(classifier.suggest("Hello", "Just a question")).isEmpty();
    assertThat(classifier.suggest(null, "  ")).isEmpty();
  }

  @Test
  @DisplayName("Feedback ratings must be between 1 and 5")
  void feedbackBounds() {
    Incident incident =
        new IncidentFactory().createIncident("t", "d", Category.NETWORK, Impact.LOW, Urgency.LOW, UserId.generate());
    assertThatThrownBy(() -> incident.recordFeedback(6, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> incident.recordFeedback(0, null)).isInstanceOf(IllegalArgumentException.class);
    incident.recordFeedback(5, "Quick fix");
    assertThat(incident.getFeedbackRating()).isEqualTo(5);
  }
}
