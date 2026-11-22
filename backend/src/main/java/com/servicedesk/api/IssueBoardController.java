package com.servicedesk.api;

import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.PublicIssueResponse;
import com.servicedesk.application.IncidentApplicationService;
import com.servicedesk.application.IssueBoardService;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.repository.IncidentFilter;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only "All issues" list, available to every signed-in user. */
@RestController
@RequestMapping("/api/v1/issues")
public class IssueBoardController {

  private final IssueBoardService issues;
  private final IncidentApplicationService incidents;

  public IssueBoardController(IssueBoardService issues, IncidentApplicationService incidents) {
    this.issues = issues;
    this.incidents = incidents;
  }

  @GetMapping
  public ResponseEntity<PagedResponse<PublicIssueResponse>> list(
      @RequestParam(required = false) IncidentStatus status,
      @RequestParam(required = false) Category category,
      @RequestParam(required = false) UUID serviceId,
      @RequestParam(required = false) String searchText,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Principal principal) {
    IncidentFilter filter =
        new IncidentFilter(
            status,
            null,
            category,
            null,
            null,
            null,
            searchText,
            serviceId,
            createdFrom != null ? createdFrom.atStartOfDay().toInstant(ZoneOffset.UTC) : null,
            createdTo != null
                ? createdTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                : null);
    return ResponseEntity.ok(issues.list(filter, page, size, principal.getName()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PublicIssueResponse> get(@PathVariable UUID id, Principal principal) {
    return ResponseEntity.ok(issues.get(id, principal.getName()));
  }

  /** "This affects me too": counts the caller on the existing issue; no new ticket is opened. */
  @PostMapping("/{id}/affected")
  public ResponseEntity<PublicIssueResponse> affected(@PathVariable UUID id, Principal principal) {
    incidents.markAffected(id, principal.getName());
    return ResponseEntity.ok(issues.get(id, principal.getName()));
  }

  @DeleteMapping("/{id}/affected")
  public ResponseEntity<PublicIssueResponse> notAffected(
      @PathVariable UUID id, Principal principal) {
    incidents.unmarkAffected(id, principal.getName());
    return ResponseEntity.ok(issues.get(id, principal.getName()));
  }
}
