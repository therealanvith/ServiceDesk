package com.servicedesk.domain.sla;

import com.servicedesk.domain.priority.Priority;

/** Domain port supplying the SLA targets currently in force for a priority. */
@FunctionalInterface
public interface SlaPolicyProvider {

  SlaPolicy policyFor(Priority priority);

  /** Built-in targets; used in tests and whenever no configuration is stored. */
  static SlaPolicyProvider defaults() {
    return SlaPolicy::defaultFor;
  }
}
