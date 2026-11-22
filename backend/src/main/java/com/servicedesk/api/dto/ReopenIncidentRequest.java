package com.servicedesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the fix didn't work; shown to the technician. */
public record ReopenIncidentRequest(
    @NotBlank(message = "Say what is still wrong")
        @Size(max = 2000, message = "Keep it under 2000 characters")
        String reason) {}
