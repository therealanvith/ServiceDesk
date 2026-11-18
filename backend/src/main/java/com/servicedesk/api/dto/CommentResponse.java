package com.servicedesk.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    UUID incidentId,
    UUID authorId,
    String authorName,
    String commentText,
    boolean isInternal,
    Instant createdAt) {}
