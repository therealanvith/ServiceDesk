package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.Category;

/** One incident category and whether reporters can currently choose it. */
public record CategorySettingResponse(Category category, boolean active) {}
