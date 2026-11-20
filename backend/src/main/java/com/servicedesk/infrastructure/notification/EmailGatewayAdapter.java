package com.servicedesk.infrastructure.notification;

import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Log-only email channel used when Brevo is not configured: it records what would have been sent so
 * demos and development need no mail account.
 */
@Component
@ConditionalOnExpression("'${app.mail.brevo.api-key:}'.isBlank()")
public class EmailGatewayAdapter implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(EmailGatewayAdapter.class);

  private final SpringDataUserJpaRepository userJpaRepository;

  public EmailGatewayAdapter(SpringDataUserJpaRepository userJpaRepository) {
    this.userJpaRepository = userJpaRepository;
  }

  @Override
  public void send(NotificationMessage message) {
    if (message == null || message.recipientId() == null) {
      return;
    }

    String recipientEmail =
        userJpaRepository
            .findById(message.recipientId().getValue())
            .map(UserEntity::getEmail)
            .orElse(null);
    if (recipientEmail == null || recipientEmail.isBlank()) {
      return; // no address on file, nothing to send
    }

    log.info(
        "[EMAIL GATEWAY] To: {} | Subject: [{}] {} | Body: {}",
        recipientEmail,
        message.type(),
        message.title(),
        message.message());
  }
}
