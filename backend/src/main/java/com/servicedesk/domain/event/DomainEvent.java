package com.servicedesk.domain.event;

import java.time.Instant;

/**
 * Root contract for all pure domain events.
 */
public interface DomainEvent {

  Instant occurredOn();
}
