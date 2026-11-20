package com.servicedesk.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
    properties = {
      "app.rate-limit.auth-per-minute=10",
      "app.rate-limit.ai-per-minute=2",
      "app.rate-limit.login.max-failures=5"
    })
class RateLimitingTest {

  @Autowired MockMvc mvc;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired PasswordEncoder passwordEncoder;

  /** Each test uses its own addresses and usernames: buckets live as long as the context. */
  private static MockHttpServletRequestBuilder login(String ip, String username, String password) {
    return post("/api/v1/auth/login")
        .with(
            request -> {
              request.setRemoteAddr(ip);
              return request;
            })
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
  }

  @Test
  void theEleventhSignInAttemptInAMinuteIsRefused() throws Exception {
    for (int i = 0; i < 10; i++) {
      mvc.perform(login("10.0.0.1", "nobody" + i, "wrong-password"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(login("10.0.0.1", "nobody", "wrong-password"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", Matchers.not(Matchers.emptyString())))
        .andExpect(header().string("RateLimit-Remaining", "0"))
        .andExpect(jsonPath("$.message", Matchers.startsWith("Too many requests")));

    // Another address is unaffected.
    mvc.perform(login("10.0.0.2", "nobody", "wrong-password")).andExpect(status().isUnauthorized());
  }

  @Test
  void aForgedForwardedForHeaderDoesNotResetTheLimit() throws Exception {
    for (int i = 0; i < 10; i++) {
      mvc.perform(login("10.0.1.1", "x" + i, "wrong").header("X-Forwarded-For", "203.0.113." + i))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(login("10.0.1.1", "x", "wrong").header("X-Forwarded-For", "198.51.100.7"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void fiveWrongPasswordsLockTheAccountFromAnyAddress() throws Exception {
    String password = "correct-horse-battery";
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "victim",
            passwordEncoder.encode(password),
            "Victim",
            null,
            Role.EMPLOYEE,
            true,
            null,
            null));
    mvc.perform(login("10.0.2.1", "victim", password)).andExpect(status().isOk());

    for (int i = 0; i < 5; i++) {
      mvc.perform(login("10.0.2." + (10 + i), "victim", "guess" + i))
          .andExpect(status().isUnauthorized());
    }
    // Locked: even the right password is refused, with the usual message.
    mvc.perform(login("10.0.2.99", "victim", password))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid username or password"));
  }

  @Test
  void theAssistantHasItsOwnTighterAllowance() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/api/v1/incidents/triage")
                  .with(user("ai-user").roles("EMPLOYEE"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}"))
          .andExpect(status().is(Matchers.not(429)));
    }
    mvc.perform(
            post("/api/v1/incidents/triage")
                .with(user("ai-user").roles("EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isTooManyRequests());
  }
}
