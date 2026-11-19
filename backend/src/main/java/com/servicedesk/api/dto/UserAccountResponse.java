package com.servicedesk.api.dto;

import com.servicedesk.domain.user.Role;
import java.time.Instant;
import java.util.UUID;

public record UserAccountResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    Role role,
    boolean active,
    boolean passwordSignIn,
    boolean googleSignIn,
    Instant createdAt) {}
