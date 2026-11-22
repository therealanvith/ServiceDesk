package com.servicedesk.infrastructure.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifies Google Identity Services ID tokens: RS256 signature against Google's published keys,
 * issuer, audience (our OAuth client id), expiry, verified email and, optionally, the Workspace
 * domain.
 */
@Component
public class GoogleIdTokenVerifier {

  /** Identity extracted from a verified Google ID token. */
  /** {@code picture} is Google's profile picture URL, or null when absent or not Google-hosted. */
  public record GoogleIdentity(String subject, String email, String name, String picture) {

    public GoogleIdentity(String subject, String email, String name) {
      this(subject, email, name, null);
    }
  }

  private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
  private static final Set<String> ISSUERS =
      Set.of("https://accounts.google.com", "accounts.google.com");

  private final String clientId;
  private final List<String> allowedDomains;
  private volatile JwtDecoder decoder;

  public GoogleIdTokenVerifier(
      @Value("${app.auth.google.client-id:}") String clientId,
      @Value("${app.auth.google.allowed-domains:}") String allowedDomains) {
    this.clientId = clientId == null ? "" : clientId.trim();
    this.allowedDomains =
        Arrays.stream(allowedDomains == null ? new String[0] : allowedDomains.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(d -> !d.isEmpty())
            .toList();
  }

  public boolean isEnabled() {
    return !clientId.isEmpty();
  }

  public String getClientId() {
    return isEnabled() ? clientId : null;
  }

  public GoogleIdentity verify(String idToken) {
    if (!isEnabled()) {
      throw new IllegalStateException("Google sign-in is not configured");
    }
    Jwt jwt;
    try {
      jwt = decoder().decode(idToken);
    } catch (JwtException ex) {
      throw new BadCredentialsException("Google sign-in failed. Please try again.");
    }

    String email = jwt.getClaimAsString("email");
    if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
      throw new BadCredentialsException("Your Google account email is not verified.");
    }
    if (!allowedDomains.isEmpty()) {
      // "hd" is only present for Google Workspace accounts and is the authoritative domain.
      String hostedDomain = jwt.getClaimAsString("hd");
      if (hostedDomain == null || !allowedDomains.contains(hostedDomain.toLowerCase())) {
        throw new BadCredentialsException("Sign in with your organisation's Google account.");
      }
    }
    String name = jwt.getClaimAsString("name");
    String picture = jwt.getClaimAsString("picture");
    // Only Google's own image host is trusted, so the stored URL can't point somewhere else.
    if (picture == null
        || !picture.startsWith("https://")
        || !picture.matches("^https://[a-z0-9-]+\\.googleusercontent\\.com/[^\\s\"'<>]{1,500}$")) {
      picture = null;
    }
    return new GoogleIdentity(jwt.getSubject(), email.toLowerCase(), name, picture);
  }

  private JwtDecoder decoder() {
    JwtDecoder current = decoder;
    if (current == null) {
      synchronized (this) {
        if (decoder == null) {
          NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build();
          nimbus.setJwtValidator(
              new DelegatingOAuth2TokenValidator<>(
                  new JwtTimestampValidator(), claim("iss", ISSUERS), audience()));
          decoder = nimbus;
        }
        current = decoder;
      }
    }
    return current;
  }

  private OAuth2TokenValidator<Jwt> audience() {
    return jwt ->
        jwt.getAudience() != null && jwt.getAudience().contains(clientId)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token was not issued for this app", null));
  }

  private static OAuth2TokenValidator<Jwt> claim(String name, Set<String> allowed) {
    return jwt ->
        allowed.contains(jwt.getClaimAsString(name))
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Unexpected " + name, null));
  }
}
