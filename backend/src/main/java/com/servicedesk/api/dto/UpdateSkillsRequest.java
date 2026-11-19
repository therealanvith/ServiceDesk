package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UpdateSkillsRequest(
    @NotNull(message = "Skills set must not be null") Set<@NotNull Category> skills) {}
