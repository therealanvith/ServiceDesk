package com.servicedesk.domain.sla;

import com.servicedesk.domain.priority.Priority;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Value object tracking Service Level Agreement response and resolution windows. */
public class SlaWindow {

  private final Instant responseDeadline;
  private Instant resolutionDeadline;
  private Duration pausedDuration;
  private Instant pausedAt;
  private SlaStatus status;

  private static final Duration DEFAULT_WARNING_WINDOW = Duration.ofMinutes(30);

  public SlaWindow(
      Instant responseDeadline,
      Instant resolutionDeadline,
      Duration pausedDuration,
      Instant pausedAt,
      SlaStatus status) {
    this.responseDeadline =
        Objects.requireNonNull(responseDeadline, "Response deadline must not be null");
    this.resolutionDeadline =
        Objects.requireNonNull(resolutionDeadline, "Resolution deadline must not be null");
    this.pausedDuration = pausedDuration != null ? pausedDuration : Duration.ZERO;
    this.pausedAt = pausedAt;
    this.status = status != null ? status : SlaStatus.WITHIN_SLA;
  }

  public static SlaWindow calculateInitial(Priority priority, Instant startTime) {
    Objects.requireNonNull(priority, "Priority must not be null");
    return calculateInitial(SlaPolicy.defaultFor(priority), startTime);
  }

  public static SlaWindow calculateInitial(SlaPolicy policy, Instant startTime) {
    Objects.requireNonNull(policy, "SLA policy must not be null");
    Objects.requireNonNull(startTime, "Start time must not be null");
    return new SlaWindow(
        startTime.plus(policy.responseTarget()),
        startTime.plus(policy.resolutionTarget()),
        Duration.ZERO,
        null,
        SlaStatus.WITHIN_SLA);
  }

  public void pause(Instant now) {
    Objects.requireNonNull(now, "Current time must not be null");
    if (this.pausedAt == null) {
      this.pausedAt = now;
    }
  }

  public void resume(Instant now) {
    Objects.requireNonNull(now, "Current time must not be null");
    if (this.pausedAt != null) {
      Duration elapsedInPause = Duration.between(this.pausedAt, now);
      if (!elapsedInPause.isNegative()) {
        this.pausedDuration = this.pausedDuration.plus(elapsedInPause);
        this.resolutionDeadline = this.resolutionDeadline.plus(elapsedInPause);
      }
      this.pausedAt = null;
    }
  }

  public SlaStatus checkBreach(Instant now) {
    return checkBreach(now, DEFAULT_WARNING_WINDOW);
  }

  /**
   * Re-evaluates the status. {@code warningWindow} is how long before the deadline the ticket is
   * flagged as at risk; callers scale it to the SLA so short targets still get a usable warning.
   */
  public SlaStatus checkBreach(Instant now, Duration warningWindow) {
    Objects.requireNonNull(now, "Current time must not be null");
    Duration warning = warningWindow != null ? warningWindow : DEFAULT_WARNING_WINDOW;

    // If currently paused, compute effective current deadline
    Instant effectiveDeadline = this.resolutionDeadline;
    if (this.pausedAt != null) {
      Duration currentlyPaused = Duration.between(this.pausedAt, now);
      if (!currentlyPaused.isNegative()) {
        effectiveDeadline = effectiveDeadline.plus(currentlyPaused);
      }
    }

    if (now.isAfter(effectiveDeadline)) {
      this.status = SlaStatus.BREACHED;
    } else {
      Duration remaining = Duration.between(now, effectiveDeadline);
      if (remaining.compareTo(warning) <= 0) {
        this.status = SlaStatus.WARNING_BREACH_IMMINENT;
      } else {
        this.status = SlaStatus.WITHIN_SLA;
      }
    }
    return this.status;
  }

  public Instant getResponseDeadline() {
    return responseDeadline;
  }

  public Instant getResolutionDeadline() {
    return resolutionDeadline;
  }

  public Duration getPausedDuration() {
    return pausedDuration;
  }

  public Instant getPausedAt() {
    return pausedAt;
  }

  public SlaStatus getStatus() {
    return status;
  }

  public boolean isPaused() {
    return pausedAt != null;
  }
}
