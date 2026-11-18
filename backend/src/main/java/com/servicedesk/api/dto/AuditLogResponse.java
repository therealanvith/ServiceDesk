package com.servicedesk.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID incidentId,
    UUID actorId,
    String actorName,
    String action,
    String oldValue,
    String newValue,
    String remarks,
    Instant timestamp) {}
