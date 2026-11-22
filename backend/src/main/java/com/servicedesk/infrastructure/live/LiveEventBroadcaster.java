package com.servicedesk.infrastructure.live;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes live events to signed-in browsers over Server-Sent Events (Observer pattern: each open
 * stream is a subscriber for its user). A user may have several tabs open; dead streams are dropped
 * as soon as a write to them fails.
 */
@Component
public class LiveEventBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(LiveEventBroadcaster.class);

  /** Streams end after this long; the browser reconnects, which also refreshes its token check. */
  private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;

  /** Browser tabs one person may keep streaming at once. */
  static final int MAX_STREAMS_PER_USER = 5;

  private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID userId) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    List<SseEmitter> evicted = new ArrayList<>();
    // Add inside compute so a concurrent remove can't drop the map entry under us; keep at most
    // MAX_STREAMS_PER_USER open per person, closing the oldest.
    streams.compute(
        userId,
        (id, set) -> {
          Set<SseEmitter> next = set != null ? set : new CopyOnWriteArraySet<>();
          next.add(emitter);
          Iterator<SseEmitter> oldest = next.iterator();
          while (next.size() > MAX_STREAMS_PER_USER && oldest.hasNext()) {
            SseEmitter old = oldest.next();
            if (old != emitter) {
              next.remove(old);
              evicted.add(old);
            }
          }
          return next;
        });
    evicted.forEach(SseEmitter::complete);
    Runnable remove = () -> remove(userId, emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(e -> remove.run());
    try {
      emitter.send(SseEmitter.event().name("ready").data("{}"));
    } catch (IOException e) {
      remove.run();
    }
    return emitter;
  }

  /** Sends one named event with a JSON-serialisable payload to every open stream of a user. */
  public void send(UUID userId, String event, Object data) {
    Set<SseEmitter> userStreams = streams.get(userId);
    if (userStreams == null) {
      return;
    }
    for (SseEmitter emitter : userStreams) {
      try {
        emitter.send(SseEmitter.event().name(event).data(data));
      } catch (IOException | IllegalStateException e) {
        remove(userId, emitter);
      }
    }
  }

  /** Comment lines keep proxies from closing idle streams and reveal dead connections. */
  @Scheduled(fixedRate = 25_000)
  void heartbeat() {
    streams.forEach(
        (userId, userStreams) -> {
          for (SseEmitter emitter : userStreams) {
            try {
              emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
              remove(userId, emitter);
            }
          }
        });
  }

  /** Ends every open stream of a user, e.g. when the account is deactivated. */
  public void disconnect(UUID userId) {
    Set<SseEmitter> userStreams = streams.remove(userId);
    if (userStreams != null) {
      userStreams.forEach(SseEmitter::complete);
    }
  }

  public int openStreams() {
    return streams.values().stream().mapToInt(Set::size).sum();
  }

  private void remove(UUID userId, SseEmitter emitter) {
    streams.computeIfPresent(
        userId,
        (id, set) -> {
          set.remove(emitter);
          return set.isEmpty() ? null : set;
        });
    log.debug("Live stream closed for user {}", userId);
  }
}
