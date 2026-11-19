package com.servicedesk.domain.sla;

import com.servicedesk.domain.priority.Priority;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Value object tracking Service Level Agreement response and resolution windows.
 */
public class SlaWindow {

  private final Instant responseDeadline;
  private Instant resolutionDeadline;
  private Duration pausedDuration;
  private Instant pausedAt;
  private SlaStatus status;

  public SlaWindow(
      Instant responseDeadline,
      Instant resolutionDeadline,
      Duration pausedDuration,
      Instant pausedAt,
      SlaStatus status) {
    this.responseDeadline = Objects.requireNonNull(responseDeadline, "Response deadline must not be null");
    this.resolutionDeadline = Objects.requireNonNull(resolutionDeadline, "Resolution deadline must not be null");
    this.pausedDuration = pausedDuration != null ? pausedDuration : Duration.ZERO;
    this.pausedAt = pausedAt;
    this.status = status != null ? status : SlaStatus.WITHIN_SLA;
  }

  public static SlaWindow calculateInitial(Priority priority, Instant startTime) {
    Objects.requireNonNull(priority, "Priority must not be null");
    Objects.requireNonNull(startTime, "Start time must not be null");

    Duration responseWindow =
        switch (priority) {
          case P1_CRITICAL -> Duration.ofMinutes(15);
          case P2_HIGH -> Duration.ofMinutes(30);
          case P3_MEDIUM -> Duration.ofHours(2);
          case P4_LOW -> Duration.ofHours(4);
        };

    Duration resolutionWindow =
        switch (priority) {
          case P1_CRITICAL -> Duration.ofHours(2);
          case P2_HIGH -> Duration.ofHours(4);
          case P3_MEDIUM -> Duration.ofHours(8);
          case P4_LOW -> Duration.ofHours(24);
        };

    return new SlaWindow(
        startTime.plus(responseWindow),
        startTime.plus(resolutionWindow),
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
    Objects.requireNonNull(now, "Current time must not be null");

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
      // Warning threshold is at 25% remaining time (75% elapsed) or under 30 minutes
      if (remaining.toMinutes() <= 30) {
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
