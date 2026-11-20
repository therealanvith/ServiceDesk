package com.servicedesk.api.dto;

import com.servicedesk.domain.priority.Priority;

public record SlaPolicyResponse(Priority priority, long responseMinutes, long resolutionMinutes) {}
