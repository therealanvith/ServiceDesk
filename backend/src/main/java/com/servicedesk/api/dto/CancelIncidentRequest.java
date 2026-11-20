package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelIncidentRequest(
    @NotBlank(message = "Cancellation reason is required")
        @Size(max = 2000, message = "Reason must not exceed 2000 characters")
        String reason) {}
