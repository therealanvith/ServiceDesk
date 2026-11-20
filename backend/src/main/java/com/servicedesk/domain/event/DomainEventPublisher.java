package com.servicedesk.domain.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory domain event publisher supporting the Observer pattern. */
public class DomainEventPublisher {

  private final List<DomainEventListener> listeners = new CopyOnWriteArrayList<>();

  public void subscribe(DomainEventListener listener) {
    if (listener != null) {
      listeners.add(listener);
    }
  }

  public void unsubscribe(DomainEventListener listener) {
    if (listener != null) {
      listeners.remove(listener);
    }
  }

  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "Domain event must not be null");
    for (DomainEventListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (Exception ignored) {
        // Observers should not disrupt the primary domain execution flow
      }
    }
  }

  public void clearListeners() {
    listeners.clear();
  }
}
