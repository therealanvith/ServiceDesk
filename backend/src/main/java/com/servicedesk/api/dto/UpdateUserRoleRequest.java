package com.servicedesk.api.dto;

import com.servicedesk.domain.user.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull(message = "Role is required") Role role) {}
