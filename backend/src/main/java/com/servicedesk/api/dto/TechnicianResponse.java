package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;
import java.util.Set;
import java.util.UUID;

public record TechnicianResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    Set<Category> skills,
    int activeTicketsCount,
    boolean available,
    int maxActiveTickets,
    boolean teamLead) {}
