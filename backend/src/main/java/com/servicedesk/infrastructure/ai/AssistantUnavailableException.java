package com.servicedesk.infrastructure.ai;

/** The language model could not give a usable answer; callers fall back to the rules. */
public class AssistantUnavailableException extends RuntimeException {

  public AssistantUnavailableException(String message) {
    super(message);
  }

  public AssistantUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
