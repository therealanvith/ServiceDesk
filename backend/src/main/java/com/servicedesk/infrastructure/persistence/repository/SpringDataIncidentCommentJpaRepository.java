package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.IncidentCommentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataIncidentCommentJpaRepository
    extends JpaRepository<IncidentCommentEntity, UUID> {

  List<IncidentCommentEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

  List<IncidentCommentEntity> findByIncidentIdAndInternalFalseOrderByCreatedAtAsc(UUID incidentId);

  long countByIncidentIdAndInternalFalse(UUID incidentId);
}
