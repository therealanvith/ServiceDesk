package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserActiveRequest(
    @NotNull(message = "Active flag is required") Boolean active) {}
