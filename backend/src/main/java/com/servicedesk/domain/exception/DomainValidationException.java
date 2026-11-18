package com.servicedesk.domain.exception;

/**
 * Thrown when business invariants or validation rules are violated.
 */
public class DomainValidationException extends RuntimeException {

  public DomainValidationException(String message) {
    super(message);
  }
}
