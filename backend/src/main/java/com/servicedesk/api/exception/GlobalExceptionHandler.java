package com.servicedesk.api.exception;

import com.servicedesk.api.dto.ErrorResponse;
import com.servicedesk.domain.exception.DomainValidationException;
import com.servicedesk.domain.exception.IncidentNotFoundException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler mapping uncaught errors to standardized JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(InvalidStateTransitionException.class)
  public ResponseEntity<ErrorResponse> handleInvalidStateTransition(
      InvalidStateTransitionException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler({IncidentNotFoundException.class, UserNotFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(
      RuntimeException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler({UnauthorizedOperationException.class, AccessDeniedException.class})
  public ResponseEntity<ErrorResponse> handleUnauthorized(
      Exception ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
  }

  @ExceptionHandler({DomainValidationException.class, IllegalArgumentException.class})
  public ResponseEntity<ErrorResponse> handleBadRequest(
      Exception ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.warn("Validation error on path {}: {}", request.getRequestURI(), ex.getMessage());

    String detailedMessage =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .orElse("Validation failed");

    return buildResponse(HttpStatus.BAD_REQUEST, detailedMessage, request);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ErrorResponse> handleMalformedRequest(
      Exception ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body or parameter value", request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneralException(
      Exception ex, HttpServletRequest request) {
    log.error("Unhandled internal server exception on path {}", request.getRequestURI(), ex);

    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected internal error occurred. Please contact system support.",
        request);
  }

  private ResponseEntity<ErrorResponse> buildResponse(
      HttpStatus status, String message, HttpServletRequest request) {
    ErrorResponse response =
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI());
    return ResponseEntity.status(status).body(response);
  }
}
