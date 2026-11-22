package com.servicedesk.domain.priority;

import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.service.BusinessCriticality;

/**
 * Strategy interface for calculating incident priority from impact, urgency and the business
 * criticality of the affected service.
 */
public interface PriorityCalculationStrategy {
  Priority calculatePriority(Impact impact, Urgency urgency);

  /**
   * Business-aware variant. Strategies that ignore criticality inherit this default, so existing
   * strategies keep working unchanged.
   */
  default Priority calculatePriority(
      Impact impact, Urgency urgency, BusinessCriticality criticality) {
    return calculatePriority(impact, urgency);
  }

  /**
   * Also takes the number of people affected. Strategies that ignore it inherit this default, so
   * existing strategies keep working unchanged.
   */
  default Priority calculatePriority(
      Impact impact, Urgency urgency, BusinessCriticality criticality, int affectedUsers) {
    return calculatePriority(impact, urgency, criticality);
  }
}
