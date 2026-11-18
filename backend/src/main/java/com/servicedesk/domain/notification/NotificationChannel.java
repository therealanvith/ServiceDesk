package com.servicedesk.domain.notification;

/**
 * Adapter interface for multi-channel notification dispatch.
 */
public interface NotificationChannel {

  void send(NotificationMessage message);
}
