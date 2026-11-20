package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.UpdateKind;
import com.servicedesk.domain.user.Role;
import java.time.Instant;
import java.util.UUID;

/** One entry in a ticket's Updates thread. */
public record CommentResponse(
    UUID id,
    UUID incidentId,
    UUID authorId,
    String authorName,
    String commentText,
    boolean isInternal,
    Instant createdAt,
    UpdateKind kind,
    Role authorRole) {}
