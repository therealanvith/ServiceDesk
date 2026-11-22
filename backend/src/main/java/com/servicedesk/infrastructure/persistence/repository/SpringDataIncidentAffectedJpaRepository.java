package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.IncidentAffectedEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataIncidentAffectedJpaRepository
    extends JpaRepository<IncidentAffectedEntity, IncidentAffectedEntity.Key> {

  long countByIncidentId(UUID incidentId);

  boolean existsByIncidentIdAndUserId(UUID incidentId, UUID userId);

  List<IncidentAffectedEntity> findByIncidentId(UUID incidentId);

  List<IncidentAffectedEntity> findByUserId(UUID userId);

  List<IncidentAffectedEntity> findByIncidentIdIn(Collection<UUID> incidentIds);
}
