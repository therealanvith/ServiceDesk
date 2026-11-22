package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
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

/** "Affects me too" without a new ticket, quick fix for reporters, help chat, team-lead assign. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PhaseFiveTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired JdbcTemplate jdbc;

  private String ticketId;
  private UUID techId;

  @BeforeEach
  void setUp() throws Exception {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(account("reporter", Role.EMPLOYEE));
    users.saveAndFlush(account("other", Role.EMPLOYEE));
    UserEntity tech = account("tech", Role.TECHNICIAN);
    techId = tech.getId();
    users.saveAndFlush(tech);
    UserEntity lead = account("lead", Role.TECHNICIAN);
    lead.setTeamLead(true);
    users.saveAndFlush(lead);
    users.saveAndFlush(account("plain", Role.TECHNICIAN));
    String body =
        mvc.perform(
                as(post("/api/v1/incidents"), "reporter", "EMPLOYEE")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Printer offline\",\"description\":\"The printer says"
                            + " offline\",\"category\":\"HARDWARE\",\"impact\":\"LOW\","
                            + "\"urgency\":\"LOW\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ticketId = mapper.readTree(body).get("id").asText();
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, String username, String role) {
    return request.with(user(username).roles(role));
  }

  private MockHttpServletRequestBuilder chat(String username, String text) {
    return as(post("/api/v1/assistant/chat"), username, "EMPLOYEE")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"messages\":[{\"text\":\"" + text + "\",\"fromUser\":true}]}");
  }

  @Test
  void affectsMeTooCountsThePersonWithoutOpeningATicket() throws Exception {
    String path = "/api/v1/issues/" + ticketId;
    mvc.perform(as(get(path), "other", "EMPLOYEE"))
        .andExpect(jsonPath("$.affectedCount").value(1))
        .andExpect(jsonPath("$.affectedByMe").value(false));

    mvc.perform(as(post(path + "/affected"), "other", "EMPLOYEE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.affectedCount").value(2))
        .andExpect(jsonPath("$.affectedByMe").value(true));
    // Twice changes nothing.
    mvc.perform(as(post(path + "/affected"), "other", "EMPLOYEE"))
        .andExpect(jsonPath("$.affectedCount").value(2));

    // No new ticket was opened: they follow the existing one.
    mvc.perform(as(get("/api/v1/incidents"), "other", "EMPLOYEE"))
        .andExpect(jsonPath("$.content[0].id").value(ticketId));

    mvc.perform(as(delete(path + "/affected"), "other", "EMPLOYEE"))
        .andExpect(jsonPath("$.affectedCount").value(1))
        .andExpect(jsonPath("$.affectedByMe").value(false));
  }

  @Test
  void theReporterCannotInflateTheCount() throws Exception {
    mvc.perform(as(post("/api/v1/issues/" + ticketId + "/affected"), "reporter", "EMPLOYEE"))
        .andExpect(jsonPath("$.affectedCount").value(1))
        .andExpect(jsonPath("$.affectedByMe").value(true));
  }

  @Test
  void reporterGetsAQuickFixOnTheirOwnTicketButOthersDoNot() throws Exception {
    String path = "/api/v1/incidents/" + ticketId + "/ai/suggest-fix";
    mvc.perform(as(post(path), "reporter", "EMPLOYEE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.steps").isNotEmpty())
        .andExpect(jsonPath("$.source").value("rules"));
    mvc.perform(as(post(path), "other", "EMPLOYEE")).andExpect(status().isNotFound());
  }

  @Test
  void employeesCannotRequestASummary() throws Exception {
    mvc.perform(as(post("/api/v1/incidents/" + ticketId + "/ai/summary"), "reporter", "EMPLOYEE"))
        .andExpect(status().isForbidden());
  }

  @Test
  void helpChatAnswersFromTheKnowledgeBase() throws Exception {
    String reply =
        mvc.perform(chat("reporter", "my wifi keeps dropping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("rules"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(mapper.readTree(reply).get("reply").asText()).contains("Wi-Fi");
  }

  @Test
  void helpChatListsTheCallersOwnTicketsOnly() throws Exception {
    String text = "what is the status of my ticket";
    String mine =
        mvc.perform(chat("reporter", text)).andReturn().getResponse().getContentAsString();
    assertThat(mapper.readTree(mine).get("reply").asText()).contains("Printer offline");
    String theirs = mvc.perform(chat("other", text)).andReturn().getResponse().getContentAsString();
    assertThat(mapper.readTree(theirs).get("reply").asText()).doesNotContain("Printer offline");
  }

  @Test
  void helpChatRejectsAnEmptyConversation() throws Exception {
    mvc.perform(
            as(post("/api/v1/assistant/chat"), "reporter", "EMPLOYEE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void teamLeadCanAssignAndSeeEveryTicketButOtherTechniciansCannot() throws Exception {
    mvc.perform(as(get("/api/v1/incidents"), "lead", "TECHNICIAN"))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(as(get("/api/v1/users/technicians"), "lead", "TECHNICIAN"))
        .andExpect(status().isOk());

    String assign = "/api/v1/incidents/" + ticketId + "/assign";
    String body = "{\"technicianId\":\"" + techId + "\"}";
    mvc.perform(
            as(post(assign), "lead", "TECHNICIAN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee.id").value(techId.toString()));

    mvc.perform(
            as(post(assign), "plain", "TECHNICIAN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void joinedIssueShowsInMyTicketsWithoutTheReporterName() throws Exception {
    mvc.perform(as(post("/api/v1/issues/" + ticketId + "/affected"), "other", "EMPLOYEE"))
        .andExpect(status().isOk());
    mvc.perform(as(get("/api/v1/incidents"), "other", "EMPLOYEE"))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Printer offline"))
        .andExpect(jsonPath("$.content[0].joined").value(true))
        .andExpect(jsonPath("$.content[0].reporterName").value("Another user"));
    mvc.perform(as(get("/api/v1/incidents"), "reporter", "EMPLOYEE"))
        .andExpect(jsonPath("$.content[0].joined").value(false));
  }
}
