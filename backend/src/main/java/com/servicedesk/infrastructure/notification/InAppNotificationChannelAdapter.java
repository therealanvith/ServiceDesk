package com.servicedesk.infrastructure.notification;

import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.infrastructure.persistence.entity.IncidentEntity;
import com.servicedesk.infrastructure.persistence.entity.NotificationEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataNotificationJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing NotificationChannel that stores notifications in the database for in-app UI delivery.
 */
@Component
public class InAppNotificationChannelAdapter implements NotificationChannel {

  private final SpringDataNotificationJpaRepository notificationJpaRepository;
  private final SpringDataUserJpaRepository userJpaRepository;
  private final SpringDataIncidentJpaRepository incidentJpaRepository;

  public InAppNotificationChannelAdapter(
      SpringDataNotificationJpaRepository notificationJpaRepository,
      SpringDataUserJpaRepository userJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository) {
    this.notificationJpaRepository = notificationJpaRepository;
    this.userJpaRepository = userJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
  }

  @Override
  public void send(NotificationMessage message) {
    if (message == null || message.recipientId() == null) {
      return;
    }

    UserEntity recipient = userJpaRepository.findById(message.recipientId().getValue()).orElse(null);
    if (recipient == null) {
      return;
    }

    IncidentEntity incident = null;
    if (message.incidentId() != null) {
      incident = incidentJpaRepository.findById(message.incidentId().getValue()).orElse(null);
    }

    NotificationEntity entity =
        new NotificationEntity(
            UUID.randomUUID(),
            recipient,
            incident,
            message.type(),
            message.title(),
            message.message(),
            false,
            message.createdAt());

    notificationJpaRepository.save(entity);
  }
}
