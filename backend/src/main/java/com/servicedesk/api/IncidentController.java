package com.servicedesk.api;

import com.servicedesk.api.dto.AddCommentRequest;
import com.servicedesk.api.dto.AssignIncidentRequest;
import com.servicedesk.api.dto.AuditLogResponse;
import com.servicedesk.api.dto.CategorySuggestionResponse;
import com.servicedesk.api.dto.IncidentDraftRequest;
import com.servicedesk.api.dto.CancelIncidentRequest;
import com.servicedesk.api.dto.CloseIncidentRequest;
import com.servicedesk.api.dto.CommentResponse;
import com.servicedesk.api.dto.CreateIncidentRequest;
import com.servicedesk.api.dto.IncidentDetailResponse;
import com.servicedesk.api.dto.IncidentSummaryResponse;
import com.servicedesk.api.dto.OverridePriorityRequest;
import com.servicedesk.api.dto.PagedResponse;
import com.servicedesk.api.dto.ProvideInfoRequest;
import com.servicedesk.api.dto.RequestInfoRequest;
import com.servicedesk.api.dto.ResolveIncidentRequest;
import com.servicedesk.api.dto.SimilarIncidentResponse;
import com.servicedesk.application.IncidentApplicationService;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.repository.IncidentFilter;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.UserId;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

  private final IncidentApplicationService incidentService;

  public IncidentController(IncidentApplicationService incidentService) {
    this.incidentService = incidentService;
  }

  @PostMapping
  public ResponseEntity<IncidentDetailResponse> createIncident(
      @Valid @RequestBody CreateIncidentRequest request, Principal principal) {
    IncidentDetailResponse response = incidentService.createIncident(request, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public ResponseEntity<PagedResponse<IncidentSummaryResponse>> listIncidents(
      @RequestParam(required = false) IncidentStatus status,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) Category category,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) UUID reporterId,
      @RequestParam(required = false) SlaStatus slaStatus,
      @RequestParam(required = false) String searchText,
      @RequestParam(required = false) UUID serviceId,
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
            priority,
            category,
            assigneeId != null ? UserId.of(assigneeId) : null,
            reporterId != null ? UserId.of(reporterId) : null,
            slaStatus,
            searchText,
            serviceId,
            createdFrom != null ? createdFrom.atStartOfDay().toInstant(ZoneOffset.UTC) : null,
            // "To" is inclusive of the whole day.
            createdTo != null ? createdTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null);

    PagedResponse<IncidentSummaryResponse> response =
        incidentService.listIncidents(filter, page, size, principal.getName());
    return ResponseEntity.ok(response);
  }

  /** Suggests a category while the reporter is still typing. */
  @PostMapping("/suggest-category")
  public ResponseEntity<CategorySuggestionResponse> suggestCategory(
      @Valid @RequestBody IncidentDraftRequest draft) {
    return ResponseEntity.ok(incidentService.suggestCategory(draft));
  }

  /** Open tickets that look like the one being written (possible duplicates). */
  @PostMapping("/check-duplicates")
  public ResponseEntity<List<SimilarIncidentResponse>> checkDuplicates(
      @Valid @RequestBody IncidentDraftRequest draft, Principal principal) {
    return ResponseEntity.ok(incidentService.findPossibleDuplicates(draft, principal.getName()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<IncidentDetailResponse> getIncident(
      @PathVariable UUID id, Principal principal) {
    IncidentDetailResponse response = incidentService.getIncidentDetail(id, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/assign")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<IncidentDetailResponse> assignIncident(
      @PathVariable UUID id,
      @RequestBody(required = false) AssignIncidentRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.assignIncident(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/start")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<IncidentDetailResponse> startWork(
      @PathVariable UUID id, Principal principal) {
    IncidentDetailResponse response = incidentService.startWork(id, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/request-info")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<IncidentDetailResponse> requestInfo(
      @PathVariable UUID id,
      @Valid @RequestBody RequestInfoRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.requestInfo(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/provide-info")
  public ResponseEntity<IncidentDetailResponse> provideInfo(
      @PathVariable UUID id,
      @Valid @RequestBody ProvideInfoRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.provideInfo(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/resolve")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<IncidentDetailResponse> resolveIncident(
      @PathVariable UUID id,
      @Valid @RequestBody ResolveIncidentRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.resolveIncident(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/close")
  public ResponseEntity<IncidentDetailResponse> closeIncident(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) CloseIncidentRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.closeIncident(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<IncidentDetailResponse> cancelIncident(
      @PathVariable UUID id,
      @Valid @RequestBody CancelIncidentRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.cancelIncident(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}/priority")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<IncidentDetailResponse> overridePriority(
      @PathVariable UUID id,
      @Valid @RequestBody OverridePriorityRequest request,
      Principal principal) {
    IncidentDetailResponse response = incidentService.overridePriority(id, request, principal.getName());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/comments")
  public ResponseEntity<CommentResponse> addComment(
      @PathVariable UUID id,
      @Valid @RequestBody AddCommentRequest request,
      Principal principal) {
    CommentResponse response = incidentService.addComment(id, request, principal.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{id}/comments")
  public ResponseEntity<List<CommentResponse>> getComments(
      @PathVariable UUID id, Principal principal) {
    List<CommentResponse> comments = incidentService.getComments(id, principal.getName());
    return ResponseEntity.ok(comments);
  }

  @GetMapping("/{id}/audit-logs")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<List<AuditLogResponse>> getAuditLogs(
      @PathVariable UUID id, Principal principal) {
    List<AuditLogResponse> logs = incidentService.getAuditLogs(id, principal.getName());
    return ResponseEntity.ok(logs);
  }

  @GetMapping("/{id}/similar")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<List<SimilarIncidentResponse>> getSimilarIncidents(
      @PathVariable UUID id, Principal principal) {
    List<SimilarIncidentResponse> similar =
        incidentService.getSimilarIncidents(id, principal.getName());
    return ResponseEntity.ok(similar);
  }
}
