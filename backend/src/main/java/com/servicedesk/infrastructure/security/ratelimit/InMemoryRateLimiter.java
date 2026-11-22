package com.servicedesk.infrastructure.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token buckets held in memory, one per policy and key. Tokens refill continuously, so a client
 * that pauses gets its allowance back gradually rather than all at once at a window boundary.
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

  /** Buckets untouched for this long are full again and can be dropped. */
  private static final Duration IDLE_EXPIRY = Duration.ofMinutes(30);

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final Clock clock;

  public InMemoryRateLimiter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Decision tryConsume(String key, RateLimitPolicy policy) {
    Instant now = clock.instant();
    Bucket bucket =
        buckets.computeIfAbsent(policy.name() + ":" + key, k -> new Bucket(policy.capacity(), now));
    return bucket.tryConsume(policy, now);
  }

  @Scheduled(fixedRate = 300_000)
  void sweepIdleBuckets() {
    Instant cutoff = clock.instant().minus(IDLE_EXPIRY);
    buckets.values().removeIf(bucket -> bucket.lastSeen().isBefore(cutoff));
  }

  int bucketCount() {
    return buckets.size();
  }

  private static final class Bucket {
    private double tokens;
    private Instant lastRefill;
    private Instant lastSeen;

    Bucket(int capacity, Instant now) {
      this.tokens = capacity;
      this.lastRefill = now;
      this.lastSeen = now;
    }

    synchronized Decision tryConsume(RateLimitPolicy policy, Instant now) {
      double perSecond = policy.capacity() / (double) policy.period().toMillis() * 1000.0;
      double elapsed = Math.max(0, Duration.between(lastRefill, now).toMillis()) / 1000.0;
      tokens = Math.min(policy.capacity(), tokens + elapsed * perSecond);
      lastRefill = now;
      lastSeen = now;
      if (tokens >= 1) {
        tokens -= 1;
        return new Decision(true, policy.capacity(), (int) Math.floor(tokens), 0);
      }
      long wait = (long) Math.ceil((1 - tokens) / perSecond);
      return new Decision(false, policy.capacity(), 0, Math.max(1, wait));
    }

    synchronized Instant lastSeen() {
      return lastSeen;
    }
  }
}
