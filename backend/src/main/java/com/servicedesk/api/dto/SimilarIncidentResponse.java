package com.servicedesk.api.dto;

public record SimilarIncidentResponse(
    String incidentNumber,
    String title,
    double similarityScore,
    String status,
    String resolutionSummary) {}
