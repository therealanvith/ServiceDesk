package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.WeightedPriorityStrategy;
import com.servicedesk.domain.service.BusinessCriticality;
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

/** Admin-configurable categories, and the number of affected users feeding the priority. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriesAndAffectedPriorityTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS incident_number_seq START WITH 1001");
    for (String name : new String[] {"reporter", "a", "b", "c"}) {
      users.saveAndFlush(account(name, Role.EMPLOYEE));
    }
    users.saveAndFlush(account("boss", Role.ADMINISTRATOR));
  }

  private static UserEntity account(String username, Role role) {
    return new UserEntity(
        UUID.randomUUID(), username, "unused", "Name " + username, null, role, true, null, null);
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, String username, String role) {
    return request.with(user(username).roles(role));
  }

  private String report(String category) throws Exception {
    return mvc.perform(
            as(post("/api/v1/incidents"), "reporter", "EMPLOYEE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Portal slow\",\"description\":\"Pages take ages\","
                        + "\"category\":\""
                        + category
                        + "\",\"impact\":\"MEDIUM\",\"urgency\":\"MEDIUM\"}"))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void anAdministratorCanSwitchACategoryOff() throws Exception {
    mvc.perform(as(get("/api/v1/catalog/categories"), "reporter", "EMPLOYEE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(9));

    mvc.perform(
            as(put("/api/v1/catalog/categories/STORAGE"), "boss", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(
            as(post("/api/v1/incidents"), "reporter", "EMPLOYEE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Disk\",\"description\":\"Full\",\"category\":\"STORAGE\","
                        + "\"impact\":\"LOW\",\"urgency\":\"LOW\"}"))
        .andExpect(status().isBadRequest());
    assertThat(report("NETWORK")).contains("Portal slow");
  }

  @Test
  void onlyAdministratorsChangeCategories() throws Exception {
    mvc.perform(
            as(put("/api/v1/catalog/categories/STORAGE"), "reporter", "EMPLOYEE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void moreAffectedPeopleRaiseThePriorityOnly() throws Exception {
    String id = mapper.readTree(report("NETWORK")).get("id").asText();
    mvc.perform(as(get("/api/v1/incidents/" + id), "reporter", "EMPLOYEE"))
        .andExpect(jsonPath("$.priority").value("P3_MEDIUM"));

    for (String name : new String[] {"a", "b"}) {
      mvc.perform(as(post("/api/v1/issues/" + id + "/affected"), name, "EMPLOYEE"))
          .andExpect(status().isOk());
    }
    // Three people are now affected, so the ticket moves up one level.
    mvc.perform(as(get("/api/v1/incidents/" + id), "reporter", "EMPLOYEE"))
        .andExpect(jsonPath("$.priority").value("P2_HIGH"));
  }

  @Test
  void theWeightedScoreUsesTheNumberOfAffectedUsers() {
    var strategy = new WeightedPriorityStrategy();
    var medium = BusinessCriticality.MEDIUM;
    assertThat(strategy.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM, medium, 1))
        .isEqualTo(Priority.P3_MEDIUM);
    assertThat(strategy.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM, medium, 3))
        .isEqualTo(Priority.P2_HIGH);
    assertThat(strategy.calculatePriority(Impact.MEDIUM, Urgency.MEDIUM, medium, 10))
        .isEqualTo(Priority.P1_CRITICAL);
    // One person changes nothing, so the old three-argument result is unchanged.
    assertThat(strategy.calculatePriority(Impact.LOW, Urgency.LOW, medium))
        .isEqualTo(strategy.calculatePriority(Impact.LOW, Urgency.LOW, medium, 1));
  }
}
