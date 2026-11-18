package com.servicedesk.api.dto;

import com.servicedesk.domain.priority.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OverridePriorityRequest(
    @NotNull(message = "New priority is required") Priority priority,
    @NotBlank(message = "Override justification is required") String remarks) {}
