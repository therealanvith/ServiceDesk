package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic assistant built from keyword rules and fixes on similar tickets. It is the fallback
 * whenever the language model is not configured or fails, so every feature keeps working.
 */
public class RuleBasedIncidentAssistant implements IncidentAssistant {

  private static final List<String> WIDE_IMPACT =
      List.of(
          "everyone",
          "all users",
          "whole",
          "entire",
          "outage",
          "company-wide",
          "nobody can",
          "all staff",
          "every ",
          "floor",
          "building",
          "department");
  private static final List<String> SOME_IMPACT =
      List.of("team", "several", "multiple", "a few", "colleagues", "group", "others");
  private static final List<String> HIGH_URGENCY =
      List.of(
          "urgent",
          "asap",
          "immediately",
          "right now",
          "can't work",
          "cannot work",
          "blocked",
          "down",
          "deadline",
          "today",
          "critical",
          "emergency",
          "not working at all");
  private static final List<String> LOW_URGENCY =
      List.of("when possible", "no rush", "minor", "cosmetic", "eventually", "nice to have");

  private static final Map<Category, List<String>> GENERIC_STEPS =
      Map.of(
          Category.NETWORK,
          List.of(
              "Turn Wi-Fi off and on again, or unplug and replug the network cable.",
              "Restart your computer and try again.",
              "Check whether colleagues near you have the same problem."),
          Category.HARDWARE,
          List.of(
              "Check that every cable is firmly connected and the device has power.",
              "Turn the device off, wait ten seconds and turn it back on.",
              "Try a different port or socket if one is available."),
          Category.SOFTWARE,
          List.of(
              "Close the application completely and open it again.",
              "Restart your computer to clear stuck processes.",
              "Check for and install any pending updates."),
          Category.ACCESS,
          List.of(
              "Check Caps Lock and try typing your password again slowly.",
              "Sign out completely, close the browser and sign in again.",
              "Try resetting your password from the sign-in page."));

  private final CategorySuggestionStrategy categories = new KeywordCategoryClassifier();

  @Override
  public String source() {
    return "rules";
  }

