package com.servicedesk.application;

import com.servicedesk.api.dto.AuthResponse;
import com.servicedesk.api.dto.CreateUserRequest;
import com.servicedesk.api.dto.LoginRequest;
import com.servicedesk.api.dto.RegisterRequest;
import com.servicedesk.api.dto.TechnicianResponse;
import com.servicedesk.api.dto.UserAccountResponse;
import com.servicedesk.api.dto.UserProfileResponse;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.entity.UserSkillEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserApplicationService {

  private final SpringDataUserJpaRepository userJpaRepository;
  private final SpringDataIncidentJpaRepository incidentJpaRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final GoogleIdTokenVerifier googleVerifier;
  private final Clock clock;
  private boolean registrationEnabled = true;

  @Value("${app.auth.registration-enabled:true}")
  void setRegistrationEnabled(boolean registrationEnabled) {
    this.registrationEnabled = registrationEnabled;
  }

  public boolean registrationEnabled() {
    return registrationEnabled;
  }

  /** Self-service sign-up. New accounts are always employees. */
  public AuthResponse register(RegisterRequest request) {
    if (!registrationEnabled) {
      throw new UnauthorizedOperationException("Self-registration is turned off");
    }
    UserAccountResponse created =
        createAccount(
            request.username(), request.fullName(), request.email(), Role.EMPLOYEE, request.password());
    UserEntity user = userJpaRepository.findById(created.id()).orElseThrow();
    return issueSession(user);
  }

  public UserApplicationService(
      SpringDataUserJpaRepository userJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      GoogleIdTokenVerifier googleVerifier,
      Clock clock) {
    this.userJpaRepository = userJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.googleVerifier = googleVerifier;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    UserEntity user =
        userJpaRepository
            .findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

    if (!user.isActive()
        || user.getPasswordHash() == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid username or password");
    }
    return issueSession(user);
  }

  public String googleClientId() {
    return googleVerifier.getClientId();
  }

  /**
   * Signs in with a Google ID token. Existing accounts are matched by Google id, then by verified
   * email; anyone else gets a new employee account. Roles are changed later by root.
   */
  public AuthResponse loginWithGoogle(String credential) {
    if (!googleVerifier.isEnabled()) {
      throw new IllegalArgumentException("Google sign-in is not enabled");
    }
    GoogleIdentity identity = googleVerifier.verify(credential);

    UserEntity user =
        userJpaRepository
            .findByGoogleSub(identity.subject())
            .or(() -> userJpaRepository.findByEmailIgnoreCase(identity.email()))
            .orElse(null);

    if (user == null) {
      String name = identity.name() != null && !identity.name().isBlank() ? identity.name() : identity.email();
      user =
          new UserEntity(
              UUID.randomUUID(),
              uniqueUsername(identity.email()),
              null,
              truncate(name, 100),
              identity.email(),
              Role.EMPLOYEE,
              true,
              clock.instant(),
              clock.instant());
      user.setGoogleSub(identity.subject());
      user = userJpaRepository.save(user);
    } else {
      if (user.getGoogleSub() == null) {
        user.setGoogleSub(identity.subject());
      } else if (!user.getGoogleSub().equals(identity.subject())) {
        // Same email, different Google account: never silently take over an existing user.
        throw new BadCredentialsException("This email is linked to a different Google account.");
      }
      if (!user.isActive()) {
        throw new BadCredentialsException("Your account has been disabled.");
      }
    }
    return issueSession(user);
  }

  // --- Root-only user management ---

  @Transactional(readOnly = true)
  public List<UserAccountResponse> listUsers() {
    return userJpaRepository.findAllByOrderByRoleAscUsernameAsc().stream()
        .map(this::toAccountResponse)
        .toList();
  }

  public UserAccountResponse createUser(CreateUserRequest request, String actorUsername) {
    requireMayGrant(actorRole(actorUsername), request.role());
    return createAccount(
        request.username(), request.fullName(), request.email(), request.role(), request.password());
  }

  private UserAccountResponse createAccount(
      String username, String fullName, String rawEmail, Role role, String password) {
    if (userJpaRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("That username is already taken");
    }
    String email = rawEmail == null || rawEmail.isBlank() ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
    if (email != null && userJpaRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new IllegalArgumentException("That email is already used by another account");
    }
    UserEntity user =
        new UserEntity(
            UUID.randomUUID(),
            username,
            passwordEncoder.encode(password),
            fullName.trim(),
            email,
            role,
            true,
            clock.instant(),
            clock.instant());
    return toAccountResponse(userJpaRepository.save(user));
  }

  public UserAccountResponse updateRole(UUID userId, Role role, String actorUsername) {
    UserEntity user = loadManageableUser(userId, actorUsername);
    requireMayGrant(actorRole(actorUsername), role);
    if (user.getRole() == Role.TECHNICIAN && role != Role.TECHNICIAN) {
      // Skills only mean something for technicians.
      user.getSkills().clear();
    }
    user.setRole(role);
    user.setUpdatedAt(clock.instant());
    return toAccountResponse(userJpaRepository.save(user));
  }

  public UserAccountResponse setActive(UUID userId, boolean active, String actorUsername) {
    UserEntity user = loadManageableUser(userId, actorUsername);
    user.setActive(active);
    user.setUpdatedAt(clock.instant());
    return toAccountResponse(userJpaRepository.save(user));
  }

  private UserEntity loadManageableUser(UUID userId, String actorUsername) {
    UserEntity user =
        userJpaRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    if (user.getRole() == Role.ROOT || user.getUsername().equals(actorUsername)) {
      throw new UnauthorizedOperationException("You can't change this account");
    }
    if (actorRole(actorUsername) != Role.ROOT && user.getRole().hasAdminRights()) {
      throw new UnauthorizedOperationException("Only root can change administrator accounts");
    }
    return user;
  }

  /**
   * Administrators manage employees and technicians; only root can create or promote
   * administrators, and nobody can create a second root.
   */
  private static void requireMayGrant(Role actor, Role target) {
    if (target == Role.ROOT) {
      throw new IllegalArgumentException("There can only be one root account");
    }
    if (target == Role.ADMINISTRATOR && actor != Role.ROOT) {
      throw new UnauthorizedOperationException("Only root can grant administrator access");
    }
  }

  private Role actorRole(String username) {
    return userJpaRepository
        .findByUsername(username)
        .map(UserEntity::getRole)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
  }

  private AuthResponse issueSession(UserEntity user) {
    String token =
        jwtTokenProvider.generateToken(UserId.of(user.getId()), user.getUsername(), user.getRole());
    return new AuthResponse(token, toProfileResponse(user));
  }

  private String uniqueUsername(String email) {
    String base = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
    if (base.length() < 3) {
      base = "user";
    }
    base = truncate(base, 44);
    String candidate = base;
    for (int i = 2; userJpaRepository.existsByUsername(candidate); i++) {
      candidate = base + "-" + i;
    }
    return candidate;
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  private UserAccountResponse toAccountResponse(UserEntity user) {
    return new UserAccountResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRole(),
        user.isActive(),
        user.getPasswordHash() != null,
        user.getGoogleSub() != null,
        user.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public UserProfileResponse getUserProfile(String username) {
    UserEntity user =
        userJpaRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    return toProfileResponse(user);
  }

  @Transactional(readOnly = true)
  public List<TechnicianResponse> listTechnicians() {
    return userJpaRepository.findByRole(Role.TECHNICIAN).stream()
        .map(this::toTechnicianResponse)
        .toList();
  }

  public TechnicianResponse updateTechnicianSkills(UUID technicianId, Set<Category> newSkills) {
    UserEntity user =
        userJpaRepository
            .findById(technicianId)
            .orElseThrow(() -> new UserNotFoundException("Technician not found: " + technicianId));

    if (user.getRole() != Role.TECHNICIAN) {
      throw new IllegalArgumentException("Skills can only be updated for technicians");
    }
    if (newSkills == null || newSkills.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Skills must contain valid categories");
    }
    // Retain existing rows to avoid insert-before-delete unique constraint failures.
    user.getSkills().removeIf(skill -> !newSkills.contains(skill.getCategory()));
    Set<Category> existing = user.getSkills().stream()
        .map(UserSkillEntity::getCategory).collect(Collectors.toSet());
    for (Category category : newSkills) {
      if (!existing.contains(category)) {
        user.getSkills().add(new UserSkillEntity(UUID.randomUUID(), user, category));
      }
    }

    UserEntity saved = userJpaRepository.save(user);
    return toTechnicianResponse(saved);
  }

  private UserProfileResponse toProfileResponse(UserEntity user) {
    Set<Category> skills =
        user.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());
    return new UserProfileResponse(
        user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(), skills);
  }

  private TechnicianResponse toTechnicianResponse(UserEntity tech) {
    Set<Category> skills =
        tech.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());

    int activeTickets =
        incidentJpaRepository.countByAssigneeIdAndStatusNotIn(
            tech.getId(), Set.of(IncidentStatus.CLOSED, IncidentStatus.CANCELLED));

    return new TechnicianResponse(
        tech.getId(),
        tech.getUsername(),
        tech.getFullName(),
        tech.getEmail(),
        skills,
        activeTickets,
        tech.isActive());
  }
}
