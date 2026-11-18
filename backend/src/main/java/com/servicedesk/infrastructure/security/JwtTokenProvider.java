package com.servicedesk.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lightweight, standard-compliant RFC 7519 HMAC-SHA256 JWT generator and validator.
 */
@Component
public class JwtTokenProvider {

  private static final String HMAC_SHA256 = "HmacSHA256";
  private final byte[] secretKeyBytes;
  private final long validityInSeconds;
  private final ObjectMapper objectMapper;

  public JwtTokenProvider(
      @Value("${app.security.jwt-secret:default_jwt_secret_key_minimum_thirty_two_bytes_long}")
          String secret,
      @Value("${app.security.jwt-validity-seconds:86400}") long validityInSeconds,
      ObjectMapper objectMapper) {
    this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    this.validityInSeconds = validityInSeconds;
    this.objectMapper = objectMapper;
  }

  public String generateToken(UserId userId, String username, Role role) {
    try {
      long now = Instant.now().getEpochSecond();
      long exp = now + validityInSeconds;

      ObjectNode headerNode = objectMapper.createObjectNode();
      headerNode.put("alg", "HS256");
      headerNode.put("typ", "JWT");

      ObjectNode payloadNode = objectMapper.createObjectNode();
      payloadNode.put("sub", username);
      payloadNode.put("userId", userId.getValue().toString());
      payloadNode.put("role", role.name());
      payloadNode.put("iat", now);
      payloadNode.put("exp", exp);

      String headerEncoded = base64UrlEncode(objectMapper.writeValueAsString(headerNode));
      String payloadEncoded = base64UrlEncode(objectMapper.writeValueAsString(payloadNode));
      String content = headerEncoded + "." + payloadEncoded;
      String signature = sign(content);

      return content + "." + signature;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate JWT token", e);
    }
  }

  public boolean validateToken(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        return false;
      }

      String content = parts[0] + "." + parts[1];
      String expectedSignature = sign(content);
      if (!expectedSignature.equals(parts[2])) {
        return false;
      }

      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      JsonNode payload = objectMapper.readTree(payloadJson);
      long exp = payload.path("exp").asLong(0);

      return Instant.now().getEpochSecond() < exp;
    } catch (Exception e) {
      return false;
    }
  }

  public String extractUsername(String token) {
    try {
      String[] parts = token.split("\\.");
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      JsonNode payload = objectMapper.readTree(payloadJson);
      return payload.path("sub").asText();
    } catch (Exception e) {
      return null;
    }
  }

  public String extractRole(String token) {
    try {
      String[] parts = token.split("\\.");
      String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      JsonNode payload = objectMapper.readTree(payloadJson);
      return payload.path("role").asText();
    } catch (Exception e) {
      return null;
    }
  }

  private String sign(String data) throws Exception {
    Mac mac = Mac.getInstance(HMAC_SHA256);
    mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
    byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
  }

  private String base64UrlEncode(String data) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(data.getBytes(StandardCharsets.UTF_8));
  }
}
