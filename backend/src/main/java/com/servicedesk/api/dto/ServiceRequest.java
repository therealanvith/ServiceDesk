package com.servicedesk.api.dto;

import com.servicedesk.domain.service.BusinessCriticality;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
    @NotBlank(message = "Service name is required")
    @Size(max = 100, message = "Service name must not exceed 100 characters")
    String name,
    @Size(max = 1000, message = "Description must not exceed 1000 characters") String description,
    @NotNull(message = "Business criticality is required") BusinessCriticality criticality,
    Boolean active) {}
