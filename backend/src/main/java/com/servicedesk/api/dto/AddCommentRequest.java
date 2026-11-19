package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
    @NotBlank(message = "Comment text is required")
    @Size(max = 10000, message = "Comment must not exceed 10000 characters")
    String commentText,
    boolean isInternal) {}
