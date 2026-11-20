package com.servicedesk.infrastructure.security.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * A named token bucket: up to {@code capacity} requests, refilled evenly over {@code period}.
 *
 * @param name used in bucket keys and logs, e.g. {@code auth}
 */
public record RateLimitPolicy(String name, int capacity, Duration period) {

  public RateLimitPolicy {
    Objects.requireNonNull(name, "Policy name must not be null");
    Objects.requireNonNull(period, "Policy period must not be null");
    if (capacity < 1 || period.isZero() || period.isNegative()) {
      throw new IllegalArgumentException("A rate limit needs a positive capacity and period");
    }
  }

  public static RateLimitPolicy perMinute(String name, int capacity) {
    return new RateLimitPolicy(name, capacity, Duration.ofMinutes(1));
  }
}
