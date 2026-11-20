package com.servicedesk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.intelligence.IncidentAssistant.AssistantAdvice;
import com.servicedesk.domain.intelligence.IncidentAssistant.PastFix;
import com.servicedesk.domain.intelligence.IncidentAssistant.TicketDraft;
import com.servicedesk.domain.intelligence.IncidentAssistant.TriageSuggestion;
import com.servicedesk.domain.intelligence.RuleBasedIncidentAssistant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedIncidentAssistantTest {

  private final RuleBasedIncidentAssistant assistant = new RuleBasedIncidentAssistant();

  @Test
  void widespreadBlockingNetworkProblemIsSerious() {
    TriageSuggestion s =
        assistant.triage(
            new TicketDraft("Wi-Fi outage", "Nobody on the whole floor can connect, urgent"),
            List.of("Campus Wi-Fi"));

    assertThat(s.category()).isEqualTo(Category.NETWORK);
    assertThat(s.impact()).isEqualTo(Impact.HIGH);
    assertThat(s.urgency()).isEqualTo(Urgency.HIGH);
    assertThat(s.reason()).isNotBlank();
  }

  @Test
  void aSinglePersonsMinorIssueStaysLow() {
    TriageSuggestion s =
        assistant.triage(
            new TicketDraft("Font looks odd", "Minor cosmetic issue in Email, no rush"),
            List.of("Email"));

    assertThat(s.impact()).isEqualTo(Impact.LOW);
    assertThat(s.urgency()).isEqualTo(Urgency.LOW);
    assertThat(s.serviceName()).isEqualTo("Email");
  }

  @Test
  void selfHelpLeadsWithPastFixesThenGenericSteps() {
    AssistantAdvice advice =
        assistant.selfHelp(
            new TicketDraft("Printer jammed", "Paper stuck in the printer"),
            List.of(
                new PastFix(null, "Opened tray 2 and removed the torn sheet. Then reprinted.")));

    assertThat(advice.steps()).hasSizeBetween(2, 4);
    assertThat(advice.steps().getFirst()).isEqualTo("Opened tray 2 and removed the torn sheet.");
  }
}
