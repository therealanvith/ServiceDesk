package com.servicedesk.api;

import com.servicedesk.api.dto.AuthConfigResponse;
import com.servicedesk.api.dto.AuthResponse;
import com.servicedesk.api.dto.GoogleSignInRequest;
import com.servicedesk.api.dto.LoginRequest;
import com.servicedesk.api.dto.RegisterRequest;
import com.servicedesk.application.UserApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final UserApplicationService userService;

  public AuthController(UserApplicationService userService) {
    this.userService = userService;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    AuthResponse response = userService.login(request);
    return ResponseEntity.ok(response);
  }

  /** Employees can create their own account; staff roles are granted by an administrator. */
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
  }

  @PostMapping("/google")
  public ResponseEntity<AuthResponse> loginWithGoogle(
      @Valid @RequestBody GoogleSignInRequest request) {
    return ResponseEntity.ok(userService.loginWithGoogle(request.credential()));
  }

  /** Lets the login page decide at runtime which sign-in options to show. */
  @GetMapping("/config")
  public ResponseEntity<AuthConfigResponse> config() {
    return ResponseEntity.ok(
        new AuthConfigResponse(userService.googleClientId(), userService.registrationEnabled()));
  }
}
