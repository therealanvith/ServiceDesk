package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.IncidentAuditLogEntity;
import com.servicedesk.infrastructure.persistence.entity.SelfHelpFeedbackEntity;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataAuditLogJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSelfHelpFeedbackJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
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
class TrendsReportTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired SpringDataIncidentJpaRepository incidentRows;
  @Autowired SpringDataAuditLogJpaRepository auditRows;
  @Autowired SpringDataSelfHelpFeedbackJpaRepository feedbackRows;
  @Autowired JdbcTemplate jdbc;

  private UUID aliceId;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    aliceId = users.saveAndFlush(account("alice", Role.EMPLOYEE)).getId();
    users.saveAndFlush(account("boss", Role.ADMINISTRATOR));
    users.saveAndFlush(account("fixer", Role.TECHNICIAN));
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private JsonNode trends(int days) throws Exception {
    String body =
        mvc.perform(
                get("/api/v1/reports/trends")
                    .param("days", String.valueOf(days))
                    .with(user("boss").roles("ADMINISTRATOR")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  @Test
  void onlyAdministratorsSeeTrendsAndOnlyForTheOfferedRanges() throws Exception {
    mvc.perform(
            get("/api/v1/reports/trends").param("days", "7").with(user("alice").roles("EMPLOYEE")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/reports/trends")
                .param("days", "7")
                .with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isForbidden());

    for (String bad : new String[] {"14", "0", "-7", "365", "week"}) {
      mvc.perform(
              get("/api/v1/reports/trends")
                  .param("days", bad)
                  .with(user("boss").roles("ADMINISTRATOR")))
          .andExpect(status().isBadRequest());
    }

    for (int days : new int[] {7, 30, 90}) {
      mvc.perform(
              get("/api/v1/reports/trends")
                  .param("days", String.valueOf(days))
                  .with(user("boss").roles("ADMINISTRATOR")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.days").value(days))
          .andExpect(jsonPath("$.daily.length()").value(days))
          .andExpect(jsonPath("$.escalationsByLevel.length()").value(3));
    }
  }

  @Test
  void aResolvedTicketShowsUpInTheDailySeriesAndCompliance() throws Exception {
    JsonNode before = trends(7);

    String body =
        mvc.perform(
                post("/api/v1/incidents")
                    .with(user("alice").roles("EMPLOYEE"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Printer jammed\",\"description\":\"Paper stuck in tray 2\","
                            + "\"category\":\"HARDWARE\",\"impact\":\"LOW\",\"urgency\":\"LOW\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(body).get("id").asText();

    mvc.perform(post("/api/v1/incidents/" + id + "/start").with(user("fixer").roles("TECHNICIAN")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/incidents/" + id + "/resolve")
                .with(user("fixer").roles("TECHNICIAN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionSummary\":\"Cleared the jam and reseated the tray\"}"))
        .andExpect(status().isOk());

    Instant now = Instant.now();
    auditRows.saveAndFlush(
        new IncidentAuditLogEntity(
            UUID.randomUUID(),
            incidentRows.findById(UUID.fromString(id)).orElseThrow(),
            null,
            "ESCALATED",
            "NONE",
            "TEAM_LEAD",
            "Response target passed",
            now));
    feedbackRows.saveAndFlush(new SelfHelpFeedbackEntity(UUID.randomUUID(), aliceId, true, now));
    feedbackRows.saveAndFlush(new SelfHelpFeedbackEntity(UUID.randomUUID(), aliceId, false, now));

    JsonNode after = trends(7);
    String today = LocalDate.now(ZoneOffset.UTC).toString();

    JsonNode lastDay = after.get("daily").get(6);
    JsonNode lastDayBefore = before.get("daily").get(6);
    assertThat(lastDay.get("date").asText()).isEqualTo(today);
    assertThat(lastDay.get("created").asLong())
        .isEqualTo(lastDayBefore.get("created").asLong() + 1);
    assertThat(lastDay.get("resolved").asLong())
        .isEqualTo(lastDayBefore.get("resolved").asLong() + 1);

    JsonNode week = last(after.get("weeklyCompliance"));
    JsonNode weekBefore = last(before.get("weeklyCompliance"));
    assertThat(week.get("resolvedCount").asLong())
        .isEqualTo(weekBefore.get("resolvedCount").asLong() + 1);
    assertThat(week.get("withinSlaCount").asLong())
        .isEqualTo(weekBefore.get("withinSlaCount").asLong() + 1);
    assertThat(week.get("compliancePercent").isNumber()).isTrue();

    assertThat(after.get("resolutionByCategory").toString()).contains("\"category\":\"HARDWARE\"");
    assertThat(after.get("escalationsByLevel").get(0).get("count").asLong())
        .isEqualTo(before.get("escalationsByLevel").get(0).get("count").asLong() + 1);
    assertThat(after.get("selfHelp").get("helped").asLong())
        .isEqualTo(before.get("selfHelp").get("helped").asLong() + 1);
    assertThat(after.get("selfHelp").get("notHelped").asLong())
        .isEqualTo(before.get("selfHelp").get("notHelped").asLong() + 1);
  }

  private static JsonNode last(JsonNode array) {
    return array.get(array.size() - 1);
  }
}
