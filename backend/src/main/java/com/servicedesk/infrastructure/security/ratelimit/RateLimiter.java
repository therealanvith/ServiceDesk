package com.servicedesk.infrastructure.security.ratelimit;

/**
 * Port for request throttling. The in-memory implementation suits a single instance; a shared store
 * such as Redis (or Bucket4j on top of it) can implement this for several instances.
 */
public interface RateLimiter {

  /** Takes one token from the bucket for {@code key} under {@code policy}. */
  Decision tryConsume(String key, RateLimitPolicy policy);

  /**
   * @param remaining whole tokens left after this request
   * @param retryAfterSeconds when refused, how long until a token is available (at least 1)
   */
  record Decision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {}
}
