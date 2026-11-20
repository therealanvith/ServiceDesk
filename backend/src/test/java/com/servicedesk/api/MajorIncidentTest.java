package com.servicedesk.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Instant;
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
class MajorIncidentTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired SpringDataItServiceJpaRepository services;
  @Autowired JdbcTemplate jdbc;

  private UUID wifi;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    for (String name : new String[] {"alice", "bob", "carol"}) {
      users.saveAndFlush(account(name, Role.EMPLOYEE));
    }
    users.saveAndFlush(account("boss", Role.ADMINISTRATOR));
    users.saveAndFlush(account("fixer", Role.TECHNICIAN));
    wifi =
        services
            .saveAndFlush(
                new ItServiceEntity(
                    UUID.randomUUID(),
                    "Campus Wi-Fi",
                    "Wireless network",
                    BusinessCriticality.HIGH,
                    Instant.now()))
            .getId();
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private String report(String reporter, String extra) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/incidents")
                    .with(user(reporter).roles("EMPLOYEE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Wi-Fi down\",\"description\":\"Can't connect on floor 2\","
                            + "\"category\":\"NETWORK\",\"impact\":\"LOW\",\"urgency\":\"LOW\","
                            + "\"serviceId\":\""
                            + wifi
                            + "\""
                            + extra
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body).get("id").asText();
  }

  private org.springframework.test.web.servlet.ResultActions link(
      String child, String parent, String who, String role) throws Exception {
    return mvc.perform(
        post("/api/v1/incidents/" + child + "/link")
            .with(user(who).roles(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"parentId\":\"" + parent + "\"}"));
  }

  @Test
  void resolvingTheMainTicketResolvesEveryLinkedOne() throws Exception {
    String main = report("alice", "");
    String second = report("bob", "");
    String third = report("carol", ",\"relatedIncidentId\":\"" + main + "\"");

    link(second, main, "alice", "EMPLOYEE").andExpect(status().isForbidden());
    link(second, main, "boss", "ADMINISTRATOR")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentIncident.id").value(main));

    mvc.perform(get("/api/v1/incidents/" + main).with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(jsonPath("$.childIncidents[*].id", Matchers.containsInAnyOrder(second, third)));

    mvc.perform(get("/api/v1/catalog/service-status").with(user("carol").roles("EMPLOYEE")))
        .andExpect(jsonPath("$[0].status").value("OUTAGE"))
        .andExpect(jsonPath("$[0].openTickets").value(3))
        .andExpect(jsonPath("$[0].openMajorIncidents").value(1));

    mvc.perform(
            post("/api/v1/incidents/" + main + "/start").with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/incidents/" + main + "/resolve")
                .with(user("fixer").roles("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionSummary\":\"Replaced the failed access point\"}"))
        .andExpect(status().isOk());

    for (String child : new String[] {second, third}) {
      mvc.perform(get("/api/v1/incidents/" + child).with(user("boss").roles("ADMINISTRATOR")))
          .andExpect(jsonPath("$.status").value("RESOLVED"))
          .andExpect(
              jsonPath(
                  "$.resolutionNotes",
                  Matchers.containsString("Replaced the failed access point")));
    }
    mvc.perform(get("/api/v1/catalog/service-status").with(user("carol").roles("EMPLOYEE")))
        .andExpect(jsonPath("$[0].status").value("OPERATIONAL"));
  }

  @Test
  void linksAreOneLevelDeep() throws Exception {
    String main = report("alice", "");
    String child = report("bob", "");
    String other = report("carol", "");
    link(child, main, "boss", "ADMINISTRATOR").andExpect(status().isOk());

    // Can't link under a ticket that is itself linked, or move a main ticket under another.
    link(other, child, "boss", "ADMINISTRATOR").andExpect(status().isBadRequest());
    link(main, other, "boss", "ADMINISTRATOR").andExpect(status().isBadRequest());
    link(main, main, "boss", "ADMINISTRATOR").andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/v1/incidents/" + child + "/unlink")
                .with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentIncident").doesNotExist());
  }
}
