package com.servicedesk.api.dto;

/** A technician's own queue at a glance. */
public record WorkloadResponse(long active, long inProgress, long waitingOnReporter, long atRisk, long breached, long resolvedThisWeek) {}
