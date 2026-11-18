package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ProvideInfoRequest(
    @NotBlank(message = "Answer text is required") String answer) {}
