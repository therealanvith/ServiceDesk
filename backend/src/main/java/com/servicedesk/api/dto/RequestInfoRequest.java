package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestInfoRequest(
    @NotBlank(message = "Question text is required")
    @Size(max = 5000, message = "Question must not exceed 5000 characters")
    String question) {}
