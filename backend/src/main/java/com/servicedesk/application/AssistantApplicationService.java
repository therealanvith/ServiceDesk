package com.servicedesk.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.AssistantDtos.AdviceResponse;
import com.servicedesk.api.dto.AssistantDtos.ChatRequest;
import com.servicedesk.api.dto.AssistantDtos.ChatResponse;
import com.servicedesk.api.dto.AssistantDtos.SummaryResponse;
import com.servicedesk.api.dto.AssistantDtos.TriageRequest;
import com.servicedesk.api.dto.AssistantDtos.TriageResponse;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.intelligence.IncidentAssistant;
import com.servicedesk.domain.intelligence.IncidentAssistant.AssistantAdvice;
import com.servicedesk.domain.intelligence.IncidentAssistant.ChatTurn;
import com.servicedesk.domain.intelligence.IncidentAssistant.PastFix;
import com.servicedesk.domain.intelligence.IncidentAssistant.TicketContext;
import com.servicedesk.domain.intelligence.IncidentAssistant.TicketDraft;
import com.servicedesk.domain.intelligence.IncidentAssistant.TriageSuggestion;
import com.servicedesk.domain.intelligence.RuleBasedIncidentAssistant;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.user.User;
import com.servicedesk.infrastructure.ai.GeminiAssistantAdapter;
import com.servicedesk.infrastructure.persistence.entity.AiAdviceCacheEntity;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.SelfHelpFeedbackEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataAiAdviceCacheJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSelfHelpFeedbackJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Help desk assistant use cases. Gemini answers when it is configured; whenever it is missing, slow
 * or returns something unusable, the rule-based assistant answers instead, so every feature keeps
 * working. Each answer says which one produced it.
 */
@Service
public class AssistantApplicationService {

  private static final Logger log = LoggerFactory.getLogger(AssistantApplicationService.class);

  private final GeminiAssistantAdapter gemini;
  private final IncidentAssistant rules = new RuleBasedIncidentAssistant();
  private final IncidentApplicationService incidents;
  private final SpringDataItServiceJpaRepository services;
  private final SpringDataSelfHelpFeedbackJpaRepository feedback;
  private final UserRepository users;
  private final PriorityCalculationStrategy priorityStrategy;
  private final Clock clock;
  private final SpringDataAiAdviceCacheJpaRepository adviceCache;
  private final ObjectMapper mapper;

  public AssistantApplicationService(
      GeminiAssistantAdapter gemini,
      IncidentApplicationService incidents,
      SpringDataItServiceJpaRepository services,
      SpringDataSelfHelpFeedbackJpaRepository feedback,
      UserRepository users,
      PriorityCalculationStrategy priorityStrategy,
      Clock clock,
      SpringDataAiAdviceCacheJpaRepository adviceCache,
      ObjectMapper mapper) {
    this.adviceCache = adviceCache;
    this.mapper = mapper;
    this.gemini = gemini;
    this.incidents = incidents;
    this.services = services;
    this.feedback = feedback;
    this.users = users;
    this.priorityStrategy = priorityStrategy;
    this.clock = clock;
  }

  private record Answer<T>(T value, String source) {}

  private <T> Answer<T> ask(String what, Function<IncidentAssistant, T> question) {
    if (gemini.isEnabled()) {
      try {
        return new Answer<>(question.apply(gemini), gemini.source());
      } catch (RuntimeException e) {
        log.warn("Assistant {} fell back to rules: {}", what, e.getMessage());
      }
    }
    return new Answer<>(question.apply(rules), rules.source());
  }

  /** Suggests category, impact, urgency and service for a ticket being written. */
  @Transactional(readOnly = true)
  public TriageResponse triage(TriageRequest request) {
    List<ItServiceEntity> active =
        services.findAll().stream().filter(ItServiceEntity::isActive).toList();
    List<String> names = active.stream().map(ItServiceEntity::getName).toList();
    TicketDraft draft = new TicketDraft(request.title(), request.description());
    Answer<TriageSuggestion> answer = ask("triage", a -> a.triage(draft, names));
    TriageSuggestion s = answer.value();
    ItServiceEntity service =
        active.stream().filter(e -> e.getName().equals(s.serviceName())).findFirst().orElse(null);
    BusinessCriticality criticality =
        service != null ? service.getCriticality() : BusinessCriticality.MEDIUM;
    return new TriageResponse(
        s.category(),
        s.impact(),
        s.urgency(),
        service != null ? service.getId() : null,
        service != null ? service.getName() : null,
        priorityStrategy.calculatePriority(s.impact(), s.urgency(), criticality),
        s.reason(),
        answer.source(),
        s.itRelated());
  }

