package com.servicedesk.infrastructure.persistence.adapter;

import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.repository.AuditLogRepository;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.IncidentAuditLogEntity;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataAuditLogJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Hexagonal persistence adapter implementing the domain AuditLogRepository port.
 */
@Component
public class JpaAuditLogRepositoryAdapter implements AuditLogRepository {

  private final SpringDataAuditLogJpaRepository auditLogJpaRepository;
  private final SpringDataIncidentJpaRepository incidentJpaRepository;
  private final SpringDataUserJpaRepository userJpaRepository;

  public JpaAuditLogRepositoryAdapter(
      SpringDataAuditLogJpaRepository auditLogJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      SpringDataUserJpaRepository userJpaRepository) {
    this.auditLogJpaRepository = auditLogJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.userJpaRepository = userJpaRepository;
  }

  @Override
  public AuditLog save(AuditLog auditLog) {
    IncidentEntity incident =
        incidentJpaRepository
            .findById(auditLog.incidentId().getValue())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Incident not found for audit log: " + auditLog.incidentId()));

    UserEntity actor = null;
    if (auditLog.actorId() != null) {
      actor = userJpaRepository.findById(auditLog.actorId().getValue()).orElse(null);
    }

    IncidentAuditLogEntity entity =
        new IncidentAuditLogEntity(
            auditLog.id(),
            incident,
            actor,
            auditLog.action(),
            auditLog.oldValue(),
            auditLog.newValue(),
            auditLog.remarks(),
            auditLog.timestamp());

    IncidentAuditLogEntity saved = auditLogJpaRepository.save(entity);
    return toDomain(saved);
  }

  @Override
  public List<AuditLog> findByIncidentId(IncidentId incidentId) {
    return auditLogJpaRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId.getValue()).stream()
        .map(this::toDomain)
        .toList();
  }

  private AuditLog toDomain(IncidentAuditLogEntity entity) {
    return new AuditLog(
        entity.getId(),
        IncidentId.of(entity.getIncident().getId()),
        entity.getActor() != null ? UserId.of(entity.getActor().getId()) : null,
        entity.getAction(),
        entity.getOldValue(),
        entity.getNewValue(),
        entity.getCreatedAt(),
        entity.getRemarks());
  }
}
