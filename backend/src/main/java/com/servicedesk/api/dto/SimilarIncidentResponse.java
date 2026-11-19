package com.servicedesk.api.dto;

import java.util.UUID;

/** A possibly-related ticket: an open one may be a duplicate, a closed one may hold a known fix. */
public record SimilarIncidentResponse(
    String incidentNumber,
    String title,
    double similarityScore,
    String status,
    String resolutionSummary,
    UUID id,
    boolean open) {}
