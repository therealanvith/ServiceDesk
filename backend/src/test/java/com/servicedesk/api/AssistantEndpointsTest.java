package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSelfHelpFeedbackJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Without GEMINI_API_KEY (as in tests) every answer comes from the rule-based assistant. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssistantEndpointsTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired SpringDataSelfHelpFeedbackJpaRepository feedback;
  @Autowired JdbcTemplate jdbc;

  private String resolvedId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(account("owner", Role.EMPLOYEE));
    users.saveAndFlush(account("someone", Role.EMPLOYEE));
    users.saveAndFlush(account("fixer", Role.TECHNICIAN));

    // Someone else's resolved ticket, whose number and title must never reach "someone".
    String body =
        mvc.perform(
                json(post("/api/v1/incidents"), "owner", "EMPLOYEE")
                    .content(
                        "{\"title\":\"Payroll laptop printer jammed\",\"description\":\"Paper"
                            + " stuck in the printer tray\",\"category\":\"HARDWARE\","
                            + "\"impact\":\"LOW\",\"urgency\":\"LOW\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    resolvedId = mapper.readTree(body).get("id").asText();
    mvc.perform(
            post("/api/v1/incidents/" + resolvedId + "/start")
                .with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isOk());
    mvc.perform(
            json(post("/api/v1/incidents/" + resolvedId + "/resolve"), "fixer", "TECHNICIAN")
                .content("{\"resolutionSummary\":\"Opened tray 2 and removed the torn sheet.\"}"))
        .andExpect(status().isOk());
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String username, String role) {
    return request.with(user(username).roles(role)).contentType(MediaType.APPLICATION_JSON);
  }

  @Test
  void triageFallsBackToRulesWithoutAKey() throws Exception {
    mvc.perform(
            json(post("/api/v1/incidents/triage"), "someone", "EMPLOYEE")
                .content(
                    "{\"title\":\"Wi-Fi outage\",\"description\":\"The whole floor is offline,"
                        + " urgent\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("rules"))
        .andExpect(jsonPath("$.category").value("NETWORK"))
        .andExpect(jsonPath("$.impact").value("HIGH"))
        .andExpect(jsonPath("$.predictedPriority").isNotEmpty())
        .andExpect(jsonPath("$.reason").isNotEmpty());
  }

  @Test
  void selfHelpUsesPastFixesButNeverOtherPeoplesTickets() throws Exception {
    String body =
        mvc.perform(
                json(post("/api/v1/incidents/self-help"), "someone", "EMPLOYEE")
                    .content(
                        "{\"title\":\"Printer jammed\",\"description\":\"Paper stuck in the printer tray\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("rules"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("removed the torn sheet");
    assertThat(body).doesNotContain("INC-").doesNotContain("Payroll").doesNotContain(resolvedId);
  }

  @Test
  void selfHelpFeedbackIsRecorded() throws Exception {
    mvc.perform(
            json(post("/api/v1/incidents/self-help/feedback"), "someone", "EMPLOYEE")
                .content("{\"helped\":true}"))
        .andExpect(status().isNoContent());
    assertThat(feedback.findAll()).anyMatch(f -> f.isHelped());
  }

  @Test
  void fixSuggestionsAndSummariesAreForStaffOnly() throws Exception {
    mvc.perform(
            post("/api/v1/incidents/" + resolvedId + "/ai/summary")
                .with(user("owner").roles("EMPLOYEE")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/incidents/" + resolvedId + "/ai/summary")
                .with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("rules"))
        .andExpect(jsonPath("$.summary").isNotEmpty());
    mvc.perform(
            post("/api/v1/incidents/" + resolvedId + "/ai/suggest-fix")
                .with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps").isArray());
  }
}
