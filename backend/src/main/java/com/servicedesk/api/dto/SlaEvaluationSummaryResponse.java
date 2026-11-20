package com.servicedesk.api.dto;

import java.time.Instant;

public record SlaEvaluationSummaryResponse(
    int evaluatedCount, int breachedCount, Instant evaluatedAt) {}
