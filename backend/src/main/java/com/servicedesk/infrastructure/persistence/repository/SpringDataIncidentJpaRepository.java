package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataIncidentJpaRepository
    extends JpaRepository<IncidentEntity, UUID>, JpaSpecificationExecutor<IncidentEntity> {

  Optional<IncidentEntity> findByIncidentNumber(String incidentNumber);

  int countByAssigneeIdAndStatusNotIn(UUID assigneeId, Collection<IncidentStatus> statuses);

  List<IncidentEntity> findByStatusIn(Collection<IncidentStatus> statuses);
}
