package com.servicedesk.domain.priority;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;

/**
 * Strategy interface for calculating incident priority from impact and urgency.
 */
public interface PriorityCalculationStrategy {

  Priority calculatePriority(Impact impact, Urgency urgency);
}
