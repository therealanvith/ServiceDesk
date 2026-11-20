package com.servicedesk.application;

import com.servicedesk.api.dto.MajorIncidentDtos.ServiceHealth;
import com.servicedesk.api.dto.MajorIncidentDtos.ServiceStatusResponse;
import com.servicedesk.api.dto.PriorityPreviewResponse;
import com.servicedesk.api.dto.ServiceRequest;
import com.servicedesk.api.dto.ServiceResponse;
import com.servicedesk.api.dto.SlaPolicyResponse;
import com.servicedesk.api.dto.UpdateSlaPolicyRequest;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.repository.IncidentRepository;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaPolicyProvider;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.SlaPolicyEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSlaPolicyJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-managed configuration: the IT service catalogue and SLA targets. */
@Service
@Transactional
public class CatalogApplicationService {

  private final SpringDataItServiceJpaRepository services;
  private final SpringDataSlaPolicyJpaRepository slaPolicies;
  private final SlaPolicyProvider slaPolicyProvider;
  private final Clock clock;
  private final PriorityCalculationStrategy priorityStrategy;

  private final IncidentRepository incidents;

  /**
   * Health of each active service from its open tickets: an open major incident (a ticket with
   * linked tickets) or two open P1s mean an outage; any open P1/P2, or two or more open tickets,
   * mean degraded; otherwise operational.
   */
  @Transactional(readOnly = true)
  public List<ServiceStatusResponse> serviceStatus() {
    List<Incident> open =
        incidents.findAll().stream()
            .filter(i -> i.getStatus().isActive() && i.getStatus() != IncidentStatus.RESOLVED)
            .toList();
    Set<IncidentId> majors =
        open.stream()
            .map(Incident::getParentIncidentId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
    return services.findAll().stream()
        .filter(ItServiceEntity::isActive)
        .sorted(java.util.Comparator.comparing(ItServiceEntity::getName))
        .map(
            service -> {
              List<Incident> onService =
                  open.stream()
                      .filter(i -> service.getId().equals(i.getAffectedServiceId()))
                      .toList();
              long major = onService.stream().filter(i -> majors.contains(i.getId())).count();
              long p1 =
                  onService.stream().filter(i -> i.getPriority() == Priority.P1_CRITICAL).count();
              boolean serious =
                  onService.stream()
                      .anyMatch(
                          i ->
                              i.getPriority() == Priority.P1_CRITICAL
                                  || i.getPriority() == Priority.P2_HIGH);
              ServiceHealth health =
                  major > 0 || p1 >= 2
                      ? ServiceHealth.OUTAGE
                      : serious || onService.size() >= 2
                          ? ServiceHealth.DEGRADED
                          : ServiceHealth.OPERATIONAL;
              return new ServiceStatusResponse(
                  service.getId(),
                  service.getName(),
                  service.getCriticality(),
                  health,
                  onService.size(),
                  major);
            })
        .toList();
  }

  public CatalogApplicationService(
      SpringDataItServiceJpaRepository services,
      SpringDataSlaPolicyJpaRepository slaPolicies,
      SlaPolicyProvider slaPolicyProvider,
      Clock clock,
      PriorityCalculationStrategy priorityStrategy,
      IncidentRepository incidents) {
    this.incidents = incidents;
    this.priorityStrategy = priorityStrategy;
    this.services = services;
    this.slaPolicies = slaPolicies;
    this.slaPolicyProvider = slaPolicyProvider;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ServiceResponse> listServices(boolean includeRetired) {
    return services.findAllByOrderByNameAsc().stream()
        .filter(s -> includeRetired || s.isActive())
        .map(CatalogApplicationService::toResponse)
        .toList();
  }

  public ServiceResponse createService(ServiceRequest request) {
    String name = request.name().trim();
    if (services.existsByNameIgnoreCase(name)) {
      throw new IllegalArgumentException("A service with that name already exists");
    }
    ItServiceEntity service =
        new ItServiceEntity(
            UUID.randomUUID(),
            name,
            blankToNull(request.description()),
            request.criticality(),
            clock.instant());
    if (request.active() != null) {
      service.setActive(request.active());
    }
    return toResponse(services.save(service));
  }

  public ServiceResponse updateService(UUID id, ServiceRequest request) {
    ItServiceEntity service =
        services.findById(id).orElseThrow(() -> new IllegalArgumentException("Service not found"));
    String name = request.name().trim();
    if (services.existsByNameIgnoreCaseAndIdNot(name, id)) {
      throw new IllegalArgumentException("A service with that name already exists");
    }
    service.setName(name);
    service.setDescription(blankToNull(request.description()));
    service.setCriticality(request.criticality());
    if (request.active() != null) {
      // Retiring keeps history intact; the service just stops appearing on the report form.
      service.setActive(request.active());
    }
    return toResponse(services.save(service));
  }

  /**
   * The priority the server would assign, so the report form never re-implements the policy. An
   * unknown or retired service counts as medium criticality, exactly as on creation.
   */
  @Transactional(readOnly = true)
  public PriorityPreviewResponse previewPriority(Impact impact, Urgency urgency, UUID serviceId) {
    BusinessCriticality criticality =
        serviceId == null
            ? BusinessCriticality.MEDIUM
            : services
                .findById(serviceId)
                .filter(ItServiceEntity::isActive)
                .map(ItServiceEntity::getCriticality)
                .orElse(BusinessCriticality.MEDIUM);
    Map<Impact, Map<Urgency, Priority>> matrix = new EnumMap<>(Impact.class);
    for (Impact i : Impact.values()) {
      Map<Urgency, Priority> row = new EnumMap<>(Urgency.class);
      for (Urgency u : Urgency.values()) {
        row.put(u, priorityStrategy.calculatePriority(i, u, criticality));
      }
      matrix.put(i, row);
    }
    return new PriorityPreviewResponse(
        priorityStrategy.calculatePriority(impact, urgency, criticality), criticality, matrix);
  }

  @Transactional(readOnly = true)
  public List<SlaPolicyResponse> listSlaPolicies() {
    return Arrays.stream(Priority.values())
        .map(slaPolicyProvider::policyFor)
        .map(CatalogApplicationService::toResponse)
        .toList();
  }

  /** New targets apply to tickets created from now on; existing deadlines are left as promised. */
  public SlaPolicyResponse updateSlaPolicy(Priority priority, UpdateSlaPolicyRequest request) {
    SlaPolicy policy =
        new SlaPolicy(
            priority,
            Duration.ofMinutes(request.responseMinutes()),
            Duration.ofMinutes(request.resolutionMinutes()));
    SlaPolicyEntity entity =
        slaPolicies
            .findById(priority)
            .orElseGet(() -> new SlaPolicyEntity(priority, 1, 1, clock.instant()));
    entity.update(request.responseMinutes(), request.resolutionMinutes(), clock.instant());
    slaPolicies.save(entity);
    return toResponse(policy);
  }

  private static ServiceResponse toResponse(ItServiceEntity s) {
    return new ServiceResponse(
        s.getId(), s.getName(), s.getDescription(), s.getCriticality(), s.isActive());
  }

  private static SlaPolicyResponse toResponse(SlaPolicy p) {
    return new SlaPolicyResponse(
        p.priority(), p.responseTarget().toMinutes(), p.resolutionTarget().toMinutes());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
