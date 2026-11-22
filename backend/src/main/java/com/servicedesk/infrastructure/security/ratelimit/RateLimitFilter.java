package com.servicedesk.infrastructure.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Throttles the API by named policy: sign-in by client address, the AI assistant and writes by
 * user, and everything else by user (or address when signed out). Runs after the JWT filter so the
 * signed-in user is known. Not a Spring bean, so it isn't also registered outside security.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

  private final RateLimiter limiter;
  private final RateLimitSettings settings;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public RateLimitFilter(
      RateLimiter limiter, RateLimitSettings settings, ObjectMapper objectMapper, Clock clock) {
    this.limiter = limiter;
    this.settings = settings;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = path(request);
    return !settings.enabled()
        || "OPTIONS".equals(request.getMethod())
        || path.equals("/api/v1/health")
        || path.startsWith("/actuator/health");
  }

  /**
   * The decoded path Spring routes on. The raw URI would let "/api/v1/auth/logi%6E" reach the login
   * endpoint while dodging the sign-in policy.
   */
  private static String path(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    RateLimitPolicy policy = settings.policyFor(request.getMethod(), path(request));
    String clientIp = clientIp(request);
    String user = signedInUser();
    String key = policy.name().equals("auth") || user == null ? "ip:" + clientIp : "user:" + user;

    RateLimiter.Decision decision = limiter.tryConsume(key, policy);
    response.setHeader("RateLimit-Limit", String.valueOf(decision.limit()));
    response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }

    log.warn("Rate limit '{}' hit by {} on {}", policy.name(), key, request.getRequestURI());
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            Instant.now(clock),
            HttpStatus.TOO_MANY_REQUESTS.value(),
            HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            "Too many requests. Try again in " + decision.retryAfterSeconds() + " seconds.",
            request.getRequestURI()));
  }

  /**
   * The connecting address, unless the app is configured to sit behind a trusted proxy; otherwise a
   * client could pick any address by sending its own X-Forwarded-For.
   */
  private String clientIp(HttpServletRequest request) {
    if (settings.trustForwardedFor()) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        // The rightmost hop was added by our proxy; anything left of it came from the client.
        String[] hops = forwarded.split(",");
        return hops[hops.length - 1].trim();
      }
    }
    return request.getRemoteAddr();
  }

  private static String signedInUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
        ? null
        : auth.getName();
  }
}
