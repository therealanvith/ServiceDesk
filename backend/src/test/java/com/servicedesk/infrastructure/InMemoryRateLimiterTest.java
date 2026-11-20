package com.servicedesk.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.infrastructure.security.ratelimit.InMemoryRateLimiter;
import com.servicedesk.infrastructure.security.ratelimit.RateLimitPolicy;
import com.servicedesk.infrastructure.security.ratelimit.RateLimiter.Decision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

  /** A clock the test moves by hand. */
  private static final class ManualClock extends Clock {
    private Instant now = Instant.parse("2026-11-20T10:00:00Z");

    void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final ManualClock clock = new ManualClock();
  private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);
  private final RateLimitPolicy sixPerMinute = RateLimitPolicy.perMinute("test", 6);

  @Test
  void allowsABurstUpToCapacityThenRefuses() {
    for (int i = 5; i >= 0; i--) {
      Decision decision = limiter.tryConsume("alice", sixPerMinute);
      assertThat(decision.allowed()).isTrue();
      assertThat(decision.remaining()).isEqualTo(i);
    }
    Decision refused = limiter.tryConsume("alice", sixPerMinute);
    assertThat(refused.allowed()).isFalse();
    // One token every 10 seconds.
    assertThat(refused.retryAfterSeconds()).isEqualTo(10);
  }

  @Test
  void refillsGraduallyAndNeverAboveCapacity() {
    for (int i = 0; i < 6; i++) {
      limiter.tryConsume("alice", sixPerMinute);
    }
    clock.advance(Duration.ofSeconds(10));
    assertThat(limiter.tryConsume("alice", sixPerMinute).allowed()).isTrue();
    assertThat(limiter.tryConsume("alice", sixPerMinute).allowed()).isFalse();

    clock.advance(Duration.ofHours(1));
    assertThat(limiter.tryConsume("alice", sixPerMinute).remaining()).isEqualTo(5);
  }

  @Test
  void keysAndPoliciesHaveSeparateBuckets() {
    for (int i = 0; i < 6; i++) {
      limiter.tryConsume("alice", sixPerMinute);
    }
    assertThat(limiter.tryConsume("bob", sixPerMinute).allowed()).isTrue();
    assertThat(limiter.tryConsume("alice", RateLimitPolicy.perMinute("other", 6)).allowed())
        .isTrue();
  }
}
