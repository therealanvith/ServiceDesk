package com.servicedesk.domain.exception;

/**
 * Thrown when an incident is not found by ID or ticket number.
 */
public class IncidentNotFoundException extends RuntimeException {

  public IncidentNotFoundException(String message) {
    super(message);
  }
}
