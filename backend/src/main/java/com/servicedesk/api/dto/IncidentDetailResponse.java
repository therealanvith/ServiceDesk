package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import java.time.Instant;
import java.util.UUID;

public record IncidentDetailResponse(
    UUID id,
    String incidentNumber,
    String title,
    String description,
    Category category,
    IncidentStatus status,
    Priority priority,
    Impact impact,
    Urgency urgency,
    UserSummaryResponse reporter,
    UserSummaryResponse assignee,
    SlaWindowResponse slaWindow,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt,
    ServiceResponse service,
    Instant respondedAt,
    Instant escalatedAt,
    Integer feedbackRating,
    String feedbackComment) {}
