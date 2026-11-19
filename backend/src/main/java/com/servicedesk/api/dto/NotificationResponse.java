package com.servicedesk.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String type,
    String title,
    String message,
    UUID incidentId,
    String incidentNumber,
    boolean read,
    Instant createdAt) {}
