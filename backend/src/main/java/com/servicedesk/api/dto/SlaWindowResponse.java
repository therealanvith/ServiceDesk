package com.servicedesk.api.dto;

import com.servicedesk.domain.sla.SlaStatus;
import java.time.Instant;

public record SlaWindowResponse(
    Instant responseDeadline,
    Instant resolutionDeadline,
    SlaStatus slaStatus,
    long pausedSeconds) {}
