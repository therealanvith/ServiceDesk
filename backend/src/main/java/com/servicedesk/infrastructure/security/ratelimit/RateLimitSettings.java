package com.servicedesk.infrastructure.security.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rate-limit policies from {@code app.rate-limit.*} and which requests each one covers. */
@Component
public class RateLimitSettings {

  private final boolean enabled;
  private final boolean trustForwardedFor;
  private final RateLimitPolicy auth;
  private final RateLimitPolicy ai;
  private final RateLimitPolicy write;
  private final RateLimitPolicy api;
  private final RateLimitPolicy events;

  public RateLimitSettings(
      @Value("${app.rate-limit.enabled:true}") boolean enabled,
      @Value("${app.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
      @Value("${app.rate-limit.auth-per-minute:10}") int authPerMinute,
      @Value("${app.rate-limit.ai-per-minute:20}") int aiPerMinute,
      @Value("${app.rate-limit.write-per-minute:120}") int writePerMinute,
      @Value("${app.rate-limit.api-per-minute:600}") int apiPerMinute,
      @Value("${app.rate-limit.events-per-minute:30}") int eventsPerMinute) {
    this.enabled = enabled;
    this.trustForwardedFor = trustForwardedFor;
    this.auth = RateLimitPolicy.perMinute("auth", authPerMinute);
    this.ai = RateLimitPolicy.perMinute("ai", aiPerMinute);
    this.write = RateLimitPolicy.perMinute("write", writePerMinute);
    this.api = RateLimitPolicy.perMinute("api", apiPerMinute);
    this.events = RateLimitPolicy.perMinute("events", eventsPerMinute);
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean trustForwardedFor() {
    return trustForwardedFor;
  }

  public RateLimitPolicy policyFor(String method, String path) {
    boolean post = "POST".equals(method);
    if (post
        && (path.equals("/api/v1/auth/login")
            || path.equals("/api/v1/auth/google")
            || path.equals("/api/v1/auth/register"))) {
      return auth;
    }
    // Opening a live stream is cheap to ask for but holds a connection: limit how often.
    if (path.equals("/api/v1/events")) {
      return events;
    }
    if (isAssistant(path)) {
      return ai;
    }
    if (post || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
      return write;
    }
    return api;
  }

  /** The AI endpoints call a paid API, so they get the tightest per-user allowance. */
  private static boolean isAssistant(String path) {
    return path.equals("/api/v1/incidents/triage")
        || path.equals("/api/v1/incidents/self-help")
        || (path.startsWith("/api/v1/incidents/") && path.contains("/ai/"));
  }
}
