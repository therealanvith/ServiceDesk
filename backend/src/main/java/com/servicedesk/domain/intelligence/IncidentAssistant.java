package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import java.util.List;

/**
 * Port for the help desk assistant: classifies a new ticket, suggests self-help before one is
 * filed, drafts a fix and summarises a ticket for staff. Implemented by a language model adapter
 * and by a rule-based fallback, so the app works the same with or without an API key.
 */
public interface IncidentAssistant {

  /** Where an answer came from, shown to users so they know how far to trust it. */
  String source();

  TriageSuggestion triage(TicketDraft draft, List<String> serviceNames);

  /** Plain steps the reporter can try first; {@code pastFixes} are fixes for similar tickets. */
  AssistantAdvice selfHelp(TicketDraft draft, List<PastFix> pastFixes);

  AssistantAdvice suggestFix(TicketContext ticket, List<PastFix> similarResolved);

  String summarize(TicketContext ticket);

  /** What a reporter has typed so far. */
  record TicketDraft(String title, String description) {}

  /** How a similar ticket was fixed. Carries no ticket number or reporter. */
  record PastFix(String title, String resolution) {}

  /** A ticket as staff see it, with its public thread and history as plain lines. */
  record TicketContext(
      String number,
      String title,
      String description,
      String category,
      String status,
      String priority,
      List<String> timeline) {}

  /**
   * @param serviceName one of the names offered, or null
   * @param reason one short sentence explaining the classification
   */
  record TriageSuggestion(
      Category category, Impact impact, Urgency urgency, String serviceName, String reason) {}

  record AssistantAdvice(String summary, List<String> steps) {}
}
