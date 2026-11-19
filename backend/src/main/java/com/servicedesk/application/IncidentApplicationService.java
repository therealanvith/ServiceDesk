package com.servicedesk.application;

import com.servicedesk.api.dto.AddCommentRequest;
import com.servicedesk.api.dto.AssignIncidentRequest;
import com.servicedesk.api.dto.AuditLogResponse;
import com.servicedesk.api.dto.CategorySuggestionResponse;
import com.servicedesk.api.dto.IncidentDraftRequest;
import com.servicedesk.api.dto.ServiceResponse;
import com.servicedesk.api.dto.CancelIncidentRequest;
import com.servicedesk.api.dto.CloseIncidentRequest;
import com.servicedesk.api.dto.CommentResponse;
import com.servicedesk.api.dto.CreateIncidentRequest;
import com.servicedesk.api.dto.IncidentDetailResponse;
import com.servicedesk.api.dto.IncidentSummaryResponse;
import com.servicedesk.api.dto.OverridePriorityRequest;
import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.ProvideInfoRequest;
import com.servicedesk.api.dto.RequestInfoRequest;
import com.servicedesk.api.dto.ResolveIncidentRequest;
import com.servicedesk.api.dto.SimilarIncidentResponse;
import com.servicedesk.api.dto.SlaWindowResponse;
import com.servicedesk.api.dto.UserSummaryResponse;
import com.servicedesk.domain.assignment.AssignmentStrategy;
import com.servicedesk.domain.assignment.LeastLoadedAssignmentStrategy;
import com.servicedesk.domain.assignment.RoundRobinAssignmentStrategy;
import com.servicedesk.domain.assignment.SkillMatchAssignmentStrategy;
import com.servicedesk.domain.audit.AuditAction;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.event.DomainEventPublisher;
import com.servicedesk.domain.event.IncidentAssignedEvent;
import com.servicedesk.domain.event.IncidentCreatedEvent;
import com.servicedesk.domain.event.IncidentStatusChangedEvent;
import com.servicedesk.domain.exception.IncidentNotFoundException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.intelligence.CategorySuggestionStrategy;
import com.servicedesk.domain.intelligence.JaccardSimilarityStrategy;
import com.servicedesk.domain.intelligence.KeywordCategoryClassifier;
import com.servicedesk.domain.intelligence.SimilarityStrategy;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.priority.WeightedPriorityStrategy;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.sla.SlaPolicyProvider;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.IncidentCommentEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentCommentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentApplicationService {

  private final IncidentRepository incidentRepository;
  private final UserRepository userRepository;
  private final AuditLogRepository auditLogRepository;
  private final SpringDataIncidentCommentJpaRepository commentJpaRepository;
  private final SpringDataIncidentJpaRepository incidentJpaRepository;
  private final SpringDataUserJpaRepository userJpaRepository;
  private final List<NotificationChannel> notificationChannels;
  private final Clock clock;
  private final IncidentFactory incidentFactory;
  private final DomainEventPublisher eventPublisher;
  private final SimilarityStrategy similarityStrategy;
  private final CategorySuggestionStrategy categorySuggester;
  private final SpringDataItServiceJpaRepository serviceRepository;

  private static final int MAX_PAGE_SIZE = 100;

  // Swapped at runtime from the admin screen while requests read them concurrently.
  private volatile AssignmentStrategy currentAssignmentStrategy;
  private volatile String currentAssignmentStrategyName = "SKILL_MATCH";

  public IncidentApplicationService(
      IncidentRepository incidentRepository,
      UserRepository userRepository,
      AuditLogRepository auditLogRepository,
      SpringDataIncidentCommentJpaRepository commentJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      SpringDataUserJpaRepository userJpaRepository,
      List<NotificationChannel> notificationChannels,
      Clock clock,
      SlaPolicyProvider slaPolicies,
      SpringDataItServiceJpaRepository serviceRepository) {
    this.incidentRepository = incidentRepository;
    this.serviceRepository = serviceRepository;
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.commentJpaRepository = commentJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.notificationChannels = notificationChannels;
    this.clock = clock;
    PriorityCalculationStrategy priorityStrategy = new WeightedPriorityStrategy();
    this.incidentFactory =
        new IncidentFactory(
            priorityStrategy, clock, incidentJpaRepository::nextIncidentNumber, slaPolicies);
    this.categorySuggester = new KeywordCategoryClassifier();
    this.eventPublisher = new DomainEventPublisher();
    this.similarityStrategy = new JaccardSimilarityStrategy();
    this.currentAssignmentStrategy = new SkillMatchAssignmentStrategy();
  }

  public IncidentDetailResponse createIncident(CreateIncidentRequest request, String reporterUsername) {
    User reporter =
        userRepository
            .findByUsername(reporterUsername)
            .orElseThrow(() -> new UserNotFoundException("Reporter not found: " + reporterUsername));

    ItServiceEntity service = null;
    if (request.serviceId() != null) {
      service =
          serviceRepository
              .findById(request.serviceId())
              .filter(ItServiceEntity::isActive)
              .orElseThrow(() -> new IllegalArgumentException("Select an active IT service"));
    }

    Incident incident =
        incidentFactory.createIncident(
            request.title(),
            request.description(),
            request.category(),
            request.impact(),
            request.urgency(),
            reporter.getId(),
            service != null ? service.getId() : null,
            service != null ? service.getCriticality() : null);

    // Persist the parent before audit entries and notifications reference it.
    incidentRepository.save(incident);

    // Record creation audit log
    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            reporter.getId(),
            AuditAction.CREATED.name(),
            null,
            incident.getStatus().name(),
            clock.instant(),
            "Created as " + incident.getCategory()
                + (service != null ? " on " + service.getName() : "")
                + "; priority " + incident.getPriority()
                + " from impact " + incident.getImpact()
                + ", urgency " + incident.getUrgency()
                + (service != null ? ", service criticality " + service.getCriticality() : "")));

    // Attempt automatic assignment using current strategy
    List<User> technicians = userRepository.findTechnicians();
    Optional<User> assignedTech = currentAssignmentStrategy.selectTechnician(incident, technicians);
    if (assignedTech.isPresent()) {
      User tech = assignedTech.get();
      incident.assign(tech.getId(), reporter.getId());

      auditLogRepository.save(
          AuditLog.create(
              incident.getId(),
              reporter.getId(),
              AuditAction.ASSIGNED.name(),
              null,
              tech.getUsername(),
              clock.instant(),
              "Auto-assigned via " + currentAssignmentStrategyName + " strategy"));

      notifyChannels(
          NotificationMessage.of(
              tech.getId(),
              incident.getId(),
              "ASSIGNMENT",
              "New Incident Assigned: " + incident.getIncidentNumber(),
              "You have been assigned to " + incident.getTitle()));

      eventPublisher.publish(
          new IncidentAssignedEvent(
              incident.getId(), tech.getId(), reporter.getId(), clock.instant()));
    }

    Incident saved = incidentRepository.save(incident);

    notifyChannels(
        NotificationMessage.of(
            reporter.getId(),
            saved.getId(),
            "CREATION",
            "Incident Reported: " + saved.getIncidentNumber(),
            "Your ticket has been logged with priority " + saved.getPriority()));

    eventPublisher.publish(
        new IncidentCreatedEvent(
            saved.getId(), reporter.getId(), saved.getPriority(), clock.instant()));

    return toDetailResponse(saved);
  }

  public IncidentDetailResponse assignIncident(
      UUID incidentId, AssignIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    requireAdmin(actor, "assign incidents");
    Incident incident = loadVisibleIncident(incidentId, actor);

    UserId targetTechId;
    String targetTechName;
    if (request != null && request.technicianId() != null) {
      User tech =
          userRepository
              .findById(UserId.of(request.technicianId()))
              .orElseThrow(() -> new UserNotFoundException("Technician not found: " + request.technicianId()));
      if (tech.getRole() != Role.TECHNICIAN || !tech.isAvailable()) {
        throw new IllegalArgumentException("Select an active technician for assignment");
      }
      targetTechId = tech.getId();
      targetTechName = tech.getUsername();
    } else {
      List<User> technicians = userRepository.findTechnicians();
      User selected =
          currentAssignmentStrategy
              .selectTechnician(incident, technicians)
              .orElseThrow(
                  () -> new InvalidStateTransitionException("No available technician for assignment"));
      targetTechId = selected.getId();
      targetTechName = selected.getUsername();
    }

    incident.assign(targetTechId, actor.getId());
    Incident saved = incidentRepository.save(incident);

    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            actor.getId(),
            AuditAction.ASSIGNED.name(),
            null,
            targetTechName,
            clock.instant(),
            "Incident assigned by " + actor.getUsername()));

    notifyChannels(
        NotificationMessage.of(
            targetTechId,
            incident.getId(),
            "ASSIGNMENT",
            "Incident Assigned: " + incident.getIncidentNumber(),
            "Assigned by " + actor.getUsername()));

    eventPublisher.publish(
        new IncidentAssignedEvent(incident.getId(), targetTechId, actor.getId(), clock.instant()));

    return toDetailResponse(saved);
  }

  public IncidentDetailResponse startWork(UUID incidentId, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);

    requireAssigneeOrAdmin(incident, actor, "start work on this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.startWork(actor.getId());
    incident.markResponded(clock.instant());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Work started by technician");
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse requestInfo(
      UUID incidentId, RequestInfoRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);

    requireAssigneeOrAdmin(incident, actor, "request information on this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.requestInfo(request.question(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(
        incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Clarification requested: " + request.question());

    notifyChannels(
        NotificationMessage.of(
            incident.getReporterId(),
            incident.getId(),
            "INFO_REQUESTED",
            "More Information Needed: " + incident.getIncidentNumber(),
            request.question()));

    return toDetailResponse(saved);
  }

  public IncidentDetailResponse provideInfo(
      UUID incidentId, ProvideInfoRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);
    requireReporterOrAdmin(incident, actor, "answer questions on this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.provideInfo(request.answer(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(
        incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Information provided: " + request.answer());

    if (incident.getAssigneeId() != null) {
      notifyChannels(
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "INFO_PROVIDED",
              "Information Provided: " + incident.getIncidentNumber(),
              request.answer()));
    }

    return toDetailResponse(saved);
  }

  public IncidentDetailResponse resolveIncident(
      UUID incidentId, ResolveIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);

    requireAssigneeOrAdmin(incident, actor, "resolve this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.resolve(request.resolutionSummary(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Resolution applied: " + request.resolutionSummary());

    notifyChannels(
        NotificationMessage.of(
            incident.getReporterId(),
            incident.getId(),
            "RESOLVED",
            "Incident Resolved: " + incident.getIncidentNumber(),
            "Resolution: " + request.resolutionSummary()));

    return toDetailResponse(saved);
  }

  public IncidentDetailResponse closeIncident(
      UUID incidentId, CloseIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);
    requireReporterOrAdmin(incident, actor, "confirm and close this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.close(actor.getId());
    if (request != null && request.rating() != null) {
      if (!actor.getId().equals(incident.getReporterId())) {
        throw new UnauthorizedOperationException("Only the person who reported it can rate the support");
      }
      incident.recordFeedback(request.rating(), request.remarks());
    }
    Incident saved = incidentRepository.save(incident);

    String remarks = request != null && request.remarks() != null ? request.remarks() : "Confirmed closure";
    if (request != null && request.rating() != null) {
      remarks = remarks + " (rated " + request.rating() + "/5)";
    }
    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), remarks);

    if (incident.getAssigneeId() != null) {
      notifyChannels(
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "CLOSED",
              "Incident Closed: " + incident.getIncidentNumber(),
              "The reporter confirmed the fix"
                  + (request != null && request.rating() != null
                      ? " and rated it " + request.rating() + "/5."
                      : ".")));
    }
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse cancelIncident(
      UUID incidentId, CancelIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);
    requireReporterOrAdmin(incident, actor, "cancel this incident");
    if (!actor.getRole().hasAdminRights() && incident.getStatus() != IncidentStatus.REPORTED) {
      throw new UnauthorizedOperationException(
          "Only an administrator can cancel an incident after it has been assigned");
    }

    IncidentStatus oldStatus = incident.getStatus();
    incident.cancel(request.reason(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Cancelled: " + request.reason());
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse overridePriority(
      UUID incidentId, OverridePriorityRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    requireAdmin(actor, "override priority");
    Incident incident = loadVisibleIncident(incidentId, actor);

    String oldPriority = incident.getPriority().name();
    incident.overridePriority(request.priority(), request.remarks(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            actor.getId(),
            AuditAction.PRIORITY_OVERRIDDEN.name(),
            oldPriority,
            request.priority().name(),
            clock.instant(),
            request.remarks()));

    for (UserId recipient : java.util.stream.Stream.of(incident.getReporterId(), incident.getAssigneeId())
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList()) {
      notifyChannels(
          NotificationMessage.of(
              recipient,
              incident.getId(),
              "PRIORITY_CHANGED",
              "Priority Changed: " + incident.getIncidentNumber(),
              "Priority changed from " + oldPriority + " to " + request.priority() + ": "
                  + request.remarks()));
    }

    return toDetailResponse(saved);
  }

  @Transactional(readOnly = true)
  public IncidentDetailResponse getIncidentDetail(UUID incidentId, String requesterUsername) {
    User requester = loadUser(requesterUsername);
    return toDetailResponse(loadVisibleIncident(incidentId, requester));
  }

  @Transactional(readOnly = true)
  public PagedResponse<IncidentSummaryResponse> listIncidents(
      IncidentFilter filter, int page, int size, String requesterUsername) {
    User requester = loadUser(requesterUsername);

    IncidentFilter effectiveFilter = scopeToRole(filter, requester);

    List<Incident> allMatching = incidentRepository.findWithFilters(effectiveFilter);
    PagedResponse<Incident> pageOfIncidents =
        PagedResponse.of(allMatching, page, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

    // Map only the requested page, resolving each user's name once.
    Map<UserId, String> names = new HashMap<>();
    Map<UUID, String> serviceNames = new HashMap<>();
    serviceRepository.findAll().forEach(s -> serviceNames.put(s.getId(), s.getName()));
    List<IncidentSummaryResponse> content =
        pageOfIncidents.content().stream()
            .map(inc -> toSummaryResponse(inc, names, serviceNames))
            .toList();

    return new PagedResponse<>(
        content,
        pageOfIncidents.page(),
        pageOfIncidents.size(),
        pageOfIncidents.totalElements(),
        pageOfIncidents.totalPages());
  }

  public CommentResponse addComment(UUID incidentId, AddCommentRequest request, String authorUsername) {
    loadVisibleIncident(incidentId, loadUser(authorUsername));

    IncidentEntity incident =
        incidentJpaRepository
            .findById(incidentId)
            .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));

    UserEntity author =
        userJpaRepository
            .findByUsername(authorUsername)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + authorUsername));

    if (author.getRole() == Role.EMPLOYEE && request.isInternal()) {
      throw new UnauthorizedOperationException("Employees cannot post internal comments");
    }

    if (author.getRole() == Role.EMPLOYEE && !incident.getReporter().getId().equals(author.getId())) {
      throw new UnauthorizedOperationException("Employees can only comment on their own incidents");
    }

    IncidentCommentEntity comment =
        new IncidentCommentEntity(
            UUID.randomUUID(),
            incident,
            author,
            request.commentText(),
            request.isInternal(),
            clock.instant());

    IncidentCommentEntity saved = commentJpaRepository.save(comment);

    auditLogRepository.save(
        AuditLog.create(
            IncidentId.of(incidentId),
            UserId.of(author.getId()),
            AuditAction.COMMENTED.name(),
            null,
            request.isInternal() ? "INTERNAL_NOTE" : "PUBLIC_COMMENT",
            clock.instant(),
            request.commentText()));

    return new CommentResponse(
        saved.getId(),
        incidentId,
        author.getId(),
        author.getFullName(),
        saved.getCommentText(),
        saved.isInternal(),
        saved.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<CommentResponse> getComments(UUID incidentId, String requesterUsername) {
    User requester = loadUser(requesterUsername);
    loadVisibleIncident(incidentId, requester);
    boolean isEmployee = requester.getRole() == Role.EMPLOYEE;

    List<IncidentCommentEntity> entities =
        isEmployee
            ? commentJpaRepository.findByIncidentIdAndInternalFalseOrderByCreatedAtAsc(incidentId)
            : commentJpaRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);

    return entities.stream()
        .map(
            c ->
                new CommentResponse(
                    c.getId(),
                    incidentId,
                    c.getAuthor().getId(),
                    c.getAuthor().getFullName(),
                    c.getCommentText(),
                    c.isInternal(),
                    c.getCreatedAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AuditLogResponse> getAuditLogs(UUID incidentId, String requesterUsername) {
    Incident incident = loadVisibleIncident(incidentId, loadUser(requesterUsername));
    Map<UserId, String> names = new HashMap<>();
    Map<IncidentId, String> numbers = new HashMap<>(Map.of(incident.getId(), incident.getIncidentNumber()));
    return auditLogRepository.findByIncidentId(IncidentId.of(incidentId)).stream()
        .map(log -> toAuditResponse(log, names, numbers))
        .toList();
  }

  /** Organisation-wide activity, newest first; administrators only. */
  @Transactional(readOnly = true)
  public List<AuditLogResponse> getRecentAuditLogs(int limit, String requesterUsername) {
    requireAdmin(loadUser(requesterUsername), "view the audit trail");
    Map<UserId, String> names = new HashMap<>();
    Map<IncidentId, String> numbers = new HashMap<>();
    return auditLogRepository.findRecent(Math.min(Math.max(limit, 1), 500)).stream()
        .map(log -> toAuditResponse(log, names, numbers))
        .toList();
  }

  private AuditLogResponse toAuditResponse(
      AuditLog log, Map<UserId, String> names, Map<IncidentId, String> numbers) {
    String actorName =
        log.actorId() == null ? "System" : userName(log.actorId(), names, "Former user");
    String number =
        numbers.computeIfAbsent(
            log.incidentId(),
            id -> incidentRepository.findById(id).map(Incident::getIncidentNumber).orElse(null));
    return new AuditLogResponse(
        log.id(),
        log.incidentId().getValue(),
        log.actorId() != null ? log.actorId().getValue() : null,
        actorName,
        log.action(),
        log.oldValue(),
        log.newValue(),
        log.remarks(),
        log.timestamp(),
        number);
  }

  /** Keyword-based category suggestion for a ticket that is still being written. */
  public CategorySuggestionResponse suggestCategory(IncidentDraftRequest draft) {
    return categorySuggester
        .suggest(draft.title(), draft.description())
        .map(s -> new CategorySuggestionResponse(s.category(), s.confidence(), s.matchedKeywords()))
        .orElse(new CategorySuggestionResponse(null, 0, List.of()));
  }

  /**
   * Open tickets that look like the one being written, limited to tickets the requester can see:
   * employees are warned about their own earlier reports, staff about anything in their scope.
   */
  @Transactional(readOnly = true)
  public List<SimilarIncidentResponse> findPossibleDuplicates(
      IncidentDraftRequest draft, String requesterUsername) {
    User requester = loadUser(requesterUsername);
    String text = (draft.title() == null ? "" : draft.title()) + " "
        + (draft.description() == null ? "" : draft.description());
    if (text.isBlank()) {
      return List.of();
    }
    return rankSimilar(
        text,
        incidentRepository.findAll().stream()
            .filter(inc -> isOpen(inc.getStatus()))
            .filter(inc -> canView(inc, requester))
            .toList());
  }

  private static boolean isOpen(IncidentStatus status) {
    return status.isActive() && status != IncidentStatus.RESOLVED;
  }

  private List<SimilarIncidentResponse> rankSimilar(String text, List<Incident> candidates) {
    return candidates.stream()
        .map(
            cand -> {
              double score =
                  similarityStrategy.calculateSimilarity(
                      text, cand.getTitle() + " " + cand.getDescription());
              return new SimilarIncidentResponse(
                  cand.getIncidentNumber(),
                  cand.getTitle(),
                  Math.round(score * 100.0) / 100.0,
                  cand.getStatus().name(),
                  cand.getResolutionSummary(),
                  cand.getId().getValue(),
                  isOpen(cand.getStatus()));
            })
        .filter(res -> res.similarityScore() >= 0.3)
        .sorted(Comparator.comparingDouble(SimilarIncidentResponse::similarityScore).reversed())
        .limit(5)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SimilarIncidentResponse> getSimilarIncidents(UUID incidentId, String requesterUsername) {
    User requester = loadUser(requesterUsername);
    Incident target = loadVisibleIncident(incidentId, requester);
    String targetText = target.getTitle() + " " + target.getDescription();

    // Open look-alikes are likely duplicates; resolved/closed ones may already hold the fix.
    return rankSimilar(
        targetText,
        incidentRepository.findAll().stream()
            .filter(inc -> !inc.getId().equals(target.getId()))
            .filter(inc -> inc.getStatus() != IncidentStatus.CANCELLED)
            .filter(inc -> canView(inc, requester))
            .toList());
  }

  public String getActiveAssignmentStrategyName() {
    return currentAssignmentStrategyName;
  }

  public void updateAssignmentStrategy(String strategyName) {
    switch (strategyName.toUpperCase()) {
      case "ROUND_ROBIN" -> {
        this.currentAssignmentStrategy = new RoundRobinAssignmentStrategy();
        this.currentAssignmentStrategyName = "ROUND_ROBIN";
      }
      case "LEAST_LOADED" -> {
        this.currentAssignmentStrategy = new LeastLoadedAssignmentStrategy();
        this.currentAssignmentStrategyName = "LEAST_LOADED";
      }
      case "SKILL_MATCH" -> {
        this.currentAssignmentStrategy = new SkillMatchAssignmentStrategy();
        this.currentAssignmentStrategyName = "SKILL_MATCH";
      }
      default -> throw new IllegalArgumentException("Unknown assignment strategy: " + strategyName);
    }
  }

  private void recordStatusChange(
      IncidentId incidentId, UserId actorId, IncidentStatus oldStatus, IncidentStatus newStatus, String remarks) {
    auditLogRepository.save(
        AuditLog.create(
            incidentId,
            actorId,
            AuditAction.STATUS_CHANGED.name(),
            oldStatus.name(),
            newStatus.name(),
            clock.instant(),
            remarks));

    eventPublisher.publish(
        new IncidentStatusChangedEvent(incidentId, oldStatus, newStatus, actorId, clock.instant()));
  }

  private void notifyChannels(NotificationMessage message) {
    NotificationDispatch.afterCommit(notificationChannels, message);
  }

  /**
   * Ticket visibility: admins and root see everything, technicians see tickets assigned to them,
   * and everyone sees the tickets they reported.
   */
  private boolean canView(Incident incident, User user) {
    if (user.getRole().hasAdminRights() || user.getId().equals(incident.getReporterId())) {
      return true;
    }
    return user.getRole() == Role.TECHNICIAN && user.getId().equals(incident.getAssigneeId());
  }

  /** Tickets outside the user's scope are reported as missing so their existence isn't leaked. */
  private Incident loadVisibleIncident(UUID incidentId, User user) {
    Incident incident = loadIncident(incidentId);
    if (!canView(incident, user)) {
      throw new IncidentNotFoundException("Incident not found: " + incidentId);
    }
    return incident;
  }

  private IncidentFilter scopeToRole(IncidentFilter filter, User requester) {
    IncidentFilter base =
        filter != null ? filter : new IncidentFilter(null, null, null, null, null, null, null);
    return switch (requester.getRole()) {
      case ADMINISTRATOR, ROOT -> base;
      case TECHNICIAN -> base.withAssignee(requester.getId());
      case EMPLOYEE -> base.withReporter(requester.getId());
    };
  }

  private void requireAdmin(User actor, String action) {
    if (!actor.getRole().hasAdminRights()) {
      throw new UnauthorizedOperationException("Only an administrator can " + action);
    }
  }

  private void requireAssigneeOrAdmin(Incident incident, User actor, String action) {
    boolean isAssignee =
        actor.getRole() == Role.TECHNICIAN && actor.getId().equals(incident.getAssigneeId());
    if (!actor.getRole().hasAdminRights() && !isAssignee) {
      throw new UnauthorizedOperationException(
          "Only the assigned technician or an administrator can " + action);
    }
  }

  private void requireReporterOrAdmin(Incident incident, User actor, String action) {
    if (!actor.getRole().hasAdminRights() && !actor.getId().equals(incident.getReporterId())) {
      throw new UnauthorizedOperationException(
          "Only the person who reported it or an administrator can " + action);
    }
  }

  private String userName(UserId id, Map<UserId, String> cache, String fallback) {
    return cache.computeIfAbsent(
        id, key -> userRepository.findById(key).map(User::getFullName).orElse(fallback));
  }

  private Incident loadIncident(UUID incidentId) {
    return incidentRepository
        .findById(IncidentId.of(incidentId))
        .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
  }

  private User loadUser(String username) {
    return userRepository
        .findByUsername(username)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
  }

  private IncidentDetailResponse toDetailResponse(Incident incident) {
    User reporter = userRepository.findById(incident.getReporterId()).orElse(null);
    UserSummaryResponse reporterDto =
        reporter != null
            ? new UserSummaryResponse(reporter.getId().getValue(), reporter.getFullName(), reporter.getEmail())
            : new UserSummaryResponse(incident.getReporterId().getValue(), "Unknown", "");

    UserSummaryResponse assigneeDto = null;
    if (incident.getAssigneeId() != null) {
      User assignee = userRepository.findById(incident.getAssigneeId()).orElse(null);
      if (assignee != null) {
        assigneeDto = new UserSummaryResponse(assignee.getId().getValue(), assignee.getFullName(), assignee.getEmail());
      }
    }

    SlaWindowResponse slaDto = null;
    if (incident.getSlaWindow() != null) {
      slaDto =
          new SlaWindowResponse(
              incident.getSlaWindow().getResponseDeadline(),
              incident.getSlaWindow().getResolutionDeadline(),
              incident.getSlaWindow().getStatus(),
              incident.getSlaWindow().getPausedDuration().toSeconds());
    }

    return new IncidentDetailResponse(
        incident.getId().getValue(),
        incident.getIncidentNumber(),
        incident.getTitle(),
        incident.getDescription(),
        incident.getCategory(),
        incident.getStatus(),
        incident.getPriority(),
        incident.getImpact(),
        incident.getUrgency(),
        reporterDto,
        assigneeDto,
        slaDto,
        incident.getResolutionSummary(),
        incident.getCreatedAt(),
        incident.getUpdatedAt(),
        incident.getAffectedServiceId() == null
            ? null
            : serviceRepository.findById(incident.getAffectedServiceId()).map(this::toServiceResponse).orElse(null),
        incident.getRespondedAt(),
        incident.getEscalatedAt(),
        incident.getFeedbackRating(),
        incident.getFeedbackComment());
  }

  private ServiceResponse toServiceResponse(ItServiceEntity s) {
    return new ServiceResponse(s.getId(), s.getName(), s.getDescription(), s.getCriticality(), s.isActive());
  }

  private IncidentSummaryResponse toSummaryResponse(
      Incident incident, Map<UserId, String> names, Map<UUID, String> serviceNames) {
    String reporterName = userName(incident.getReporterId(), names, "Unknown");
    String assigneeName =
        incident.getAssigneeId() != null
            ? userName(incident.getAssigneeId(), names, "Unassigned")
            : "Unassigned";

    return new IncidentSummaryResponse(
        incident.getId().getValue(),
        incident.getIncidentNumber(),
        incident.getTitle(),
        incident.getCategory(),
        incident.getStatus(),
        incident.getPriority(),
        reporterName,
        assigneeName,
        incident.getSlaWindow() != null ? incident.getSlaWindow().getStatus() : null,
        incident.getCreatedAt(),
        incident.getAffectedServiceId() != null ? serviceNames.get(incident.getAffectedServiceId()) : null);
  }
}
