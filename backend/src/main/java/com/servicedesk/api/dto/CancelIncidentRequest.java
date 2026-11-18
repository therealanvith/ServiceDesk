package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelIncidentRequest(
    @NotBlank(message = "Cancellation reason is required") String reason) {}
