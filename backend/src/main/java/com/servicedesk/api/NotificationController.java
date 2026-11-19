package com.servicedesk.api;

import com.servicedesk.api.dto.NotificationListResponse;
import com.servicedesk.application.NotificationApplicationService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

  private final NotificationApplicationService notifications;

  public NotificationController(NotificationApplicationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public ResponseEntity<NotificationListResponse> inbox(Principal principal) {
    return ResponseEntity.ok(notifications.inbox(principal.getName()));
  }

  @PostMapping("/{id}/read")
  public ResponseEntity<Void> markRead(@PathVariable UUID id, Principal principal) {
    notifications.markRead(id, principal.getName());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/read-all")
  public ResponseEntity<Void> markAllRead(Principal principal) {
    notifications.markAllRead(principal.getName());
    return ResponseEntity.noContent().build();
  }
}
