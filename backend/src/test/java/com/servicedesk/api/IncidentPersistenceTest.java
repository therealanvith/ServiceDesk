package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.service.BusinessCriticality;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.ItServiceEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataItServiceJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Instant;
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
  @Autowired SpringDataItServiceJpaRepository services;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "reporter",
            "unused",
            "Reporter",
            "reporter@test.example",
            Role.EMPLOYEE,
            true,
            null,
            null));
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void createsIncidentWithAuditAndUniqueNumbers() throws Exception {
    JsonNode first = create();
    JsonNode second = create();
    incidents.flush();
    assertThat(first.get("status").asText()).isEqualTo("REPORTED");
    assertThat(second.get("incidentNumber").asText())
        .isNotEqualTo(first.get("incidentNumber").asText());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM incident_audit_logs WHERE incident_id = ? AND action = 'CREATED'",
                Integer.class,
                UUID.fromString(first.get("id").asText())))
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void createsAssignedIncidentWithAudit() throws Exception {
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "technician",
            "unused",
            "Technician",
            "technician@test.example",
            Role.TECHNICIAN,
            true,
            null,
            null));
    JsonNode result = create();
    incidents.flush();
    assertThat(result.get("status").asText()).isEqualTo("ASSIGNED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM incident_audit_logs WHERE incident_id = ?",
                Integer.class,
                UUID.fromString(result.get("id").asText())))
        .isEqualTo(2);
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void otherEmployeesCannotReadCommentsOrChangeIncident() throws Exception {
    String id = create().get("id").asText();
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "other",
            "unused",
            "Other",
            "other@test.example",
            Role.EMPLOYEE,
            true,
            null,
            null));
    mvc.perform(get("/api/v1/incidents/" + id + "/comments").with(user("other").roles("EMPLOYEE")))
        .andExpect(status().isNotFound());
    for (String action : new String[] {"comments", "cancel", "close", "provide-info"}) {
      mvc.perform(
              post("/api/v1/incidents/" + id + "/" + action)
                  .with(user("other").roles("EMPLOYEE"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
              {"commentText":"Test comment", "isInternal":false,
               "reason":"Test reason", "answer":"Test answer"}
              """))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void onlyAssigneeWorksTicketAndOnlyReporterClosesIt() throws Exception {
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "technician",
            "unused",
            "Technician",
            "technician@test.example",
            Role.TECHNICIAN,
            true,
            null,
            null));
    String id = create().get("id").asText();
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "bystander",
            "unused",
            "Bystander",
            "bystander@test.example",
            Role.TECHNICIAN,
            true,
            null,
            null));

    mvc.perform(
            post("/api/v1/incidents/" + id + "/start").with(user("bystander").roles("TECHNICIAN")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/incidents/" + id + "/start").with(user("technician").roles("TECHNICIAN")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/incidents/" + id + "/resolve")
                .with(user("technician").roles("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionSummary\":\"Replaced cable\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/incidents/" + id + "/close").with(user("technician").roles("TECHNICIAN")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/incidents/" + id + "/close")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void listIsScopedByRole() throws Exception {
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "technician",
            "unused",
            "Technician",
            "technician@test.example",
            Role.TECHNICIAN,
            true,
            null,
            null));
    create(); // auto-assigned to "technician"
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(), "other", "unused", "Other", null, Role.EMPLOYEE, true, null, null));
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "idle-tech",
            "unused",
            "Idle",
            null,
            Role.TECHNICIAN,
            false,
            null,
            null));
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "boss",
            "unused",
            "Boss",
            null,
            Role.ADMINISTRATOR,
            true,
            null,
            null));
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(), "root", "unused", "Root", null, Role.ROOT, true, null, null));

    assertThat(total("reporter", "EMPLOYEE")).isEqualTo(1);
    assertThat(total("other", "EMPLOYEE")).isZero();
    assertThat(total("technician", "TECHNICIAN")).isEqualTo(1);
    assertThat(total("idle-tech", "TECHNICIAN")).isZero();
    assertThat(total("boss", "ADMINISTRATOR")).isEqualTo(1);
    assertThat(total("root", "ROOT")).isEqualTo(1);

    // A technician cannot widen their scope through query parameters.
    mvc.perform(
            get("/api/v1/incidents?reporterId=" + UUID.randomUUID())
                .with(user("idle-tech").roles("TECHNICIAN")))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void onlyAdminsAssignTickets() throws Exception {
    String id = create().get("id").asText();
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "technician",
            "unused",
            "Technician",
            null,
            Role.TECHNICIAN,
            true,
            null,
            null));
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(), "root", "unused", "Root", null, Role.ROOT, true, null, null));
    mvc.perform(
            post("/api/v1/incidents/" + id + "/assign")
                .with(user("technician").roles("TECHNICIAN")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/incidents/" + id + "/assign").with(user("root").roles("ROOT")))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void criticalServiceRaisesPriorityAndShowsInReports() throws Exception {
    ItServiceEntity payments =
        services.saveAndFlush(
            new ItServiceEntity(
                UUID.randomUUID(),
                "Payment Application",
                null,
                BusinessCriticality.CRITICAL,
                Instant.now()));
    JsonNode created =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Checkout failing\",\"description\":\"Card payments time out\","
                                + "\"category\":\"APPLICATION\",\"impact\":\"MEDIUM\",\"urgency\":\"MEDIUM\","
                                + "\"serviceId\":\""
                                + payments.getId()
                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    // Medium/medium alone would be P3; the critical service lifts it to P1.
    assertThat(created.get("priority").asText()).isEqualTo("P1_CRITICAL");
    assertThat(created.get("service").get("name").asText()).isEqualTo("Payment Application");

    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            "boss",
            "unused",
            "Boss",
            null,
            Role.ADMINISTRATOR,
            true,
            null,
            null));
    JsonNode report =
        mapper.readTree(
            mvc.perform(get("/api/v1/reports/overview").with(user("boss").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(report.get("overview").get("total").asLong()).isEqualTo(1);
    assertThat(report.get("overview").get("criticalOpen").asLong()).isEqualTo(1);
    assertThat(report.get("byService").get(0).get("service").asText())
        .isEqualTo("Payment Application");

    mvc.perform(get("/api/v1/reports/overview")).andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/incidents?serviceId="
                    + payments.getId()
                    + "&createdFrom=2000-01-01&createdTo=2999-12-31")
                .with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "reporter", roles = "EMPLOYEE")
  void warnsAboutDuplicatesAndSuggestsCategory() throws Exception {
    create();
    JsonNode duplicates =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/incidents/check-duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Network unavailable again\",\"description\":\"Cannot reach the gateway\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(duplicates).hasSize(1);
    assertThat(duplicates.get(0).get("open").asBoolean()).isTrue();

    // Someone else's open ticket is not revealed to a different employee.
    users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(), "other", "unused", "Other", null, Role.EMPLOYEE, true, null, null));
    JsonNode hidden =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/incidents/check-duplicates")
                        .with(user("other").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Network unavailable\",\"description\":\"Cannot reach the gateway\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(hidden).isEmpty();

    JsonNode suggestion =
        mapper.readTree(
            mvc.perform(
                    post("/api/v1/incidents/suggest-category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"title\":\"Wi-Fi down\",\"description\":\"No internet in the library\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(suggestion.get("category").asText()).isEqualTo("NETWORK");
  }

  private long total(String username, String role) throws Exception {
    return mapper
        .readTree(
            mvc.perform(get("/api/v1/incidents").with(user(username).roles(role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("totalElements")
        .asLong();
  }

  private JsonNode create() throws Exception {
    return mapper.readTree(
        mvc.perform(
                post("/api/v1/incidents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
            {"title":"Network unavailable","description":"Cannot reach the gateway",
             "category":"NETWORK","impact":"HIGH","urgency":"HIGH"}
            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
