package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.service.BusinessCriticality;
import java.util.Map;

/** The priority a ticket would get, and the full impact x urgency grid for that service. */
public record PriorityPreviewResponse(
    Priority priority,
    BusinessCriticality criticality,
    Map<Impact, Map<Urgency, Priority>> matrix) {}
