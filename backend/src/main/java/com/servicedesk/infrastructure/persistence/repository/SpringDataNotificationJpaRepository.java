package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.infrastructure.persistence.entity.NotificationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataNotificationJpaRepository
    extends JpaRepository<NotificationEntity, UUID> {

  List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

  List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

  long countByRecipientIdAndReadFalse(UUID recipientId);

  Optional<NotificationEntity> findByIdAndRecipientId(UUID id, UUID recipientId);

  @Modifying
  @Query("update NotificationEntity n set n.read = true where n.recipient.id = :recipientId and n.read = false")
  int markAllRead(@Param("recipientId") UUID recipientId);
}
