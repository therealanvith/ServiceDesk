package com.servicedesk.domain.exception;

/** Thrown when an illegal state transition is attempted on an incident. */
public class InvalidStateTransitionException extends RuntimeException {

  public InvalidStateTransitionException(String message) {
    super(message);
  }
}
