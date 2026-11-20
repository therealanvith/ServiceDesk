package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code currentPassword} may be empty for Google-only accounts setting their first password. */
public record ChangePasswordRequest(
    @Size(max = 128) String currentPassword,
    @NotBlank(message = "New password is required")
        @Size(min = 12, max = 128, message = "Password must be 12-128 characters")
        String newPassword) {}
