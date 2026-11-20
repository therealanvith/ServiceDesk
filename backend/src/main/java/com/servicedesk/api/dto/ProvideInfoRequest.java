package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProvideInfoRequest(
    @NotBlank(message = "Answer text is required")
        @Size(max = 5000, message = "Answer must not exceed 5000 characters")
        String answer) {}
