package com.servicedesk.api.dto;

import jakarta.validation.constraints.Size;

/** A ticket being written; used for category suggestions and duplicate checks before submit. */
public record IncidentDraftRequest(
    @Size(max = 200, message = "Title must not exceed 200 characters") String title,
    @Size(max = 10000, message = "Description must not exceed 10000 characters")
        String description) {}
