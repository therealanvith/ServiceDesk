package com.servicedesk.api;

import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.infrastructure.live.LiveEventBroadcaster;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live updates for the signed-in user as Server-Sent Events: {@code notification} when something
 * new arrives in their inbox and {@code incident-updated} when a ticket they follow changes.
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventsController {

  private final LiveEventBroadcaster broadcaster;
  private final SpringDataUserJpaRepository users;

  public EventsController(LiveEventBroadcaster broadcaster, SpringDataUserJpaRepository users) {
    this.broadcaster = broadcaster;
    this.users = users;
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(Principal principal) {
    var user =
        users
            .findByUsername(principal.getName())
            .orElseThrow(() -> new UserNotFoundException("User not found: " + principal.getName()));
    return broadcaster.subscribe(user.getId());
  }
}
