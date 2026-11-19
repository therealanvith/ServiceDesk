package com.servicedesk.application;

import com.servicedesk.api.dto.NotificationListResponse;
import com.servicedesk.api.dto.NotificationResponse;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.infrastructure.persistence.entity.NotificationEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataNotificationJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's in-app notification inbox. */
@Service
@Transactional
public class NotificationApplicationService {

  private static final int INBOX_SIZE = 50;

  private final SpringDataNotificationJpaRepository notifications;
  private final SpringDataUserJpaRepository users;

  public NotificationApplicationService(
      SpringDataNotificationJpaRepository notifications, SpringDataUserJpaRepository users) {
    this.notifications = notifications;
    this.users = users;
  }

  @Transactional(readOnly = true)
  public NotificationListResponse inbox(String username) {
    UUID userId = userId(username);
    return new NotificationListResponse(
        notifications.countByRecipientIdAndReadFalse(userId),
        notifications
            .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(0, INBOX_SIZE))
            .stream()
            .map(NotificationApplicationService::toResponse)
            .toList());
  }

  public void markRead(UUID notificationId, String username) {
    // Scoped to the recipient, so nobody can mark someone else's notifications.
    notifications
        .findByIdAndRecipientId(notificationId, userId(username))
        .ifPresent(n -> n.setRead(true));
  }

  public void markAllRead(String username) {
    notifications.markAllRead(userId(username));
  }

  private UUID userId(String username) {
    return users
        .findByUsername(username)
        .map(UserEntity::getId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
  }

  private static NotificationResponse toResponse(NotificationEntity n) {
    return new NotificationResponse(
        n.getId(),
        n.getType(),
        n.getTitle(),
        n.getMessage(),
        n.getIncident() != null ? n.getIncident().getId() : null,
        n.getIncident() != null ? n.getIncident().getIncidentNumber() : null,
        n.isRead(),
        n.getCreatedAt());
  }
}
