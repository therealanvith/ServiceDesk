package com.servicedesk.api.dto;

import com.servicedesk.domain.priority.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverridePriorityRequest(
    @NotNull(message = "New priority is required") Priority priority,
    @NotBlank(message = "Override justification is required")
        @Size(max = 2000, message = "Justification must not exceed 2000 characters")
        String remarks) {}
