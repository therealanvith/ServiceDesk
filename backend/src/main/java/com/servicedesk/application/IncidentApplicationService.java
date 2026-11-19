package com.servicedesk.application;

import com.servicedesk.api.dto.AddCommentRequest;
import com.servicedesk.api.dto.AssignIncidentRequest;
import com.servicedesk.api.dto.AuditLogResponse;
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
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.intelligence.JaccardSimilarityStrategy;
import com.servicedesk.domain.intelligence.SimilarityStrategy;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.IncidentCommentEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentCommentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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

  private AssignmentStrategy currentAssignmentStrategy;
  private String currentAssignmentStrategyName = "SKILL_MATCH";

  public IncidentApplicationService(
      IncidentRepository incidentRepository,
      UserRepository userRepository,
      AuditLogRepository auditLogRepository,
      SpringDataIncidentCommentJpaRepository commentJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      SpringDataUserJpaRepository userJpaRepository,
      List<NotificationChannel> notificationChannels,
      Clock clock) {
    this.incidentRepository = incidentRepository;
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.commentJpaRepository = commentJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.notificationChannels = notificationChannels;
    this.clock = clock;
    PriorityCalculationStrategy priorityStrategy = new MatrixPriorityStrategy();
    this.incidentFactory = new IncidentFactory(priorityStrategy, clock, incidentJpaRepository::nextIncidentNumber);
    this.eventPublisher = new DomainEventPublisher();
    this.similarityStrategy = new JaccardSimilarityStrategy();
    this.currentAssignmentStrategy = new SkillMatchAssignmentStrategy();
  }

  public IncidentDetailResponse createIncident(CreateIncidentRequest request, String reporterUsername) {
    User reporter =
        userRepository
            .findByUsername(reporterUsername)
            .orElseThrow(() -> new UserNotFoundException("Reporter not found: " + reporterUsername));

    Incident incident =
        incidentFactory.createIncident(
            request.title(),
            request.description(),
            request.category(),
            request.impact(),
            request.urgency(),
            reporter.getId());

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
            "Incident ticket created"));

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
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

    UserId targetTechId;
    String targetTechName;
    if (request != null && request.technicianId() != null) {
      User tech =
          userRepository
              .findById(UserId.of(request.technicianId()))
              .orElseThrow(() -> new UserNotFoundException("Technician not found: " + request.technicianId()));
      targetTechId = tech.getId();
      targetTechName = tech.getUsername();
    } else {
      List<User> technicians = userRepository.findTechnicians();
      User selected =
          currentAssignmentStrategy
              .selectTechnician(incident, technicians)
              .orElseThrow(() -> new IllegalStateException("No available technician for assignment"));
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
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

    IncidentStatus oldStatus = incident.getStatus();
    incident.startWork(actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Work started by technician");
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse requestInfo(
      UUID incidentId, RequestInfoRequest request, String actorUsername) {
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

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
    getIncidentDetail(incidentId, actorUsername);
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

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
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

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
    getIncidentDetail(incidentId, actorUsername);
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

    IncidentStatus oldStatus = incident.getStatus();
    incident.close(actor.getId());
    Incident saved = incidentRepository.save(incident);

    String remarks = request != null && request.remarks() != null ? request.remarks() : "Confirmed closure";
    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), remarks);
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse cancelIncident(
      UUID incidentId, CancelIncidentRequest request, String actorUsername) {
    getIncidentDetail(incidentId, actorUsername);
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

    IncidentStatus oldStatus = incident.getStatus();
    incident.cancel(request.reason(), actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Cancelled: " + request.reason());
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse overridePriority(
      UUID incidentId, OverridePriorityRequest request, String actorUsername) {
    Incident incident = loadIncident(incidentId);
    User actor = loadUser(actorUsername);

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

    return toDetailResponse(saved);
  }

  @Transactional(readOnly = true)
  public IncidentDetailResponse getIncidentDetail(UUID incidentId, String requesterUsername) {
    Incident incident = loadIncident(incidentId);
    User requester = loadUser(requesterUsername);

    if (requester.getRole() == Role.EMPLOYEE && !incident.getReporterId().equals(requester.getId())) {
      throw new UnauthorizedOperationException("Employees can only view their own incidents");
    }

    return toDetailResponse(incident);
  }

  @Transactional(readOnly = true)
  public PagedResponse<IncidentSummaryResponse> listIncidents(
      IncidentFilter filter, int page, int size, String requesterUsername) {
    User requester = loadUser(requesterUsername);

    IncidentFilter effectiveFilter = filter;
    if (requester.getRole() == Role.EMPLOYEE) {
      effectiveFilter =
          new IncidentFilter(
              filter != null ? filter.status() : null,
              filter != null ? filter.priority() : null,
              filter != null ? filter.category() : null,
              null,
              requester.getId(), // restrict to own incidents
              filter != null ? filter.slaStatus() : null,
              filter != null ? filter.searchText() : null);
    }

    List<Incident> allMatching = incidentRepository.findWithFilters(effectiveFilter);
    List<IncidentSummaryResponse> summaryList = allMatching.stream().map(this::toSummaryResponse).toList();

    return PagedResponse.of(summaryList, page, size);
  }

  public CommentResponse addComment(UUID incidentId, AddCommentRequest request, String authorUsername) {
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
    getIncidentDetail(incidentId, requesterUsername);
    User requester = loadUser(requesterUsername);
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
  public List<AuditLogResponse> getAuditLogs(UUID incidentId) {
    return auditLogRepository.findByIncidentId(IncidentId.of(incidentId)).stream()
        .map(
            log -> {
              String actorName = "System";
              if (log.actorId() != null) {
                actorName =
                    userRepository
                        .findById(log.actorId())
                        .map(User::getFullName)
                        .orElse("User (" + log.actorId() + ")");
              }
              return new AuditLogResponse(
                  log.id(),
                  log.incidentId().getValue(),
                  log.actorId() != null ? log.actorId().getValue() : null,
                  actorName,
                  log.action(),
                  log.oldValue(),
                  log.newValue(),
                  log.remarks(),
                  log.timestamp());
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SimilarIncidentResponse> getSimilarIncidents(UUID incidentId) {
    Incident target = loadIncident(incidentId);
    String targetText = target.getTitle() + " " + target.getDescription();

    List<Incident> resolvedCandidates =
        incidentRepository.findAll().stream()
            .filter(inc -> !inc.getId().equals(target.getId()))
            .filter(inc -> inc.getStatus() == IncidentStatus.RESOLVED || inc.getStatus() == IncidentStatus.CLOSED)
            .toList();

    return resolvedCandidates.stream()
        .map(
            cand -> {
              String candText = cand.getTitle() + " " + cand.getDescription();
              double score = similarityStrategy.calculateSimilarity(targetText, candText);
              return new SimilarIncidentResponse(
                  cand.getIncidentNumber(),
                  cand.getTitle(),
                  Math.round(score * 100.0) / 100.0,
                  cand.getStatus().name(),
                  cand.getResolutionSummary());
            })
        .filter(res -> res.similarityScore() >= 0.3) // 0.3 threshold for meaningful keyword suggestions
        .sorted(Comparator.comparingDouble(SimilarIncidentResponse::similarityScore).reversed())
        .limit(5)
        .toList();
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
    if (notificationChannels != null) {
      for (NotificationChannel channel : notificationChannels) {
        try {
          channel.send(message);
        } catch (Exception ignored) {
        }
      }
    }
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
        incident.getUpdatedAt());
  }

  private IncidentSummaryResponse toSummaryResponse(Incident incident) {
    String reporterName =
        userRepository
            .findById(incident.getReporterId())
            .map(User::getFullName)
            .orElse("Unknown");

    String assigneeName = "Unassigned";
    if (incident.getAssigneeId() != null) {
      assigneeName =
          userRepository
              .findById(incident.getAssigneeId())
              .map(User::getFullName)
              .orElse("Unassigned");
    }

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
        incident.getCreatedAt());
  }
}
