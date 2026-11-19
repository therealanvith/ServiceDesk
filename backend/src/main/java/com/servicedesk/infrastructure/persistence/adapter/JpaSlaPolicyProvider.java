package com.servicedesk.infrastructure.persistence.adapter;

import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaPolicyProvider;
import com.servicedesk.infrastructure.persistence.repository.SpringDataSlaPolicyJpaRepository;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** Reads the administrator-configured SLA targets, falling back to the defaults if unset. */
@Component
public class JpaSlaPolicyProvider implements SlaPolicyProvider {

  private final SpringDataSlaPolicyJpaRepository repository;

  public JpaSlaPolicyProvider(SpringDataSlaPolicyJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public SlaPolicy policyFor(Priority priority) {
    return repository
        .findById(priority)
        .map(
            p ->
                new SlaPolicy(
                    priority,
                    Duration.ofMinutes(p.getResponseMinutes()),
                    Duration.ofMinutes(p.getResolutionMinutes())))
        .orElseGet(() -> SlaPolicy.defaultFor(priority));
  }
}
