package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.user.Role;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    Role role,
    Set<Category> skills,
    boolean mustChangePassword,
    boolean googleLinked,
    boolean hasPassword,
    boolean teamLead,
    String avatar) {

  public UserProfileResponse(
      UUID id, String username, String fullName, String email, Role role, Set<Category> skills) {
    this(id, username, fullName, email, role, skills, false, false, true, false, null);
  }
}
