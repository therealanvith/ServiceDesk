package com.servicedesk.api.dto;

import java.util.UUID;

public record UserSummaryResponse(UUID id, String fullName, String email) {}
