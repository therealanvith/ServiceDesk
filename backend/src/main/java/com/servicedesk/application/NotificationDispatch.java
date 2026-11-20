package com.servicedesk.application;

import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends notifications once the surrounding transaction has committed.
 *
 * <p>Sending inside the business transaction is unsafe: a failing channel that touches the database
 * marks the whole transaction rollback-only, so the user's action would fail with an
 * UnexpectedRollbackException even though the failure was "ignored". Deferring also guarantees
 * nobody is notified about a change that was later rolled back.
 */
final class NotificationDispatch {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatch.class);

  private NotificationDispatch() {}

  static void afterCommit(List<NotificationChannel> channels, NotificationMessage message) {
    if (channels == null || channels.isEmpty() || message == null) {
      return;
    }
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sendNow(channels, message);
            }
          });
    } else {
      sendNow(channels, message);
    }
  }

  private static void sendNow(List<NotificationChannel> channels, NotificationMessage message) {
    for (NotificationChannel channel : channels) {
      try {
        channel.send(message);
      } catch (RuntimeException ex) {
        log.warn(
            "Notification channel {} failed for {}: {}",
            channel.getClass().getSimpleName(),
            message.type(),
            ex.getMessage());
      }
    }
  }
}
