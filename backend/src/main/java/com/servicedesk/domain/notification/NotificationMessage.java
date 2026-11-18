package com.servicedesk.domain.notification;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain representation of a notification message dispatched to a user.
 */
public record NotificationMessage(
    UserId recipientId,
    IncidentId incidentId,
    String type,
    String title,
    String message,
    Instant createdAt) {

  public NotificationMessage {
    Objects.requireNonNull(recipientId, "Recipient ID must not be null");
    Objects.requireNonNull(type, "Notification type must not be null");
    Objects.requireNonNull(title, "Notification title must not be null");
    Objects.requireNonNull(message, "Notification message must not be null");
    Objects.requireNonNull(createdAt, "CreatedAt must not be null");
  }

  public static NotificationMessage of(
      UserId recipientId,
      IncidentId incidentId,
      String type,
      String title,
      String message) {
    return new NotificationMessage(recipientId, incidentId, type, title, message, Instant.now());
  }
}
