package com.servicedesk.domain.repository;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** Domain port for incident persistence and retrieval. */
public interface IncidentRepository {

  Optional<Incident> findById(IncidentId id);

  Optional<Incident> findByIncidentNumber(String incidentNumber);

  Incident save(Incident incident);

  List<Incident> findAll();

  List<Incident> findWithFilters(IncidentFilter filter);

  int countActiveByTechnicianId(UserId technicianId);
}
