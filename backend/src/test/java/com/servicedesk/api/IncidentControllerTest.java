package com.servicedesk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.CreateIncidentRequest;
import com.servicedesk.api.dto.IncidentDetailResponse;
import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.SlaWindowResponse;
import com.servicedesk.api.dto.UserSummaryResponse;
import com.servicedesk.application.AssistantApplicationService;
import com.servicedesk.application.IncidentApplicationService;
import com.servicedesk.domain.escalation.EscalationLevel;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.infrastructure.config.ClockConfig;
import com.servicedesk.infrastructure.security.DomainUserDetailsService;
import com.servicedesk.infrastructure.security.JwtAuthenticationFilter;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import com.servicedesk.infrastructure.security.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncidentController.class)
@Import({SecurityConfig.class, ClockConfig.class, JwtAuthenticationFilter.class})
class IncidentControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private IncidentApplicationService incidentService;
  @MockBean private AssistantApplicationService assistantService;
  @MockBean private JwtTokenProvider jwtTokenProvider;
  @MockBean private DomainUserDetailsService userDetailsService;

  @Test
  @WithMockUser(
      username = "emp",
      roles = {"EMPLOYEE"})
  @DisplayName("POST /api/v1/incidents creates ticket and returns 201 Created")
  void testCreateIncident() throws Exception {
    UUID incidentId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();

    CreateIncidentRequest request =
        new CreateIncidentRequest(
            "Lab 304 gateway unreachable",
            "Desktops cannot reach local gateway",
            Category.NETWORK,
            Impact.HIGH,
            Urgency.HIGH);

    IncidentDetailResponse response =
        new IncidentDetailResponse(
            incidentId,
            "INC-1001",
            request.title(),
            request.description(),
            request.category(),
            IncidentStatus.ASSIGNED,
            Priority.P1_CRITICAL,
            request.impact(),
            request.urgency(),
            new UserSummaryResponse(reporterId, "Employee", null),
            null,
            new SlaWindowResponse(
                Instant.now().plusSeconds(900),
                Instant.now().plusSeconds(7200),
                SlaStatus.WITHIN_SLA,
                0),
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            EscalationLevel.NONE);

    when(incidentService.createIncident(any(CreateIncidentRequest.class), eq("emp")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(incidentId.toString()))
        .andExpect(jsonPath("$.incidentNumber").value("INC-1001"))
        .andExpect(jsonPath("$.priority").value("P1_CRITICAL"))
        .andExpect(jsonPath("$.status").value("ASSIGNED"));
  }

  @Test
  @WithMockUser(
      username = "emp",
      roles = {"EMPLOYEE"})
  @DisplayName("POST /api/v1/incidents fails with 400 Bad Request on invalid input")
  void testCreateIncidentValidationFailure() throws Exception {
    CreateIncidentRequest invalid = new CreateIncidentRequest("", "", null, null, null);

    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "emp", roles = "EMPLOYEE")
  void malformedRequestsReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"INVALID\"}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/incidents/not-a-uuid")).andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/v1/incidents?status=INVALID")).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(
      username = "tech_lead",
      roles = {"TECHNICIAN"})
  @DisplayName("GET /api/v1/incidents returns paged list")
  void testListIncidents() throws Exception {
    when(incidentService.listIncidents(any(), eq(0), eq(20), eq("tech_lead")))
        .thenReturn(PagedResponse.of(List.of(), 0, 20));

    mockMvc
        .perform(get("/api/v1/incidents"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
