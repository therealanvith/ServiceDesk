package com.servicedesk.infrastructure.config;

import com.servicedesk.domain.priority.PriorityCalculationStrategy;
import com.servicedesk.domain.priority.WeightedPriorityStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of the active business policies, shared by every use case that needs them. */
@Configuration
public class DomainPolicyConfig {

  /** Priority from impact, urgency and service criticality; also used for the form's preview. */
  @Bean
  public PriorityCalculationStrategy priorityCalculationStrategy() {
    return new WeightedPriorityStrategy();
  }
}
