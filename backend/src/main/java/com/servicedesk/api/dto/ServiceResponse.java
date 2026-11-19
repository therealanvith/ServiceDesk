package com.servicedesk.api.dto;

import com.servicedesk.domain.service.BusinessCriticality;
import java.util.UUID;

public record ServiceResponse(
    UUID id, String name, String description, BusinessCriticality criticality, boolean active) {}
