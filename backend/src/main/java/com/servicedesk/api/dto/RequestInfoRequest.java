package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestInfoRequest(
    @NotBlank(message = "Question text is required") String question) {}
