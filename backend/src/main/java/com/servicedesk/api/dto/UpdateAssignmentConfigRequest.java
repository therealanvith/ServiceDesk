package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAssignmentConfigRequest(
    @NotBlank(message = "Strategy name is required") String strategy) {}
