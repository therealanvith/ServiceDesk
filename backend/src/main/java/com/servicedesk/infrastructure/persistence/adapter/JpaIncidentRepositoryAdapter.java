package com.servicedesk.infrastructure.persistence.adapter;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.state.AssignedState;
import com.servicedesk.domain.incident.state.CancelledState;
import com.servicedesk.domain.incident.state.ClosedState;
import com.servicedesk.domain.incident.state.InProgressState;
import com.servicedesk.domain.incident.state.IncidentState;
import com.servicedesk.domain.incident.state.PendingInfoState;
import com.servicedesk.domain.incident.state.ReportedState;
import com.servicedesk.domain.incident.state.ResolvedState;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.sla.SlaWindow;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Hexagonal persistence adapter implementing the domain IncidentRepository port.
 */
@Component
public class JpaIncidentRepositoryAdapter implements IncidentRepository {

  private final SpringDataIncidentJpaRepository incidentJpaRepository;
  private final SpringDataUserJpaRepository userJpaRepository;
  private final Clock clock;

  public JpaIncidentRepositoryAdapter(
      SpringDataIncidentJpaRepository incidentJpaRepository,
      SpringDataUserJpaRepository userJpaRepository,
      Clock clock) {
    this.incidentJpaRepository = incidentJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.clock = clock;
  }

  @Override
  public Optional<Incident> findById(IncidentId id) {
    return incidentJpaRepository.findById(id.getValue()).map(this::toDomain);
  }

  @Override
  public Optional<Incident> findByIncidentNumber(String incidentNumber) {
    return incidentJpaRepository.findByIncidentNumber(incidentNumber).map(this::toDomain);
  }

  @Override
  public Incident save(Incident incident) {
    IncidentEntity entity = toEntity(incident);
    IncidentEntity saved = incidentJpaRepository.save(entity);
    return toDomain(saved);
  }

  @Override
  public List<Incident> findAll() {
    return incidentJpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public List<Incident> findWithFilters(IncidentFilter filter) {
    if (filter == null) {
      return findAll();
    }

    Specification<IncidentEntity> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (filter.status() != null) {
            predicates.add(cb.equal(root.get("status"), filter.status()));
          }
          if (filter.priority() != null) {
            predicates.add(cb.equal(root.get("priority"), filter.priority()));
          }
          if (filter.category() != null) {
            predicates.add(cb.equal(root.get("category"), filter.category()));
          }
          if (filter.assigneeId() != null) {
            predicates.add(cb.equal(root.get("assignee").get("id"), filter.assigneeId().getValue()));
          }
          if (filter.reporterId() != null) {
            predicates.add(cb.equal(root.get("reporter").get("id"), filter.reporterId().getValue()));
          }
          if (filter.slaStatus() != null) {
            predicates.add(cb.equal(root.get("slaStatus"), filter.slaStatus()));
          }
          if (filter.searchText() != null && !filter.searchText().isBlank()) {
            String pattern = "%" + filter.searchText().trim().toLowerCase() + "%";
            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
            Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
            Predicate numMatch = cb.like(cb.lower(root.get("incidentNumber")), pattern);
            predicates.add(cb.or(titleMatch, descMatch, numMatch));
          }

          query.orderBy(cb.desc(root.get("createdAt")));
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return incidentJpaRepository.findAll(spec).stream().map(this::toDomain).toList();
  }

  @Override
  public int countActiveByTechnicianId(UserId technicianId) {
    return incidentJpaRepository.countByAssigneeIdAndStatusNotIn(
        technicianId.getValue(), Set.of(IncidentStatus.CLOSED, IncidentStatus.CANCELLED));
  }

  private Incident toDomain(IncidentEntity entity) {
    IncidentState state =
        switch (entity.getStatus()) {
          case REPORTED -> new ReportedState();
          case ASSIGNED -> new AssignedState();
          case IN_PROGRESS -> new InProgressState();
          case PENDING_INFO -> new PendingInfoState();
          case RESOLVED -> new ResolvedState();
          case CLOSED -> new ClosedState();
          case CANCELLED -> new CancelledState();
        };

    SlaWindow slaWindow =
        new SlaWindow(
            entity.getResponseDeadline(),
            entity.getResolutionDeadline(),
            Duration.ofSeconds(entity.getPausedDurationSeconds()),
            entity.getPausedAt(),
            entity.getSlaStatus());

    Incident incident =
        new Incident(
            IncidentId.of(entity.getId()),
            entity.getIncidentNumber(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getPriority(),
            entity.getImpact(),
            entity.getUrgency(),
            UserId.of(entity.getReporter().getId()),
            entity.getAssignee() != null ? UserId.of(entity.getAssignee().getId()) : null,
            state,
            slaWindow,
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            clock);

    incident.setResolutionSummary(entity.getResolutionNotes());
    incident.setResolvedAt(entity.getResolvedAt());
    incident.setClosedAt(entity.getClosedAt());

    return incident;
  }

  private IncidentEntity toEntity(Incident incident) {
    IncidentEntity entity =
        incidentJpaRepository
            .findById(incident.getId().getValue())
            .orElseGet(IncidentEntity::new);

    entity.setId(incident.getId().getValue());
    entity.setIncidentNumber(incident.getIncidentNumber());
    entity.setTitle(incident.getTitle());
    entity.setDescription(incident.getDescription());
    entity.setCategory(incident.getCategory());
    entity.setStatus(incident.getStatus());
    entity.setPriority(incident.getPriority());
    entity.setImpact(incident.getImpact());
    entity.setUrgency(incident.getUrgency());

    UserEntity reporter =
        userJpaRepository
            .findById(incident.getReporterId().getValue())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Reporter entity not found: " + incident.getReporterId()));
    entity.setReporter(reporter);

    if (incident.getAssigneeId() != null) {
      UserEntity assignee =
          userJpaRepository
              .findById(incident.getAssigneeId().getValue())
              .orElse(null);
      entity.setAssignee(assignee);
    } else {
      entity.setAssignee(null);
    }

    if (incident.getSlaWindow() != null) {
      entity.setResponseDeadline(incident.getSlaWindow().getResponseDeadline());
      entity.setResolutionDeadline(incident.getSlaWindow().getResolutionDeadline());
      entity.setPausedAt(incident.getSlaWindow().getPausedAt());
      entity.setPausedDurationSeconds(incident.getSlaWindow().getPausedDuration().toSeconds());
      entity.setSlaStatus(incident.getSlaWindow().getStatus());
    }

    entity.setResolutionNotes(incident.getResolutionSummary());
    entity.setResolvedAt(incident.getResolvedAt());
    entity.setClosedAt(incident.getClosedAt());
    entity.setCreatedAt(incident.getCreatedAt());
    entity.setUpdatedAt(incident.getUpdatedAt());

    return entity;
  }
}
