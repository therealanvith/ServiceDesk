package com.servicedesk.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Closing confirms the fix; the reporter may rate the support they received. */
public record CloseIncidentRequest(
    @Size(max = 2000, message = "Remarks must not exceed 2000 characters") String remarks,
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    Integer rating) {}
