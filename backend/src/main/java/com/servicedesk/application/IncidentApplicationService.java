package com.servicedesk.application;

import com.servicedesk.api.dto.AddCommentRequest;
import com.servicedesk.api.dto.AssignIncidentRequest;
import com.servicedesk.api.dto.AuditLogResponse;
import com.servicedesk.api.dto.CancelIncidentRequest;
import com.servicedesk.api.dto.CategorySuggestionResponse;
import com.servicedesk.api.dto.CloseIncidentRequest;
import com.servicedesk.api.dto.CommentResponse;
import com.servicedesk.api.dto.CreateIncidentRequest;
import com.servicedesk.api.dto.IncidentDetailResponse;
import com.servicedesk.api.dto.IncidentDraftRequest;
import com.servicedesk.api.dto.IncidentSummaryResponse;
import com.servicedesk.api.dto.MajorIncidentDtos.LinkedIncidentResponse;
import com.servicedesk.api.dto.OverridePriorityRequest;
import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.ProvideInfoRequest;
import com.servicedesk.api.dto.ReopenIncidentRequest;
import com.servicedesk.api.dto.RequestInfoRequest;
import com.servicedesk.api.dto.ResolveIncidentRequest;
import com.servicedesk.api.dto.ServiceResponse;
import com.servicedesk.api.dto.SimilarIncidentResponse;
import com.servicedesk.api.dto.SlaWindowResponse;
import com.servicedesk.api.dto.UserSummaryResponse;
import com.servicedesk.domain.assignment.AssignmentStrategy;
import com.servicedesk.domain.assignment.CapacityAwareAssignmentStrategy;
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
import com.servicedesk.domain.exception.TechnicianWorkloadExceededException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.UpdateKind;
import com.servicedesk.domain.intelligence.CategorySuggestionStrategy;
import com.servicedesk.domain.intelligence.IncidentAssistant.PastFix;
import com.servicedesk.domain.intelligence.IncidentAssistant.TicketContext;
import com.servicedesk.domain.intelligence.JaccardSimilarityStrategy;
import com.servicedesk.domain.intelligence.KeywordCategoryClassifier;
import com.servicedesk.domain.intelligence.SimilarityStrategy;
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.sla.SlaPolicyProvider;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.live.LiveEventBroadcaster;
import com.servicedesk.infrastructure.persistence.entity.CategorySettingEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentAffectedEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentCommentEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataCategorySettingJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentAffectedJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentCommentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final LiveEventBroadcaster liveEvents;
  private final SpringDataIncidentAffectedJpaRepository affectedRepository;
  private final PriorityCalculationStrategy priorityStrategy;
  private final SpringDataCategorySettingJpaRepository categorySettings;

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
      SpringDataItServiceJpaRepository serviceRepository,
      PriorityCalculationStrategy priorityStrategy,
      LiveEventBroadcaster liveEvents,
      SpringDataIncidentAffectedJpaRepository affectedRepository,
      SpringDataCategorySettingJpaRepository categorySettings) {
    this.priorityStrategy = priorityStrategy;
    this.categorySettings = categorySettings;
    this.affectedRepository = affectedRepository;
    this.liveEvents = liveEvents;
    this.incidentRepository = incidentRepository;
    this.serviceRepository = serviceRepository;
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.commentJpaRepository = commentJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.notificationChannels = notificationChannels;
    this.clock = clock;
    this.incidentFactory =
        new IncidentFactory(
            priorityStrategy, clock, incidentJpaRepository::nextIncidentNumber, slaPolicies);
    this.categorySuggester = new KeywordCategoryClassifier();
    this.eventPublisher = new DomainEventPublisher();
    this.similarityStrategy = new JaccardSimilarityStrategy();
    this.currentAssignmentStrategy =
        new CapacityAwareAssignmentStrategy(new SkillMatchAssignmentStrategy());
  }

  public IncidentDetailResponse createIncident(
      CreateIncidentRequest request, String reporterUsername) {
    User reporter =
        userRepository
            .findByUsername(reporterUsername)
            .orElseThrow(
                () -> new UserNotFoundException("Reporter not found: " + reporterUsername));

    ItServiceEntity service = null;
    if (request.serviceId() != null) {
      service =
          serviceRepository
              .findById(request.serviceId())
              .filter(ItServiceEntity::isActive)
              .orElseThrow(() -> new IllegalArgumentException("Select an active IT service"));
    }

    // A category with no row yet is available; an administrator switches one off explicitly.
    if (!categorySettings
        .findById(request.category().name())
        .map(CategorySettingEntity::isActive)
        .orElse(true)) {
      throw new IllegalArgumentException("That category is not available right now");
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

    // "This affects me too": join the existing issue when it's still open and is a main ticket.
    Incident related = null;
    if (request.relatedIncidentId() != null) {
      related =
          incidentRepository
              .findById(IncidentId.of(request.relatedIncidentId()))
              .orElseThrow(() -> new IllegalArgumentException("That issue no longer exists"));
      // A linked ticket points at its main ticket: join that one.
      if (related.getParentIncidentId() != null) {
        related =
            incidentRepository
                .findById(related.getParentIncidentId())
                .orElseThrow(() -> new IllegalArgumentException("That issue no longer exists"));
      }
      if (!isOpen(related.getStatus())) {
        throw new IllegalArgumentException(
            "That issue has already been fixed; report this as a new problem instead");
      }
    }
    if (related != null) {
      incident.linkToParent(related.getId());
    }

    // Persist the parent before audit entries and notifications reference it.
    incidentRepository.save(incident);
    if (related != null) {
      auditLogRepository.save(
          AuditLog.create(
              incident.getId(),
              reporter.getId(),
              AuditAction.LINKED.name(),
              null,
              related.getIncidentNumber(),
              clock.instant(),
              "Reported as also affected by " + related.getIncidentNumber()));
      broadcastChange(related);
    }

    // Record creation audit log
    auditLogRepository.save(
        AuditLog.create(
            incident.getId(),
            reporter.getId(),
            AuditAction.CREATED.name(),
            null,
            incident.getStatus().name(),
            clock.instant(),
            "Created as "
                + incident.getCategory()
                + (service != null ? " on " + service.getName() : "")
                + "; priority "
                + incident.getPriority()
                + " from impact "
                + incident.getImpact()
                + ", urgency "
                + incident.getUrgency()
                + (service != null ? ", service criticality " + service.getCriticality() : "")
                + (Boolean.TRUE.equals(request.aiSuggestionAccepted())
                    ? "; the assistant's suggested classification was kept"
                    : "")));

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
    } else if (technicians.stream().anyMatch(User::isAvailable)) {
      notifyAdmins(
          incident,
          "UNASSIGNED",
          "No technician has room for " + incident.getIncidentNumber(),
          "Every available technician is at their ticket limit. Assign it manually or raise a"
              + " limit under Settings.");
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

    broadcastChange(saved);
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse assignIncident(
      UUID incidentId, AssignIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    requireAdminOrTeamLead(actor, "assign incidents");
    Incident incident = loadVisibleIncident(incidentId, actor);

    UserId targetTechId;
    String targetTechName;
    if (request != null && request.technicianId() != null) {
      User tech =
          userRepository
              .findById(UserId.of(request.technicianId()))
              .orElseThrow(
                  () ->
                      new UserNotFoundException("Technician not found: " + request.technicianId()));
      if (tech.getRole() != Role.TECHNICIAN || !tech.isAvailable()) {
        throw new IllegalArgumentException("Select an active technician for assignment");
      }
      if (!tech.getId().equals(incident.getAssigneeId()) && !tech.hasCapacity()) {
        throw new TechnicianWorkloadExceededException(
            tech.getFullName()
                + " already has "
                + tech.getActiveTicketsCount()
                + " open tickets, their limit. Choose someone else or raise the limit.");
      }
      targetTechId = tech.getId();
      targetTechName = tech.getUsername();
    } else {
      List<User> technicians = userRepository.findTechnicians();
      User selected =
          currentAssignmentStrategy
              .selectTechnician(incident, technicians)
              .orElseThrow(
                  () ->
                      new TechnicianWorkloadExceededException(
                          "No available technician has room for another ticket"));
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

    broadcastChange(saved);
    return toDetailResponse(saved);
  }

  /** The assigned technician accepts the ticket, which meets the response target. */
  public IncidentDetailResponse acknowledge(UUID incidentId, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);
    requireAssigneeOrAdmin(incident, actor, "acknowledge this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.acknowledge(actor.getId());
    incident.markResponded(clock.instant());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(
        incident.getId(), actor.getId(), oldStatus, incident.getStatus(), "Acknowledged");
    postUpdate(
        incident.getId(), actor, UpdateKind.PICKUP, actor.getFullName() + " picked this up.");
    notifyChannels(
        NotificationMessage.of(
            incident.getReporterId(),
            incident.getId(),
            "ACKNOWLEDGED",
            "Picked up: " + incident.getIncidentNumber(),
            actor.getFullName() + " is handling your ticket."));
    broadcastChange(saved);
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

    recordStatusChange(
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Work started by technician");
    // Skipping the acknowledge step still tells the reporter someone has it.
    if (oldStatus == IncidentStatus.ASSIGNED) {
      postUpdate(
          incident.getId(),
          actor,
          UpdateKind.PICKUP,
          actor.getFullName() + " picked this up and is working on it.");
    }
    broadcastChange(saved);
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
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Clarification requested: " + request.question());
    postUpdate(incident.getId(), actor, UpdateKind.QUESTION, request.question());

    notifyChannels(
        NotificationMessage.of(
            incident.getReporterId(),
            incident.getId(),
            "INFO_REQUESTED",
            "More Information Needed: " + incident.getIncidentNumber(),
            request.question()));

    broadcastChange(saved);
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
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Information provided: " + request.answer());
    postUpdate(incident.getId(), actor, UpdateKind.ANSWER, request.answer());

    if (incident.getAssigneeId() != null) {
      notifyChannels(
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "INFO_PROVIDED",
              "Information Provided: " + incident.getIncidentNumber(),
              request.answer()));
    }

    broadcastChange(saved);
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
    postUpdate(incident.getId(), actor, UpdateKind.RESOLUTION, request.resolutionSummary());

    notifyChannels(
        NotificationMessage.of(
            incident.getReporterId(),
            incident.getId(),
            "RESOLVED",
            "Incident Resolved: " + incident.getIncidentNumber(),
            "Resolution: " + request.resolutionSummary()));

    resolveLinkedTickets(saved, actor, request.resolutionSummary());
    notifyAffected(saved, "Fixed: " + request.resolutionSummary());
    broadcastChange(saved);
    return toDetailResponse(saved);
  }

  /**
   * "This affects me too" without a new ticket: the caller is counted on the open main ticket and
   * told when it is fixed. Counting twice, or on one's own ticket, changes nothing.
   */
  @Transactional
  public void markAffected(UUID incidentId, String username) {
    User user = loadUser(username);
    Incident issue = mainTicket(incidentId);
    if (!isOpen(issue.getStatus())) {
      throw new IllegalArgumentException(
          "That issue has already been fixed; report this as a new problem instead");
    }
    UUID issueId = issue.getId().getValue();
    boolean already =
        issue.getReporterId().equals(user.getId())
            || affectedRepository.existsByIncidentIdAndUserId(issueId, user.getId().getValue());
    if (already) {
      return;
    }
    affectedRepository.save(
        new IncidentAffectedEntity(issueId, user.getId().getValue(), clock.instant()));
    raisePriorityForAffectedUsers(issue, user);
    if (issue.getAssigneeId() != null) {
      notifyChannels(
          NotificationMessage.of(
              issue.getAssigneeId(),
              issue.getId(),
              "AFFECTED",
              "More people affected: " + issue.getIncidentNumber(),
              "Another person says this affects them too ("
                  + (1 + affectedRepository.countByIncidentId(issueId))
                  + "+ affected)."));
    }
    broadcastChange(issue);
  }

  /**
   * More people affected can only make a ticket more urgent: the priority strategy is asked again
   * with the number of affected users, and the ticket is raised (never lowered) if it scores
   * higher. The change is audited and the reporter and assignee are told.
   */
  private void raisePriorityForAffectedUsers(Incident issue, User actor) {
    UUID issueId = issue.getId().getValue();
    int people =
        1
            + incidentRepository.findChildren(issue.getId()).size()
            + (int) affectedRepository.countByIncidentId(issueId);
    BusinessCriticality criticality =
        issue.getAffectedServiceId() == null
            ? BusinessCriticality.MEDIUM
            : serviceRepository
                .findById(issue.getAffectedServiceId())
                .map(ItServiceEntity::getCriticality)
                .orElse(BusinessCriticality.MEDIUM);
    Priority raised =
        priorityStrategy.calculatePriority(
            issue.getImpact(), issue.getUrgency(), criticality, people);
    if (raised.ordinal() >= issue.getPriority().ordinal()) {
      return;
    }
    String old = issue.getPriority().name();
    String why = "Raised automatically: " + people + " people are affected";
    issue.overridePriority(raised, why, actor.getId());
    incidentRepository.save(issue);
    auditLogRepository.save(
        AuditLog.create(
            issue.getId(),
            actor.getId(),
            AuditAction.PRIORITY_OVERRIDDEN.name(),
            old,
            raised.name(),
            clock.instant(),
            why));
    for (UserId recipient :
        java.util.stream.Stream.of(issue.getReporterId(), issue.getAssigneeId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList()) {
      notifyChannels(
          NotificationMessage.of(
              recipient,
              issue.getId(),
              "PRIORITY_CHANGED",
              "Priority Changed: " + issue.getIncidentNumber(),
              "Priority changed from " + old + " to " + raised + ": " + why));
    }
  }

  @Transactional
  public void unmarkAffected(UUID incidentId, String username) {
    User user = loadUser(username);
    Incident issue = mainTicket(incidentId);
    UUID userId = user.getId().getValue();
    if (affectedRepository.existsByIncidentIdAndUserId(issue.getId().getValue(), userId)) {
      affectedRepository.deleteById(
          new IncidentAffectedEntity.Key(issue.getId().getValue(), userId));
    }
  }

  private Incident mainTicket(UUID incidentId) {
    Incident issue =
        incidentRepository
            .findById(IncidentId.of(incidentId))
            .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
    // A linked ticket points at its main ticket: that is the one people join.
    if (issue.getParentIncidentId() != null) {
      return incidentRepository
          .findById(issue.getParentIncidentId())
          .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
    }
    return issue;
  }

  private void notifyAffected(Incident issue, String detail) {
    for (IncidentAffectedEntity row :
        affectedRepository.findByIncidentId(issue.getId().getValue())) {
      notifyChannels(
          NotificationMessage.of(
              UserId.of(row.getUserId()),
              issue.getId(),
              "RESOLVED",
              "Issue fixed: " + issue.getIncidentNumber(),
              detail));
    }
  }

  /** Every open ticket linked under a major incident is resolved with it, and its reporter told. */
  private void resolveLinkedTickets(Incident parent, User actor, String resolutionSummary) {
    for (Incident child : incidentRepository.findChildren(parent.getId())) {
      if (!isOpen(child.getStatus())) {
        continue;
      }
      IncidentStatus before = child.getStatus();
      String summary = "Resolved with " + parent.getIncidentNumber() + ": " + resolutionSummary;
      child.resolveWithParent(summary);
      Incident savedChild = incidentRepository.save(child);
      recordStatusChange(
          child.getId(),
          actor.getId(),
          before,
          child.getStatus(),
          "Resolved together with major incident " + parent.getIncidentNumber());
      postUpdate(child.getId(), actor, UpdateKind.RESOLUTION, summary);
      notifyChannels(
          NotificationMessage.of(
              child.getReporterId(),
              child.getId(),
              "RESOLVED",
              "Incident Resolved: " + child.getIncidentNumber(),
              "Fixed as part of " + parent.getIncidentNumber() + ": " + resolutionSummary));
      broadcastChange(savedChild);
    }
  }

  /**
   * Links a ticket under a major incident (administrators only). One level deep: a main ticket
   * can't itself be linked, and a linked ticket can't have its own linked tickets.
   */
  public IncidentDetailResponse linkToParent(UUID childId, UUID parentId, String actorUsername) {
    User actor = loadUser(actorUsername);
    requireAdmin(actor, "link tickets");
    Incident child = loadVisibleIncident(childId, actor);
    Incident parent = loadVisibleIncident(parentId, actor);
    requireLinkable(child, parent);

    child.linkToParent(parent.getId());
    Incident saved = incidentRepository.save(child);
    auditLogRepository.save(
        AuditLog.create(
            child.getId(),
            actor.getId(),
            AuditAction.LINKED.name(),
            null,
            parent.getIncidentNumber(),
            clock.instant(),
            "Linked to major incident " + parent.getIncidentNumber()));
    postUpdate(
        child.getId(),
        actor,
        UpdateKind.STATUS,
        "Linked to "
            + parent.getIncidentNumber()
            + ", a known issue affecting several people. It will be resolved together with it.");
    notifyChannels(
        NotificationMessage.of(
            child.getReporterId(),
            child.getId(),
            "LINKED",
            "Known issue: " + child.getIncidentNumber(),
            "Your ticket is part of "
                + parent.getIncidentNumber()
                + " ("
                + parent.getTitle()
                + "). You'll hear when it's fixed."));
    broadcastChange(saved);
    broadcastChange(parent);
    return toDetailResponse(saved);
  }

  public IncidentDetailResponse unlinkFromParent(UUID childId, String actorUsername) {
    User actor = loadUser(actorUsername);
    requireAdmin(actor, "unlink tickets");
    Incident child = loadVisibleIncident(childId, actor);
    IncidentId parentId = child.getParentIncidentId();
    if (parentId == null) {
      throw new IllegalArgumentException("This ticket isn't linked to a major incident");
    }
    String parentNumber =
        incidentRepository.findById(parentId).map(Incident::getIncidentNumber).orElse("—");
    child.linkToParent(null);
    Incident saved = incidentRepository.save(child);
    auditLogRepository.save(
        AuditLog.create(
            child.getId(),
            actor.getId(),
            AuditAction.UNLINKED.name(),
            parentNumber,
            null,
            clock.instant(),
            "Unlinked from " + parentNumber));
    postUpdate(
        child.getId(), actor, UpdateKind.STATUS, "No longer linked to " + parentNumber + ".");
    broadcastChange(saved);
    incidentRepository.findById(parentId).ifPresent(this::broadcastChange);
    return toDetailResponse(saved);
  }

  private void requireLinkable(Incident child, Incident parent) {
    if (child.getId().equals(parent.getId())) {
      throw new IllegalArgumentException("A ticket can't be linked to itself");
    }
    if (!isOpen(child.getStatus()) || !isOpen(parent.getStatus())) {
      throw new InvalidStateTransitionException("Only open tickets can be linked");
    }
    if (parent.getParentIncidentId() != null) {
      throw new IllegalArgumentException(
          "That ticket is itself linked to another one; link to the main ticket instead");
    }
    if (!incidentRepository.findChildren(child.getId()).isEmpty()) {
      throw new IllegalArgumentException(
          "This ticket has its own linked tickets, so it can't be linked under another");
    }
  }

  /** The reporter says the fix didn't work: the ticket goes back to the technician. */
  public IncidentDetailResponse reopenIncident(
      UUID incidentId, ReopenIncidentRequest request, String actorUsername) {
    User actor = loadUser(actorUsername);
    Incident incident = loadVisibleIncident(incidentId, actor);
    requireReporterOrAdmin(incident, actor, "reopen this incident");

    IncidentStatus oldStatus = incident.getStatus();
    incident.reopen(actor.getId());
    Incident saved = incidentRepository.save(incident);

    recordStatusChange(
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Reopened: " + request.reason());
    postUpdate(
        incident.getId(),
        actor,
        UpdateKind.STATUS,
        (actor.getId().equals(incident.getReporterId())
                ? "Reopened by the reporter: "
                : "Reopened by " + actor.getFullName() + ": ")
            + request.reason());
    if (incident.getAssigneeId() != null) {
      notifyChannels(
          NotificationMessage.of(
              incident.getAssigneeId(),
              incident.getId(),
              "REOPENED",
              "Reopened: " + incident.getIncidentNumber(),
              "The fix didn't work: " + request.reason()));
    }
    broadcastChange(saved);
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
        throw new UnauthorizedOperationException(
            "Only the person who reported it can rate the support");
      }
      incident.recordFeedback(request.rating(), request.remarks());
    }
    Incident saved = incidentRepository.save(incident);

    String remarks =
        request != null && request.remarks() != null ? request.remarks() : "Confirmed closure";
    if (request != null && request.rating() != null) {
      remarks = remarks + " (rated " + request.rating() + "/5)";
    }
    recordStatusChange(incident.getId(), actor.getId(), oldStatus, incident.getStatus(), remarks);
    // Public entry: no reporter name, rating or remarks (those stay in the audit trail).
    postUpdate(
        incident.getId(),
        actor,
        UpdateKind.STATUS,
        actor.getId().equals(incident.getReporterId())
            ? "The reporter confirmed the fix and closed the ticket."
            : "Closed by " + actor.getFullName() + ".");

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
    broadcastChange(saved);
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

    recordStatusChange(
        incident.getId(),
        actor.getId(),
        oldStatus,
        incident.getStatus(),
        "Cancelled: " + request.reason());
    postUpdate(
        incident.getId(),
        actor,
        UpdateKind.STATUS,
        (actor.getId().equals(incident.getReporterId())
                ? "Cancelled by the reporter: "
                : "Cancelled by " + actor.getFullName() + ": ")
            + request.reason());
    broadcastChange(saved);
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

    for (UserId recipient :
        java.util.stream.Stream.of(incident.getReporterId(), incident.getAssigneeId())
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList()) {
      notifyChannels(
          NotificationMessage.of(
              recipient,
              incident.getId(),
              "PRIORITY_CHANGED",
              "Priority Changed: " + incident.getIncidentNumber(),
              "Priority changed from "
                  + oldPriority
                  + " to "
                  + request.priority()
                  + ": "
                  + request.remarks()));
    }

    broadcastChange(saved);
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
    // Issues the employee joined with "this affects me too" sit beside the ones they reported.
    Set<UUID> joinedIds = new HashSet<>();
    if (requester.getRole() == Role.EMPLOYEE) {
      affectedRepository
          .findByUserId(requester.getId().getValue())
          .forEach(row -> joinedIds.add(row.getIncidentId()));
      if (!joinedIds.isEmpty()) {
        Set<UUID> have = new HashSet<>();
        for (Incident inc : allMatching) {
          have.add(inc.getId().getValue());
        }
        List<Incident> merged = new ArrayList<>(allMatching);
        incidentRepository.findWithFilters(effectiveFilter.unscoped()).stream()
            .filter(
                inc ->
                    joinedIds.contains(inc.getId().getValue())
                        && !have.contains(inc.getId().getValue()))
            .forEach(merged::add);
        merged.sort(Comparator.comparing(Incident::getCreatedAt).reversed());
        allMatching = merged;
      }
    }
    PagedResponse<Incident> pageOfIncidents =
        PagedResponse.of(allMatching, page, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

    // Map only the requested page, resolving each user's name once.
    Map<UserId, String> names = new HashMap<>();
    Map<UUID, String> serviceNames = new HashMap<>();
    serviceRepository.findAll().forEach(s -> serviceNames.put(s.getId(), s.getName()));
    List<IncidentSummaryResponse> content =
        pageOfIncidents.content().stream()
            .map(
                inc -> {
                  IncidentSummaryResponse row = toSummaryResponse(inc, names, serviceNames);
                  boolean followed =
                      joinedIds.contains(inc.getId().getValue())
                          && !inc.getReporterId().equals(requester.getId());
                  return followed ? row.asJoined() : row;
                })
            .toList();

    return new PagedResponse<>(
        content,
        pageOfIncidents.page(),
        pageOfIncidents.size(),
        pageOfIncidents.totalElements(),
        pageOfIncidents.totalPages());
  }

  public CommentResponse addComment(
      UUID incidentId, AddCommentRequest request, String authorUsername) {
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

    if (author.getRole() == Role.EMPLOYEE
        && !incident.getReporter().getId().equals(author.getId())) {
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
    broadcastChange(loadIncident(incidentId));

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
        saved.getCreatedAt(),
        saved.getKind(),
        author.getRole());
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
                    c.getCreatedAt(),
                    c.getKind(),
                    c.getAuthor().getRole()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AuditLogResponse> getAuditLogs(UUID incidentId, String requesterUsername) {
    Incident incident = loadVisibleIncident(incidentId, loadUser(requesterUsername));
    Map<UserId, String> names = new HashMap<>();
    Map<IncidentId, String> numbers =
        new HashMap<>(Map.of(incident.getId(), incident.getIncidentNumber()));
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
    String text =
        (draft.title() == null ? "" : draft.title())
            + " "
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
  public List<SimilarIncidentResponse> getSimilarIncidents(
      UUID incidentId, String requesterUsername) {
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

  /** A ticket the user may see, flattened for the assistant (staff endpoints only). */
  @Transactional(readOnly = true)
  public TicketContext assistantContext(UUID incidentId, String username) {
    User viewer = loadUser(username);
    Incident incident = loadVisibleIncident(incidentId, viewer);
    // Staff see the whole thread; the reporter only gets what is public.
    boolean staff = viewer.getRole() != Role.EMPLOYEE;
    List<String> timeline =
        commentJpaRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
            .filter(c -> staff || !c.isInternal())
            .map(
                c ->
                    (c.isInternal() ? "Internal note" : c.getKind().name())
                        + " by "
                        + c.getAuthor().getFullName()
                        + ": "
                        + c.getCommentText())
            .toList();
    if (timeline.size() > 30) {
      timeline = timeline.subList(timeline.size() - 30, timeline.size());
    }
    return new TicketContext(
        incident.getIncidentNumber(),
        incident.getTitle(),
        incident.getDescription(),
        incident.getCategory().name(),
        incident.getStatus().name(),
        incident.getPriority().name(),
        timeline);
  }

  /** The caller's own tickets as short lines, newest first, for the help chat. */
  @Transactional(readOnly = true)
  public List<String> myTicketLines(String username) {
    User me = loadUser(username);
    return incidentRepository
        .findWithFilters(IncidentFilter.empty().withReporter(me.getId()))
        .stream()
        .sorted(Comparator.comparing(Incident::getCreatedAt).reversed())
        .limit(10)
        .map(
            i ->
                i.getIncidentNumber()
                    + " \""
                    + i.getTitle()
                    + "\" is "
                    + i.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' '))
        .toList();
  }

  /** How the closest resolved tickets were fixed, best match first (at most three). */
  @Transactional(readOnly = true)
  public List<PastFix> resolvedFixesLike(String text, UUID excludeId) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    record Scored(Incident incident, double score) {}
    return incidentRepository.findAll().stream()
        .filter(
            inc ->
                inc.getStatus() == IncidentStatus.RESOLVED
                    || inc.getStatus() == IncidentStatus.CLOSED)
        .filter(inc -> inc.getResolutionSummary() != null && !inc.getResolutionSummary().isBlank())
        .filter(inc -> excludeId == null || !inc.getId().getValue().equals(excludeId))
        .map(
            inc ->
                new Scored(
                    inc,
                    similarityStrategy.calculateSimilarity(
                        text, inc.getTitle() + " " + inc.getDescription())))
        .filter(s -> s.score() >= 0.2)
        .sorted(Comparator.comparingDouble(Scored::score).reversed())
        .limit(3)
        .map(s -> new PastFix(s.incident().getTitle(), s.incident().getResolutionSummary()))
        .toList();
  }

  public String getActiveAssignmentStrategyName() {
    return currentAssignmentStrategyName;
  }

  public void updateAssignmentStrategy(String strategyName) {
    switch (strategyName.toUpperCase()) {
      case "ROUND_ROBIN" -> {
        this.currentAssignmentStrategy =
            new CapacityAwareAssignmentStrategy(new RoundRobinAssignmentStrategy());
        this.currentAssignmentStrategyName = "ROUND_ROBIN";
      }
      case "LEAST_LOADED" -> {
        this.currentAssignmentStrategy =
            new CapacityAwareAssignmentStrategy(new LeastLoadedAssignmentStrategy());
        this.currentAssignmentStrategyName = "LEAST_LOADED";
      }
      case "SKILL_MATCH" -> {
        this.currentAssignmentStrategy =
            new CapacityAwareAssignmentStrategy(new SkillMatchAssignmentStrategy());
        this.currentAssignmentStrategyName = "SKILL_MATCH";
      }
      default -> throw new IllegalArgumentException("Unknown assignment strategy: " + strategyName);
    }
  }

  /**
   * Writes a system entry into the ticket's public Updates thread, in the same transaction as the
   * action it describes.
   */
  private void postUpdate(IncidentId incidentId, User actor, UpdateKind kind, String text) {
    IncidentEntity incident =
        incidentJpaRepository
            .findById(incidentId.getValue())
            .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + incidentId));
    UserEntity author =
        userJpaRepository
            .findById(actor.getId().getValue())
            .orElseThrow(() -> new UserNotFoundException("User not found: " + actor.getUsername()));
    IncidentCommentEntity entry =
        new IncidentCommentEntity(
            UUID.randomUUID(), incident, author, text, false, clock.instant());
    entry.setKind(kind);
    commentJpaRepository.save(entry);
  }

  private void recordStatusChange(
      IncidentId incidentId,
      UserId actorId,
      IncidentStatus oldStatus,
      IncidentStatus newStatus,
      String remarks) {
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

  /**
   * Tells the reporter, the assignee and administrators' open pages that a ticket changed, once the
   * change is committed. Pages refetch, so only the id and status travel.
   */
  private void broadcastChange(Incident incident) {
    Set<UUID> audience = new HashSet<>();
    audience.add(incident.getReporterId().getValue());
    if (incident.getAssigneeId() != null) {
      audience.add(incident.getAssigneeId().getValue());
    }
    userRepository.findAll().stream()
        .filter(u -> u.getRole().hasAdminRights() && u.isAvailable())
        .forEach(u -> audience.add(u.getId().getValue()));
    Map<String, Object> data =
        Map.of(
            "incidentId", incident.getId().getValue(),
            "incidentNumber", incident.getIncidentNumber(),
            "status", incident.getStatus().name());
    NotificationDispatch.runAfterCommit(
        () -> audience.forEach(userId -> liveEvents.send(userId, "incident-updated", data)));
  }

  private void notifyAdmins(Incident incident, String type, String title, String body) {
    userRepository.findAll().stream()
        .filter(u -> u.getRole().hasAdminRights() && u.isAvailable())
        .forEach(
            admin ->
                notifyChannels(
                    NotificationMessage.of(admin.getId(), incident.getId(), type, title, body)));
  }

  private void notifyChannels(NotificationMessage message) {
    NotificationDispatch.afterCommit(notificationChannels, message);
  }

  /**
   * Ticket visibility: admins and root see everything, technicians see tickets assigned to them,
   * and everyone sees the tickets they reported.
   */
  private boolean canView(Incident incident, User user) {
    if (user.getRole().hasAdminRights()
        || isTeamLead(user)
        || user.getId().equals(incident.getReporterId())) {
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
    if (isTeamLead(requester)) {
      return base; // the team lead sees every ticket in order to reassign them
    }
    return switch (requester.getRole()) {
      case ADMINISTRATOR, ROOT -> base;
        // A technician's worklist: tickets assigned to them, plus any they reported themselves.
      case TECHNICIAN -> base.withParticipant(requester.getId());
      case EMPLOYEE -> base.withReporter(requester.getId());
    };
  }

  private static boolean isTeamLead(User user) {
    return user.getRole() == Role.TECHNICIAN && user.isTeamLead();
  }

  private void requireAdminOrTeamLead(User actor, String action) {
    if (!actor.getRole().hasAdminRights() && !isTeamLead(actor)) {
      throw new UnauthorizedOperationException("Only an administrator or team lead can " + action);
    }
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
            ? new UserSummaryResponse(
                reporter.getId().getValue(), reporter.getFullName(), reporter.getEmail())
            : new UserSummaryResponse(incident.getReporterId().getValue(), "Unknown", "");

    UserSummaryResponse assigneeDto = null;
    if (incident.getAssigneeId() != null) {
      User assignee = userRepository.findById(incident.getAssigneeId()).orElse(null);
      if (assignee != null) {
        assigneeDto =
            new UserSummaryResponse(
                assignee.getId().getValue(), assignee.getFullName(), assignee.getEmail());
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
            : serviceRepository
                .findById(incident.getAffectedServiceId())
                .map(this::toServiceResponse)
                .orElse(null),
        incident.getRespondedAt(),
        incident.getEscalatedAt(),
        incident.getFeedbackRating(),
        incident.getFeedbackComment(),
        incident.getEscalationLevel(),
        incident.getParentIncidentId() == null
            ? null
            : incidentRepository
                .findById(incident.getParentIncidentId())
                .map(IncidentApplicationService::toLinked)
                .orElse(null),
        incidentRepository.findChildren(incident.getId()).stream()
            .map(IncidentApplicationService::toLinked)
            .toList(),
        incident.getResolvedAt(),
        incident.getClosedAt());
  }

  private static LinkedIncidentResponse toLinked(Incident incident) {
    return new LinkedIncidentResponse(
        incident.getId().getValue(),
        incident.getIncidentNumber(),
        incident.getTitle(),
        incident.getStatus());
  }

  private ServiceResponse toServiceResponse(ItServiceEntity s) {
    return new ServiceResponse(
        s.getId(), s.getName(), s.getDescription(), s.getCriticality(), s.isActive());
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
        incident.getAffectedServiceId() != null
            ? serviceNames.get(incident.getAffectedServiceId())
            : null,
        incident.getAssigneeId() != null ? incident.getAssigneeId().getValue() : null,
        incident.getResolvedAt(),
        false);
  }
}