  @Override
  public TriageSuggestion triage(TicketDraft draft, List<String> serviceNames) {
    String text = lower(draft);
    var suggestion = categories.suggest(draft.title(), draft.description());
    Category category =
        suggestion.map(CategorySuggestionStrategy.Suggestion::category).orElse(Category.SOFTWARE);
    Impact impact =
        containsAny(text, WIDE_IMPACT)
            ? Impact.HIGH
            : containsAny(text, SOME_IMPACT) ? Impact.MEDIUM : Impact.LOW;
    Urgency urgency =
        containsAny(text, HIGH_URGENCY)
            ? Urgency.HIGH
            : containsAny(text, LOW_URGENCY) ? Urgency.LOW : Urgency.MEDIUM;
    String service =
        serviceNames.stream()
            .filter(name -> name != null && text.contains(name.toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null);

    List<String> evidence = new ArrayList<>();
    suggestion.ifPresent(
        s ->
            evidence.add(
                "mentions " + String.join(", ", s.matchedKeywords().stream().limit(3).toList())));
    if (impact == Impact.HIGH) {
      evidence.add("sounds like many people are affected");
    } else if (impact == Impact.MEDIUM) {
      evidence.add("sounds like a group is affected");
    }
    if (urgency == Urgency.HIGH) {
      evidence.add("work is blocked or time-critical");
    }
    String reason =
        evidence.isEmpty()
            ? "No strong signals in the text, so standard settings were chosen."
            : capitalise(String.join("; ", evidence)) + ".";
    return new TriageSuggestion(category, impact, urgency, service, reason);
  }

  @Override
  public AssistantAdvice selfHelp(TicketDraft draft, List<PastFix> pastFixes) {
    Category category =
        categories
            .suggest(draft.title(), draft.description())
            .map(CategorySuggestionStrategy.Suggestion::category)
            .orElse(Category.SOFTWARE);
    Set<String> steps = new LinkedHashSet<>();
    for (PastFix fix : pastFixes) {
      String step = firstSentence(fix.resolution());
      if (step != null) {
        steps.add(step);
      }
      if (steps.size() == 2) {
        break;
      }
    }
    var article = KnowledgeBase.best(draft.title() + " " + draft.description());
    article.ifPresent(a -> a.steps().stream().limit(4 - steps.size()).forEach(steps::add));
    for (String generic : GENERIC_STEPS.get(category)) {
      if (steps.size() >= 4) {
        break;
      }
      steps.add(generic);
    }
    String summary =
        !pastFixes.isEmpty()
            ? "Similar problems were fixed by these steps."
            : article
                .map(KnowledgeBase.Article::summary)
                .orElse(
                    "These usually fix " + category.name().toLowerCase(Locale.ROOT) + " problems.");
    return new AssistantAdvice(summary, List.copyOf(steps));
  }

  @Override
  public AssistantAdvice suggestFix(TicketContext ticket, List<PastFix> similarResolved) {
    if (similarResolved.isEmpty()) {
      var article = KnowledgeBase.best(ticket.title() + " " + ticket.description());
      if (article.isPresent()) {
        return new AssistantAdvice(
            article.get().title() + ": " + article.get().summary(), article.get().steps());
      }
      Category category = parseCategory(ticket.category());
      return new AssistantAdvice(
          "No similar ticket has been resolved yet. Standard first checks:",
          GENERIC_STEPS.get(category));
    }
    List<String> steps =
        similarResolved.stream()
            .limit(3)
            .map(fix -> "\"" + fix.title() + "\" was fixed by: " + fix.resolution())
            .toList();
    return new AssistantAdvice("Based on " + steps.size() + " similar resolved ticket(s):", steps);
  }

  @Override
  public String summarize(TicketContext ticket) {
    StringBuilder out = new StringBuilder();
    out.append(ticket.number())
        .append(" (")
        .append(ticket.priority())
        .append(", ")
        .append(ticket.status().toLowerCase(Locale.ROOT).replace('_', ' '))
        .append("): ")
        .append(ticket.title())
        .append(". ");
    List<String> timeline = ticket.timeline();
    if (timeline.isEmpty()) {
      out.append("Nothing has happened on it yet.");
    } else {
      out.append(timeline.size()).append(" update(s). Latest: ").append(timeline.getLast());
    }
    return out.toString();
  }

  @Override
  public String chat(List<ChatTurn> conversation, List<String> context) {
    String last =
        conversation.stream()
            .filter(ChatTurn::fromUser)
            .reduce((a, b) -> b)
            .map(ChatTurn::text)
            .orElse("");
    String lower = last.toLowerCase(Locale.ROOT);
    if ((lower.contains("my ticket") || lower.contains("status") || lower.contains("my issue"))
        && !context.isEmpty()) {
      return "Here are your tickets:\n" + String.join("\n", context);
    }
    var matches = KnowledgeBase.search(last, 1);
    if (matches.isEmpty()) {
      return "I can help with Wi-Fi, VPN, printers, passwords, email, monitors, slow computers and"
          + " similar problems. Tell me what is happening, or report it from New ticket and the"
          + " team will pick it up.";
    }
    var article = matches.getFirst();
    StringBuilder out = new StringBuilder(article.summary()).append("\n");
    int n = 1;
    for (String step : article.steps()) {
      out.append(n++).append(". ").append(step).append("\n");
    }
    out.append(article.escalate());
    return out.toString();
  }

  private static Category parseCategory(String value) {
    try {
      return Category.valueOf(value);
    } catch (RuntimeException e) {
      return Category.SOFTWARE;
    }
  }

  private static String lower(TicketDraft draft) {
    return ((draft.title() == null ? "" : draft.title())
            + " "
            + (draft.description() == null ? "" : draft.description()))
        .toLowerCase(Locale.ROOT);
  }

  private static boolean containsAny(String text, List<String> phrases) {
    return phrases.stream().anyMatch(text::contains);
  }

  private static String firstSentence(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String trimmed = text.strip();
    int end = trimmed.indexOf(". ");
    String sentence = end > 0 ? trimmed.substring(0, end + 1) : trimmed;
    return sentence.length() > 200 ? sentence.substring(0, 197) + "..." : sentence;
  }

  private static String capitalise(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
