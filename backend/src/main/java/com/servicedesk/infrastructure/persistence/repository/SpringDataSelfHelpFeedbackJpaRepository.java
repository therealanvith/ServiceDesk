package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.SelfHelpFeedbackEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataSelfHelpFeedbackJpaRepository
    extends JpaRepository<SelfHelpFeedbackEntity, UUID> {

  List<SelfHelpFeedbackEntity> findByCreatedAtGreaterThanEqual(Instant since);
}
