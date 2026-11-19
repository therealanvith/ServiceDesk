package com.servicedesk.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSlaPolicyRequest(
    @NotNull(message = "Response target is required")
    @Min(value = 1, message = "Response target must be at least 1 minute")
    @Max(value = 43200, message = "Response target must be at most 30 days")
    Integer responseMinutes,
    @NotNull(message = "Resolution target is required")
    @Min(value = 1, message = "Resolution target must be at least 1 minute")
    @Max(value = 43200, message = "Resolution target must be at most 30 days")
    Integer resolutionMinutes) {}
