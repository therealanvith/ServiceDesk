package com.servicedesk.api;

import com.servicedesk.api.dto.AssignmentConfigResponse;
import com.servicedesk.api.dto.SlaEvaluationSummaryResponse;
import com.servicedesk.api.dto.UpdateAssignmentConfigRequest;
import com.servicedesk.application.IncidentApplicationService;
import com.servicedesk.application.SlaScheduledEvaluator;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
public class AdminController {

  private final IncidentApplicationService incidentService;
  private final SlaScheduledEvaluator slaEvaluator;

  public AdminController(
      IncidentApplicationService incidentService, SlaScheduledEvaluator slaEvaluator) {
    this.incidentService = incidentService;
    this.slaEvaluator = slaEvaluator;
  }

  @GetMapping("/config/assignment")
  public ResponseEntity<AssignmentConfigResponse> getAssignmentConfig() {
    String currentStrategy = incidentService.getActiveAssignmentStrategyName();
    return ResponseEntity.ok(new AssignmentConfigResponse(currentStrategy));
  }

  @PutMapping("/config/assignment")
  public ResponseEntity<AssignmentConfigResponse> updateAssignmentConfig(
      @Valid @RequestBody UpdateAssignmentConfigRequest request) {
    incidentService.updateAssignmentStrategy(request.strategy());
    String updated = incidentService.getActiveAssignmentStrategyName();
    return ResponseEntity.ok(new AssignmentConfigResponse(updated));
  }

  @PostMapping("/sla/evaluate")
  public ResponseEntity<SlaEvaluationSummaryResponse> triggerSlaEvaluation() {
    SlaEvaluationSummaryResponse summary = slaEvaluator.evaluateAllSla();
    return ResponseEntity.ok(summary);
  }
}
