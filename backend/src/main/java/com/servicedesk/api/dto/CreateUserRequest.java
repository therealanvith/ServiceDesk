package com.servicedesk.api.dto;

import com.servicedesk.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank(message = "Username is required")
    @Pattern(
        regexp = "^[a-z0-9._-]{3,50}$",
        message = "Username must be 3-50 characters: lowercase letters, digits, '.', '_' or '-'")
    String username,
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    String fullName,
    @Email(message = "Email must be a valid address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    String email,
    @NotNull(message = "Role is required") Role role,
    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must be 12-128 characters")
    String password) {}
