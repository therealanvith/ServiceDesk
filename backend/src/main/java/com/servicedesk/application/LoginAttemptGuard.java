package com.servicedesk.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Locks a username after repeated wrong passwords, whichever addresses they come from, so spreading
 * a guessing attack across IPs does not get around the per-address limit. Unknown usernames are
 * tracked the same way, so a lockout reveals nothing about which accounts exist.
 */
@Component
public class LoginAttemptGuard {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

  private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
  private final Clock clock;
  private final int maxFailures;
  private final Duration window;
  private final Duration lockout;

  public LoginAttemptGuard(
      Clock clock,
      @Value("${app.rate-limit.login.max-failures:5}") int maxFailures,
      @Value("${app.rate-limit.login.window-minutes:15}") long windowMinutes,
      @Value("${app.rate-limit.login.lockout-minutes:15}") long lockoutMinutes) {
    this.clock = clock;
    this.maxFailures = maxFailures;
    this.window = Duration.ofMinutes(windowMinutes);
    this.lockout = Duration.ofMinutes(lockoutMinutes);
  }

  public boolean isLocked(String username) {
    Attempts entry = attempts.get(key(username));
    return entry != null && entry.lockedUntil(clock.instant()) != null;
  }

  public void recordFailure(String username) {
    Instant now = clock.instant();
    Attempts entry = attempts.computeIfAbsent(key(username), k -> new Attempts());
    if (entry.recordFailure(now, maxFailures, window, lockout)) {
      log.warn(
          "[SECURITY] Sign-in for '{}' locked for {} minutes after {} failed attempts",
          key(username),
          lockout.toMinutes(),
          maxFailures);
    }
  }

  public void recordSuccess(String username) {
    attempts.remove(key(username));
  }

  @Scheduled(fixedRate = 600_000)
  void forgetOldAttempts() {
    Instant cutoff = clock.instant().minus(window.plus(lockout));
    attempts.values().removeIf(entry -> entry.lastActivity().isBefore(cutoff));
  }

  private static String key(String username) {
    return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
  }

  private static final class Attempts {
    private final Deque<Instant> failures = new ArrayDeque<>();
    private Instant lockedUntil;
    private Instant lastActivity = Instant.EPOCH;

    /** Returns true when this failure starts a lockout. */
    synchronized boolean recordFailure(
        Instant now, int maxFailures, Duration window, Duration lockout) {
      lastActivity = now;
      if (lockedUntil(now) != null) {
        return false;
      }
      failures.addLast(now);
      while (!failures.isEmpty() && failures.peekFirst().isBefore(now.minus(window))) {
        failures.removeFirst();
      }
      if (failures.size() >= maxFailures) {
        lockedUntil = now.plus(lockout);
        failures.clear();
        return true;
      }
      return false;
    }

    synchronized Instant lockedUntil(Instant now) {
      if (lockedUntil != null && !now.isBefore(lockedUntil)) {
        lockedUntil = null;
      }
      return lockedUntil;
    }

    synchronized Instant lastActivity() {
      return lastActivity;
    }
  }
}
