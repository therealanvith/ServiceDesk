package com.servicedesk.api;

import com.servicedesk.api.dto.PriorityPreviewResponse;
import com.servicedesk.api.dto.ServiceRequest;
import com.servicedesk.api.dto.ServiceResponse;
import com.servicedesk.api.dto.SlaPolicyResponse;
import com.servicedesk.api.dto.UpdateSlaPolicyRequest;
import com.servicedesk.application.CatalogApplicationService;
import com.servicedesk.domain.incident.Impact;
import com.servicedesk.domain.incident.Urgency;
import com.servicedesk.domain.priority.Priority;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** IT service catalogue and SLA targets. Everyone can read services; only admins change them. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

  private final CatalogApplicationService catalog;

  public CatalogController(CatalogApplicationService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/services")
  public ResponseEntity<List<ServiceResponse>> listServices(
      @RequestParam(defaultValue = "false") boolean includeRetired) {
    return ResponseEntity.ok(catalog.listServices(includeRetired));
  }

  @PostMapping("/services")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody ServiceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(catalog.createService(request));
  }

  @PutMapping("/services/{id}")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<ServiceResponse> updateService(
      @PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
    return ResponseEntity.ok(catalog.updateService(id, request));
  }

  @GetMapping("/priority-preview")
  public ResponseEntity<PriorityPreviewResponse> previewPriority(
      @RequestParam Impact impact,
      @RequestParam Urgency urgency,
      @RequestParam(required = false) UUID serviceId) {
    return ResponseEntity.ok(catalog.previewPriority(impact, urgency, serviceId));
  }

  @GetMapping("/sla-policies")
  public ResponseEntity<List<SlaPolicyResponse>> listSlaPolicies() {
    return ResponseEntity.ok(catalog.listSlaPolicies());
  }

  @PutMapping("/sla-policies/{priority}")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<SlaPolicyResponse> updateSlaPolicy(
      @PathVariable Priority priority, @Valid @RequestBody UpdateSlaPolicyRequest request) {
    return ResponseEntity.ok(catalog.updateSlaPolicy(priority, request));
  }
}
