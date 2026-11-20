package com.servicedesk.domain.exception;

/** Thrown when a user cannot be located by ID or username. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(String message) {
    super(message);
  }
}
