package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IncidentPersistenceTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired SpringDataIncidentJpaRepository incidents;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "reporter", "unused", "Reporter",
        "reporter@test.example", Role.EMPLOYEE, true, null, null));
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void createsIncidentWithAuditAndUniqueNumbers() throws Exception {
    JsonNode first = create();
    JsonNode second = create();
    incidents.flush();
    assertThat(first.get("status").asText()).isEqualTo("REPORTED");
    assertThat(second.get("incidentNumber").asText()).isNotEqualTo(first.get("incidentNumber").asText());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_audit_logs WHERE incident_id = ? AND action = 'CREATED'",
        Integer.class, UUID.fromString(first.get("id").asText()))).isEqualTo(1);
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void createsAssignedIncidentWithAudit() throws Exception {
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "technician", "unused", "Technician",
        "technician@test.example", Role.TECHNICIAN, true, null, null));
    JsonNode result = create();
    incidents.flush();
    assertThat(result.get("status").asText()).isEqualTo("ASSIGNED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM incident_audit_logs WHERE incident_id = ?",
        Integer.class, UUID.fromString(result.get("id").asText()))).isEqualTo(2);
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void otherEmployeesCannotReadCommentsOrChangeIncident() throws Exception {
    String id = create().get("id").asText();
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "other", "unused", "Other",
        "other@test.example", Role.EMPLOYEE, true, null, null));
    mvc.perform(get("/api/v1/incidents/" + id + "/comments").with(user("other").roles("EMPLOYEE")))
        .andExpect(status().isForbidden());
    for (String action : new String[] {"comments", "cancel", "close", "provide-info"}) {
      mvc.perform(post("/api/v1/incidents/" + id + "/" + action)
          .with(user("other").roles("EMPLOYEE"))
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
              {"commentText":"Test comment", "isInternal":false,
               "reason":"Test reason", "answer":"Test answer"}
              """))
          .andExpect(status().isForbidden());
    }
  }

  private JsonNode create() throws Exception {
    return mapper.readTree(mvc.perform(post("/api/v1/incidents")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"title":"Network unavailable","description":"Cannot reach the gateway",
             "category":"NETWORK","impact":"HIGH","urgency":"HIGH"}
            """))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  }
}
