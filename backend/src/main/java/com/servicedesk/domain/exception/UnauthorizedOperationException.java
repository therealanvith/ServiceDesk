package com.servicedesk.domain.exception;

/**
 * Thrown when an actor attempts an action prohibited by their role or ticket ownership.
 */
public class UnauthorizedOperationException extends RuntimeException {

  public UnauthorizedOperationException(String message) {
    super(message);
  }
}
