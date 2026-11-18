package com.servicedesk.domain.event;

/**
 * Observer interface listening for domain events.
 */
public interface DomainEventListener {

  void onEvent(DomainEvent event);
}
