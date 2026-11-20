package com.servicedesk.application;

import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.PublicIssueResponse;
import com.servicedesk.api.dto.PublicIssueResponse.PublicUpdate;
import com.servicedesk.domain.exception.IncidentNotFoundException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.IncidentCommentEntity;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentCommentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only "All issues" list every signed-in user can browse, so people can check whether a
 * problem is already known and follow its progress. It hides who reported an issue and never
 * includes internal notes or the audit history; acting on a ticket still goes through the scoped
 * incident endpoints.
 */
@Service
@Transactional(readOnly = true)
public class IssueBoardService {

  private static final int MAX_PAGE_SIZE = 100;

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final SpringDataIncidentCommentJpaRepository comments;
  private final SpringDataItServiceJpaRepository services;

  public IssueBoardService(
      IncidentRepository incidents,
      UserRepository users,
      SpringDataIncidentCommentJpaRepository comments,
      SpringDataItServiceJpaRepository services) {
    this.incidents = incidents;
    this.users = users;
    this.comments = comments;
    this.services = services;
  }

  public PagedResponse<PublicIssueResponse> list(IncidentFilter filter, int page, int size) {
    // Reporter and assignee criteria are not part of the public view.
    IncidentFilter publicFilter =
        new IncidentFilter(
            filter.status(),
            filter.priority(),
            filter.category(),
            null,
            null,
            filter.slaStatus(),
            filter.searchText(),
            filter.serviceId(),
            filter.createdFrom(),
            filter.createdTo());
    PagedResponse<Incident> pageOf =
        PagedResponse.of(
            incidents.findWithFilters(publicFilter),
            page,
            Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    Map<UUID, String> serviceNames = serviceNames();
    Map<UserId, String> names = new HashMap<>();
    List<PublicIssueResponse> content =
        pageOf.content().stream()
            .map(
                inc ->
                    toResponse(
                        inc,
                        serviceNames,
                        names,
                        comments.countByIncidentIdAndInternalFalse(inc.getId().getValue()),
                        List.of()))
            .toList();
    return new PagedResponse<>(
        content, pageOf.page(), pageOf.size(), pageOf.totalElements(), pageOf.totalPages());
  }

  public PublicIssueResponse get(UUID id) {
    Incident incident =
        incidents
            .findById(IncidentId.of(id))
            .orElseThrow(() -> new IncidentNotFoundException("Incident not found: " + id));
    List<IncidentCommentEntity> publicEntries =
        comments.findByIncidentIdAndInternalFalseOrderByCreatedAtAsc(id);
    UUID reporterId = incident.getReporterId().getValue();
    List<PublicUpdate> updates =
        publicEntries.stream()
            .map(
                c -> {
                  boolean byReporter = c.getAuthor().getId().equals(reporterId);
                  return new PublicUpdate(
                      c.getId(),
                      c.getKind(),
                      byReporter ? "Reporter" : c.getAuthor().getFullName(),
                      byReporter ? null : c.getAuthor().getRole().name(),
                      c.getCommentText(),
                      c.getCreatedAt());
                })
            .toList();
    return toResponse(incident, serviceNames(), new HashMap<>(), updates.size(), updates);
  }

  private PublicIssueResponse toResponse(
      Incident incident,
      Map<UUID, String> serviceNames,
      Map<UserId, String> names,
      long updateCount,
      List<PublicUpdate> updates) {
    String handledBy =
        incident.getAssigneeId() == null
            ? null
            : names.computeIfAbsent(
                incident.getAssigneeId(),
                id -> users.findById(id).map(User::getFullName).orElse(null));
    return new PublicIssueResponse(
        incident.getId().getValue(),
        incident.getIncidentNumber(),
        incident.getTitle(),
        incident.getDescription(),
        incident.getStatus(),
        incident.getPriority(),
        incident.getCategory(),
        incident.getAffectedServiceId() == null
            ? null
            : serviceNames.get(incident.getAffectedServiceId()),
        handledBy,
        incident.getCreatedAt(),
        incident.getUpdatedAt(),
        updateCount,
        updates);
  }

  private Map<UUID, String> serviceNames() {
    Map<UUID, String> map = new HashMap<>();
    for (ItServiceEntity s : services.findAll()) {
      map.put(s.getId(), s.getName());
    }
    return map;
  }
}
