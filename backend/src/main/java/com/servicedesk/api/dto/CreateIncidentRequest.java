package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateIncidentRequest(
    @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,
    @NotBlank(message = "Description is required")
        @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description,
    @NotNull(message = "Category is required") Category category,
    @NotNull(message = "Impact is required") Impact impact,
    @NotNull(message = "Urgency is required") Urgency urgency,
    /** Optional affected IT service; its business criticality feeds into the priority. */
    UUID serviceId,
    /** True when the reporter kept the assistant's suggested classification; noted in the audit. */
    Boolean aiSuggestionAccepted,
    /** Set from "This affects me too": the open issue this report joins. */
    UUID relatedIncidentId) {

  public CreateIncidentRequest(
      String title, String description, Category category, Impact impact, Urgency urgency) {
    this(title, description, category, impact, urgency, null, null, null);
  }

  public CreateIncidentRequest(
      String title,
      String description,
      Category category,
      Impact impact,
      Urgency urgency,
      UUID serviceId) {
    this(title, description, category, impact, urgency, serviceId, null, null);
  }
}
