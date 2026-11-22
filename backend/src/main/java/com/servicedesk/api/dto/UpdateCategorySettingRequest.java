package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCategorySettingRequest(
    @NotNull(message = "Say whether the category is active") Boolean active) {}
