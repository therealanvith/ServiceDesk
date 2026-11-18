package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCommentRequest(
    @NotBlank(message = "Comment text is required") String commentText,
    boolean isInternal) {}
