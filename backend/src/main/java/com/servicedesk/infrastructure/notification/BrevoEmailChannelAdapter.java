package com.servicedesk.infrastructure.notification;

import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends notification emails through Brevo's transactional API ({@code POST /v3/smtp/email}). Active
 * only when {@code BREVO_API_KEY} is set; otherwise {@link EmailGatewayAdapter} just logs. Runs
 * asynchronously, and only after the business transaction has committed, so a slow mail API never
 * delays or rolls back the user's action.
 */
@Component
@ConditionalOnExpression("!'${app.mail.brevo.api-key:}'.isBlank()")
public class BrevoEmailChannelAdapter implements NotificationChannel {

  private static final Logger log = LoggerFactory.getLogger(BrevoEmailChannelAdapter.class);

  private final RestClient http;
  private final SpringDataUserJpaRepository users;
  private final String apiKey;
  private final String fromAddress;
  private final String fromName;
  private final String appBaseUrl;

  @Autowired
  public BrevoEmailChannelAdapter(
      RestClient.Builder builder,
      SpringDataUserJpaRepository users,
      @Value("${app.mail.brevo.api-key:}") String apiKey,
      @Value("${app.mail.brevo.base-url:https://api.brevo.com/v3}") String baseUrl,
      @Value("${app.mail.from-address:}") String fromAddress,
      @Value("${app.mail.from-name:ServiceDesk}") String fromName,
      @Value("${app.base-url:http://localhost:3000}") String appBaseUrl) {
    this(
        builder.baseUrl(baseUrl).requestFactory(timeouts()).build(),
        users,
        apiKey,
        fromAddress,
        fromName,
        appBaseUrl);
  }

  BrevoEmailChannelAdapter(
      RestClient http,
      SpringDataUserJpaRepository users,
      String apiKey,
      String fromAddress,
      String fromName,
      String appBaseUrl) {
    this.http = http;
    this.users = users;
    this.apiKey = apiKey.trim();
    this.fromAddress = fromAddress;
    this.fromName = fromName;
    this.appBaseUrl = appBaseUrl.replaceAll("/+$", "");
  }

  private static JdkClientHttpRequestFactory timeouts() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    return factory;
  }

  @Override
  @Async
  public void send(NotificationMessage message) {
    if (message == null || message.recipientId() == null) {
      return;
    }
    UserEntity recipient = users.findById(message.recipientId().getValue()).orElse(null);
    if (recipient == null
        || !recipient.isActive()
        || recipient.getEmail() == null
        || recipient.getEmail().isBlank()) {
      return; // deactivated, or no address on file
    }
    try {
      http.post()
          .uri("/smtp/email")
          .header("api-key", apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(payload(recipient, message))
          .retrieve()
          .toBodilessEntity();
    } catch (RuntimeException e) {
      // Never expose the key; the in-app notification still reached the user.
      log.warn("Brevo email to user {} failed: {}", recipient.getId(), e.getMessage());
    }
  }

  Map<String, Object> payload(UserEntity recipient, NotificationMessage message) {
    String link =
        message.incidentId() == null
            ? appBaseUrl + "/notifications"
            : appBaseUrl + "/incidents/" + message.incidentId().getValue();
    String title = HtmlUtils.htmlEscape(message.title());
    String body = HtmlUtils.htmlEscape(message.message()).replace("\n", "<br>");
    String html =
        "<div style=\"font-family:system-ui,sans-serif;max-width:560px;color:#1f2328\">"
            + "<p style=\"font-size:13px;color:#59636e;margin:0 0 8px\">ServiceDesk</p>"
            + "<h1 style=\"font-size:18px;margin:0 0 12px\">"
            + title
            + "</h1>"
            + "<p style=\"font-size:15px;line-height:1.5;margin:0 0 20px\">"
            + body
            + "</p>"
            + "<a href=\""
            + HtmlUtils.htmlEscape(link)
            + "\" style=\"display:inline-block;padding:9px 14px;background:#2155c4;color:#fff;"
            + "border-radius:6px;text-decoration:none\">Open in ServiceDesk</a>"
            + "</div>";
    return Map.of(
        "sender", Map.of("name", fromName, "email", fromAddress),
        "to", List.of(Map.of("email", recipient.getEmail(), "name", recipient.getFullName())),
        "subject", message.title(),
        "htmlContent", html,
        "textContent", message.title() + "\n\n" + message.message() + "\n\n" + link);
  }
}
