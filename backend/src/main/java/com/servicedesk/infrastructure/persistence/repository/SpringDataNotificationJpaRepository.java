package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.NotificationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataNotificationJpaRepository
    extends JpaRepository<NotificationEntity, UUID> {

  List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
}
