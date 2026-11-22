package com.servicedesk.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcknowledgeAndCapacityTest {

  private static final String TICKET =
      "{\"title\":\"Printer jam\",\"description\":\"Paper stuck in tray 2\","
          + "\"category\":\"HARDWARE\",\"impact\":\"LOW\",\"urgency\":\"LOW\"}";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired JdbcTemplate jdbc;

  private UserEntity tech;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(account("reporter", Role.EMPLOYEE));
    users.saveAndFlush(account("boss", Role.ADMINISTRATOR));
    tech = account("tech", Role.TECHNICIAN);
    tech.setMaxActiveTickets(1);
    tech = users.saveAndFlush(tech);
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private String report() throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/incidents")
                    .with(user("reporter").roles("EMPLOYEE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TICKET))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("id").asText();
  }

  @Test
  void acknowledgingMeetsTheResponseTargetAndTellsTheReporter() throws Exception {
    String id = report();

    mvc.perform(
            post("/api/v1/incidents/" + id + "/acknowledge").with(user("tech").roles("TECHNICIAN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
        .andExpect(jsonPath("$.respondedAt").isNotEmpty());

    mvc.perform(
            get("/api/v1/incidents/" + id + "/comments").with(user("reporter").roles("EMPLOYEE")))
        .andExpect(jsonPath("$[*].kind", Matchers.contains("PICKUP")));

    // Starting after acknowledging does not post a second pick-up entry.
    mvc.perform(post("/api/v1/incidents/" + id + "/start").with(user("tech").roles("TECHNICIAN")))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    mvc.perform(
            get("/api/v1/incidents/" + id + "/comments").with(user("reporter").roles("EMPLOYEE")))
        .andExpect(jsonPath("$[*].kind", Matchers.contains("PICKUP")));
  }

  @Test
  void aTechnicianAtTheirLimitIsNotGivenMoreWork() throws Exception {
    String first = report();
    mvc.perform(get("/api/v1/incidents/" + first).with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(jsonPath("$.assignee.fullName").value("Name tech"));

    // The only technician is full, so the second ticket waits unassigned...
    String second = report();
    mvc.perform(get("/api/v1/incidents/" + second).with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(jsonPath("$.status").value("REPORTED"));

    // ...and an administrator can't push it onto them either.
    mvc.perform(
            post("/api/v1/incidents/" + second + "/assign")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":\"" + tech.getId() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", Matchers.containsString("limit")));

    // Raising the limit frees them up.
    mvc.perform(
            put("/api/v1/users/" + tech.getId() + "/capacity")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxActiveTickets\":3,\"teamLead\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxActiveTickets").value(3))
        .andExpect(jsonPath("$.teamLead").value(true));
    mvc.perform(
            post("/api/v1/incidents/" + second + "/assign")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":\"" + tech.getId() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"));
  }

  @Test
  void onlyAdministratorsSetCapacity() throws Exception {
    mvc.perform(
            put("/api/v1/users/" + tech.getId() + "/capacity")
                .with(user("tech").roles("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxActiveTickets\":50,\"teamLead\":true}"))
        .andExpect(status().isForbidden());
  }
}
