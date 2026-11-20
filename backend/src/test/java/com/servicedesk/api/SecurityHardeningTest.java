package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Regression tests for the security review findings. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityHardeningTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;

  @MockBean GoogleIdTokenVerifier google;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    when(google.isEnabled()).thenReturn(true);
  }

  private UserEntity account(String username, Role role, String email) {
    return users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            username,
            encoder.encode("a-long-enough-password"),
            username,
            email,
            role,
            true,
            null,
            null));
  }

  private String login(String username, String password) throws Exception {
    return mapper
        .readTree(
            mvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"username\":\""
                                + username
                                + "\",\"password\":\""
                                + password
                                + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("token")
        .asText();
  }

  @Test
  void googleNeverTakesOverAnAccountWithTheSameEmail() throws Exception {
    account("victim", Role.EMPLOYEE, "victim@example.com");
    when(google.verify(anyString()))
        .thenReturn(new GoogleIdentity("google-sub-1", "victim@example.com", "Victim"));

    mvc.perform(
            post("/api/v1/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"x.y.z\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(users.findByUsername("victim").orElseThrow().getGoogleSub()).isNull();
  }

  @Test
  void signedInUsersCanLinkGoogleExplicitly() throws Exception {
    account("linker", Role.EMPLOYEE, null);
    when(google.verify(anyString()))
        .thenReturn(new GoogleIdentity("google-sub-2", "linker@example.com", "Linker"));

    mvc.perform(
            post("/api/v1/users/me/google")
                .with(user("linker").roles("EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"x.y.z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.googleLinked").value(true));
  }

  @Test
  void adminCreatedAccountsMustChangeTheirTemporaryPassword() throws Exception {
    account("boss", Role.ADMINISTRATOR, null);
    mvc.perform(
            post("/api/v1/users")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"new.hire\",\"fullName\":\"New Hire\","
                        + "\"role\":\"EMPLOYEE\",\"password\":\"temporary-password-1\"}"))
        .andExpect(status().isCreated());
    users.flush();

    String token = login("new.hire", "temporary-password-1");
    mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));

    mvc.perform(
            post("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"currentPassword\":\"temporary-password-1\","
                        + "\"newPassword\":\"my-own-secret-password\"}"))
        .andExpect(status().isNoContent());
    users.flush();
    mvc.perform(get("/api/v1/incidents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void techniciansSeeTicketsTheyReported() throws Exception {
    account("tech.reporter", Role.TECHNICIAN, null);
    // No other technician exists, but the reporter is a technician and may be auto-assigned;
    // either way the ticket must be in their worklist.
    mvc.perform(
            post("/api/v1/incidents")
                .with(user("tech.reporter").roles("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Printer jam\",\"description\":\"Paper stuck\","
                        + "\"category\":\"HARDWARE\",\"impact\":\"LOW\",\"urgency\":\"LOW\"}"))
        .andExpect(status().isCreated());
    JsonNode list =
        mapper.readTree(
            mvc.perform(get("/api/v1/incidents").with(user("tech.reporter").roles("TECHNICIAN")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(list.get("totalElements").asLong()).isEqualTo(1);
  }

  @Test
  void unauthenticatedErrorsUseTheStandardErrorBody() throws Exception {
    mvc.perform(get("/api/v1/incidents"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.path").value("/api/v1/incidents"));
  }

  @Test
  void publicPlaceholderJwtSecretsAreRejected() {
    assertThatThrownBy(
            () ->
                new JwtTokenProvider(
                    "default_dev_secret_key_change_in_production", 3600, new ObjectMapper()))
        .isInstanceOf(IllegalStateException.class);
  }
}
