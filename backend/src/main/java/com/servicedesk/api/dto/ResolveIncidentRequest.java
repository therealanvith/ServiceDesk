package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
    @NotBlank(message = "Resolution summary is required")
        @Size(max = 5000, message = "Resolution summary must not exceed 5000 characters")
        String resolutionSummary) {}