  /**
   * Steps to try before filing. Built from how similar tickets were fixed, but only their fix text
   * is used: never another ticket's number, title or reporter.
   */
  @Transactional(readOnly = true)
  public AdviceResponse selfHelp(TriageRequest request) {
    TicketDraft draft = new TicketDraft(request.title(), request.description());
    List<PastFix> fixes =
        incidents
            .resolvedFixesLike(request.title() + " " + nullToEmpty(request.description()), null)
            .stream()
            .map(fix -> new PastFix(null, fix.resolution()))
            .toList();
    String key = cacheKey("self-help", null, request.title(), request.description());
    AdviceResponse cached = cachedAdvice(key);
    if (cached != null) {
      return cached;
    }
    Answer<AssistantAdvice> answer = ask("self-help", a -> a.selfHelp(draft, fixes));
    return remember(key, "self-help", answer);
  }

  @Transactional
  public void recordSelfHelpFeedback(boolean helped, String username) {
    User user =
        users
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    feedback.save(
        new SelfHelpFeedbackEntity(
            UUID.randomUUID(), user.getId().getValue(), helped, clock.instant()));
  }

  /** A draft fix for staff working a ticket they can see. */
  @Transactional(readOnly = true)
  public AdviceResponse suggestFix(UUID incidentId, String username) {
    TicketContext ticket = incidents.assistantContext(incidentId, username);
    List<PastFix> similar =
        incidents.resolvedFixesLike(ticket.title() + " " + ticket.description(), incidentId);
    // The thread grows, but the fix is keyed to what the ticket is about so it is asked once.
    String key = cacheKey("suggest-fix", incidentId, ticket.title(), ticket.description());
    AdviceResponse cached = cachedAdvice(key);
    if (cached != null) {
      return cached;
    }
    Answer<AssistantAdvice> answer = ask("suggest-fix", a -> a.suggestFix(ticket, similar));
    return remember(key, "suggest-fix", answer);
  }

  @Transactional(readOnly = true)
  public SummaryResponse summarize(UUID incidentId, String username) {
    TicketContext ticket = incidents.assistantContext(incidentId, username);
    Answer<String> answer = ask("summary", a -> a.summarize(ticket));
    return new SummaryResponse(answer.value(), answer.source());
  }

  /** A reply in the help chat, aware of the caller's own tickets. */
  @Transactional(readOnly = true)
  public ChatResponse chat(ChatRequest request, String username) {
    List<ChatTurn> turns =
        request.messages().stream().map(m -> new ChatTurn(m.fromUser(), m.text())).toList();
    if (turns.stream().noneMatch(ChatTurn::fromUser)) {
      throw new IllegalArgumentException("Say something first");
    }
    List<String> mine = incidents.myTicketLines(username);
    Answer<String> answer = ask("chat", a -> a.chat(turns, mine));
    return new ChatResponse(answer.value(), answer.source());
  }

  /** Same question, same key: the text is trimmed, lower-cased and whitespace-collapsed. */
  private static String cacheKey(String kind, UUID incidentId, String title, String description) {
    String text =
        kind
            + "|"
            + (incidentId == null ? "" : incidentId)
            + "|"
            + normalise(title)
            + "|"
            + normalise(description);
    try {
      byte[] hash =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String normalise(String text) {
    return nullToEmpty(text).toLowerCase(java.util.Locale.ROOT).replaceAll("\s+", " ").strip();
  }

  private AdviceResponse cachedAdvice(String key) {
    try {
      return adviceCache
          .findById(key)
          .map(
              row -> {
                try {
                  List<String> steps =
                      mapper.readValue(row.getSteps(), new TypeReference<List<String>>() {});
                  return new AdviceResponse(
                      row.getSummary(), steps, row.getSource(), row.isItRelated());
                } catch (java.io.IOException e) {
                  return null;
                }
              })
          .orElse(null);
    } catch (RuntimeException e) {
      log.warn("Advice cache read failed: {}", e.getMessage());
      return null;
    }
  }

  /** Saves a language-model answer for next time; rule-based answers are free and not kept. */
  private AdviceResponse remember(String key, String kind, Answer<AssistantAdvice> answer) {
    AssistantAdvice value = answer.value();
    AdviceResponse response =
        new AdviceResponse(value.summary(), value.steps(), answer.source(), value.itRelated());
    if (gemini.source().equals(answer.source())) {
      try {
        adviceCache.save(
            new AiAdviceCacheEntity(
                key,
                kind,
                value.summary(),
                mapper.writeValueAsString(value.steps()),
                answer.source(),
                value.itRelated(),
                clock.instant()));
      } catch (java.io.IOException | RuntimeException e) {
        log.warn("Advice cache write failed: {}", e.getMessage());
      }
    }
    return response;
  }

  private static String nullToEmpty(String text) {
    return text == null ? "" : text;
  }
}
