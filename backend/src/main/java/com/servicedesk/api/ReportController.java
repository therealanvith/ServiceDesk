package com.servicedesk.api;

import com.servicedesk.api.dto.AuditLogResponse;
import com.servicedesk.api.dto.ReportResponse;
import com.servicedesk.api.dto.WorkloadResponse;
import com.servicedesk.application.IncidentApplicationService;
import com.servicedesk.application.ReportApplicationService;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

  private final ReportApplicationService reports;
  private final IncidentApplicationService incidents;

  public ReportController(ReportApplicationService reports, IncidentApplicationService incidents) {
    this.reports = reports;
    this.incidents = incidents;
  }

  @GetMapping("/overview")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<ReportResponse> overview(Principal principal) {
    return ResponseEntity.ok(reports.overview(principal.getName()));
  }

  @GetMapping("/my-workload")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<WorkloadResponse> myWorkload(Principal principal) {
    return ResponseEntity.ok(reports.myWorkload(principal.getName()));
  }

  @GetMapping("/audit-logs")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<List<AuditLogResponse>> auditLogs(
      @RequestParam(defaultValue = "200") int limit, Principal principal) {
    return ResponseEntity.ok(incidents.getRecentAuditLogs(limit, principal.getName()));
  }
}
