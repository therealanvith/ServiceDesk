package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.ArrayList;
import java.util.List;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UpdatesAndIssueBoardTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired JdbcTemplate jdbc;

  private String ticketId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(account("reporter", Role.EMPLOYEE));
    users.saveAndFlush(account("tech", Role.TECHNICIAN));
    users.saveAndFlush(account("other", Role.EMPLOYEE));
    ticketId =
        json(mvc.perform(
                    json(
                        post("/api/v1/incidents"),
                        "reporter",
                        "EMPLOYEE",
                        "{\"title\":\"VPN drops\",\"description\":\"Disconnects every hour\","
                            + "\"category\":\"NETWORK\",\"impact\":\"LOW\",\"urgency\":\"LOW\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get("id")
            .asText();
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String username, String role, String body) {
    return request
        .with(user(username).roles(role))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private JsonNode json(String body) throws Exception {
    return mapper.readTree(body);
  }

  private List<String> kinds(String username, String role) throws Exception {
    JsonNode thread =
        json(
            mvc.perform(
                    get("/api/v1/incidents/" + ticketId + "/comments")
                        .with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    List<String> kinds = new ArrayList<>();
    thread.forEach(entry -> kinds.add(entry.get("kind").asText()));
    return kinds;
  }

  @Test
  void lifecycleActionsAppearInTheUpdatesThread() throws Exception {
    String base = "/api/v1/incidents/" + ticketId;
    mvc.perform(post(base + "/start").with(user("tech").roles("TECHNICIAN")))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                post(base + "/request-info"),
                "tech",
                "TECHNICIAN",
                "{\"question\":\"Which network are you on?\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                post(base + "/provide-info"),
                "reporter",
                "EMPLOYEE",
                "{\"answer\":\"Home Wi-Fi\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                post(base + "/resolve"),
                "tech",
                "TECHNICIAN",
                "{\"resolutionSummary\":\"Updated the VPN client\"}"))
        .andExpect(status().isOk());
    mvc.perform(json(post(base + "/close"), "reporter", "EMPLOYEE", "{\"rating\":5}"))
        .andExpect(status().isOk());

    assertThat(kinds("reporter", "EMPLOYEE"))
        .containsExactly("PICKUP", "QUESTION", "ANSWER", "RESOLUTION", "STATUS");
  }

  @Test
  void everyoneCanReadIssuesWithoutReporterIdentityOrInternalNotes() throws Exception {
    String base = "/api/v1/incidents/" + ticketId;
    mvc.perform(
            json(
                post(base + "/comments"),
                "reporter",
                "EMPLOYEE",
                "{\"commentText\":\"Still happening\",\"isInternal\":false}"))
        .andExpect(status().isCreated());
    mvc.perform(
            json(
                post(base + "/comments"),
                "tech",
                "TECHNICIAN",
                "{\"commentText\":\"Suspect the reporter's router\",\"isInternal\":true}"))
        .andExpect(status().isCreated());

    String body =
        mvc.perform(get("/api/v1/issues/" + ticketId).with(user("other").roles("EMPLOYEE")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode issue = json(body);
    assertThat(issue.get("updates")).hasSize(1);
    assertThat(issue.get("updates").get(0).get("author").asText()).isEqualTo("Reporter");
    assertThat(body).doesNotContain("Name reporter").doesNotContain("router");

    JsonNode list =
        json(
            mvc.perform(get("/api/v1/issues").with(user("other").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(list.get("totalElements").asLong()).isEqualTo(1);

    // The reporter's own thread never carries staff-only notes; staff see both entries.
    assertThat(kinds("reporter", "EMPLOYEE")).containsExactly("COMMENT");
    assertThat(kinds("tech", "TECHNICIAN")).containsExactly("COMMENT", "COMMENT");

    // Read-only: the scoped ticket endpoints still hide it from other employees.
    mvc.perform(get(base).with(user("other").roles("EMPLOYEE"))).andExpect(status().isNotFound());
  }
}
