package com.servicedesk.domain.exception;

/** The chosen technician already has as many open tickets as their limit allows. */
public class TechnicianWorkloadExceededException extends RuntimeException {

  public TechnicianWorkloadExceededException(String message) {
    super(message);
  }
}
