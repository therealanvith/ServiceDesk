package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.service.BusinessCriticality;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Major-incident linking and the service status board. */
public final class MajorIncidentDtos {

  private MajorIncidentDtos() {}

  public record LinkIncidentRequest(@NotNull(message = "Choose the main ticket") UUID parentId) {}

  /** A ticket on either end of a major-incident link. */
  public record LinkedIncidentResponse(
      UUID id, String incidentNumber, String title, IncidentStatus status) {}

  public enum ServiceHealth {
    OPERATIONAL,
    DEGRADED,
    OUTAGE
  }

  /** Counts only, so anyone can see the status without seeing other people's tickets. */
  public record ServiceStatusResponse(
      UUID serviceId,
      String name,
      BusinessCriticality criticality,
      ServiceHealth status,
      long openTickets,
      long openMajorIncidents) {}
}
