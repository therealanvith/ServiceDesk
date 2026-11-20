package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import java.time.Instant;
import java.util.UUID;

public record IncidentSummaryResponse(
    UUID id,
    String incidentNumber,
    String title,
    Category category,
    IncidentStatus status,
    Priority priority,
    String reporterName,
    String assigneeName,
    SlaStatus slaStatus,
    Instant createdAt,
    String serviceName) {}
