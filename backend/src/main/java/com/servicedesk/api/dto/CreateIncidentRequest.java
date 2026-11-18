package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    String title,

    @NotBlank(message = "Description is required")
    String description,

    @NotNull(message = "Category is required")
    Category category,

    @NotNull(message = "Impact is required")
    Impact impact,

    @NotNull(message = "Urgency is required")
    Urgency urgency) {}
