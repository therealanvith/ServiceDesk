package com.servicedesk.domain.repository;

import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.incident.IncidentId;
import java.util.List;

/**
 * Domain port for immutable audit log ledger records.
 */
public interface AuditLogRepository {

  AuditLog save(AuditLog auditLog);

  List<AuditLog> findByIncidentId(IncidentId incidentId);

  /** Most recent entries across all incidents, newest first. */
  List<AuditLog> findRecent(int limit);
}
