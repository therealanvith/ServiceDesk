package com.servicedesk.api;

import com.servicedesk.api.dto.ChangePasswordRequest;
import com.servicedesk.api.dto.CreateUserRequest;
import com.servicedesk.api.dto.GoogleSignInRequest;
import com.servicedesk.api.dto.TechnicianResponse;
import com.servicedesk.api.dto.UpdateCapacityRequest;
import com.servicedesk.api.dto.UpdateEmailRequest;
import com.servicedesk.api.dto.UpdateSkillsRequest;
import com.servicedesk.api.dto.UpdateUserActiveRequest;
import com.servicedesk.api.dto.UpdateUserRoleRequest;
import com.servicedesk.api.dto.UserAccountResponse;
import com.servicedesk.api.dto.UserProfileResponse;
import com.servicedesk.application.UserApplicationService;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.user.Role;
import jakarta.validation.Valid;
import java.security.Principal;
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

  @PostMapping("/me/password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, Principal principal) {
    userService.changePassword(
        principal.getName(), request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  /** Links Google sign-in to the signed-in account (proves ownership of both). */
  @PostMapping("/me/google")
  public ResponseEntity<UserProfileResponse> linkGoogle(
      @Valid @RequestBody GoogleSignInRequest request, Principal principal) {
    return ResponseEntity.ok(userService.linkGoogle(principal.getName(), request.credential()));
  }

  @GetMapping("/technicians")
  @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<List<TechnicianResponse>> listTechnicians(Principal principal) {
    // Technicians may see the list only when they lead the team (to reassign tickets).
    UserProfileResponse caller = userService.getUserProfile(principal.getName());
    if (caller.role() == Role.TECHNICIAN && !caller.teamLead()) {
      throw new UnauthorizedOperationException("Only a team lead can see the technician list");
    }
    List<TechnicianResponse> techs = userService.listTechnicians();
    return ResponseEntity.ok(techs);
  }

  @PutMapping("/{id}/skills")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<TechnicianResponse> updateSkills(
      @PathVariable UUID id, @Valid @RequestBody UpdateSkillsRequest request) {
    TechnicianResponse updated = userService.updateTechnicianSkills(id, request.skills());
    return ResponseEntity.ok(updated);
  }

  @PutMapping("/{id}/capacity")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<TechnicianResponse> updateCapacity(
      @PathVariable UUID id, @Valid @RequestBody UpdateCapacityRequest request) {
    return ResponseEntity.ok(
        userService.updateTechnicianCapacity(id, request.maxActiveTickets(), request.teamLead()));
  }

  // --- Account management (administrators and root) ---

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<List<UserAccountResponse>> listUsers() {
    return ResponseEntity.ok(userService.listUsers());
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<UserAccountResponse> createUser(
      @Valid @RequestBody CreateUserRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.createUser(request, principal.getName()));
  }

  @PutMapping("/{id}/email")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<UserAccountResponse> updateEmail(
      @PathVariable UUID id, @Valid @RequestBody UpdateEmailRequest request, Principal principal) {
    return ResponseEntity.ok(userService.updateEmail(id, request.email(), principal.getName()));
  }

  @PutMapping("/{id}/role")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<UserAccountResponse> updateRole(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserRoleRequest request,
      Principal principal) {
    return ResponseEntity.ok(userService.updateRole(id, request.role(), principal.getName()));
  }

  @PutMapping("/{id}/active")
  @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'ROOT')")
  public ResponseEntity<UserAccountResponse> setActive(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateUserActiveRequest request,
      Principal principal) {
    return ResponseEntity.ok(userService.setActive(id, request.active(), principal.getName()));
  }
}
