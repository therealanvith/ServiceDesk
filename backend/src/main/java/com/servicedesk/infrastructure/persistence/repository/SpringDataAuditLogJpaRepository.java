package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.IncidentAuditLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAuditLogJpaRepository
    extends JpaRepository<IncidentAuditLogEntity, UUID> {

  List<IncidentAuditLogEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

  List<IncidentAuditLogEntity> findAllByOrderByCreatedAtDesc(
      org.springframework.data.domain.Pageable pageable);
}
