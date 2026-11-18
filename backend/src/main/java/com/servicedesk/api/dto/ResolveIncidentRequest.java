package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveIncidentRequest(
    @NotBlank(message = "Resolution summary is required") String resolutionSummary) {}
