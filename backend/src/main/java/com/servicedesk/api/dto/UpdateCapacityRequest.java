package com.servicedesk.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** How many open tickets a technician may hold, and whether they lead the team. */
public record UpdateCapacityRequest(
    @NotNull(message = "Set a ticket limit")
        @Min(value = 1, message = "The limit must be at least 1")
        @Max(value = 50, message = "The limit can be at most 50")
        Integer maxActiveTickets,
    boolean teamLead) {}
