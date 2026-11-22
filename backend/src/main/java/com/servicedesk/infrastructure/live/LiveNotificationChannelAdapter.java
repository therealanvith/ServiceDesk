package com.servicedesk.infrastructure.live;

import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Delivers each notification to the recipient's open browser tabs as it happens. */
@Component
public class LiveNotificationChannelAdapter implements NotificationChannel {

  private final LiveEventBroadcaster broadcaster;

  public LiveNotificationChannelAdapter(LiveEventBroadcaster broadcaster) {
    this.broadcaster = broadcaster;
  }

  @Override
  public void send(NotificationMessage message) {
    if (message == null || message.recipientId() == null) {
      return;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", message.type());
    data.put("title", message.title());
    data.put("message", message.message());
    data.put("incidentId", message.incidentId() == null ? null : message.incidentId().getValue());
    data.put("createdAt", message.createdAt().toString());
    broadcaster.send(message.recipientId().getValue(), "notification", data);
  }
}
