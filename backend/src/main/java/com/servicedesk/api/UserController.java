package com.servicedesk.api;

import com.servicedesk.api.dto.TechnicianResponse;
import com.servicedesk.api.dto.UpdateSkillsRequest;
import com.servicedesk.api.dto.UserProfileResponse;
import com.servicedesk.application.UserApplicationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserApplicationService userService;

  public UserController(UserApplicationService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public ResponseEntity<UserProfileResponse> getCurrentUser(Principal principal) {
    UserProfileResponse profile = userService.getUserProfile(principal.getName());
    return ResponseEntity.ok(profile);
  }

  @GetMapping("/technicians")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR')")
  public ResponseEntity<List<TechnicianResponse>> listTechnicians() {
    List<TechnicianResponse> techs = userService.listTechnicians();
    return ResponseEntity.ok(techs);
  }

  @PutMapping("/{id}/skills")
  @PreAuthorize("hasRole('ADMINISTRATOR')")
  public ResponseEntity<TechnicianResponse> updateSkills(
      @PathVariable UUID id, @Valid @RequestBody UpdateSkillsRequest request) {
    TechnicianResponse updated = userService.updateTechnicianSkills(id, request.skills());
    return ResponseEntity.ok(updated);
  }
}
