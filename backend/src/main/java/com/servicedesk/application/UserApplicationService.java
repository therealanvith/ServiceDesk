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
import com.servicedesk.domain.notification.NotificationChannel;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.live.LiveEventBroadcaster;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.entity.UserSkillEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier;
import com.servicedesk.infrastructure.security.GoogleIdTokenVerifier.GoogleIdentity;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
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
  private final List<NotificationChannel> notificationChannels;
  private final LoginAttemptGuard loginAttempts;
  private final LiveEventBroadcaster liveEvents;
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
            request.username(),
            request.fullName(),
            request.email(),
            Role.EMPLOYEE,
            request.password());
    UserEntity user = userJpaRepository.findById(created.id()).orElseThrow();
    announceNewUser(user, "signed up");
    return issueSession(user);
  }

  /** Tells administrators someone joined so they can review the new account's role. */
  private void announceNewUser(UserEntity user, String how) {
    for (Role role : List.of(Role.ADMINISTRATOR, Role.ROOT)) {
      for (UserEntity admin : userJpaRepository.findByRoleAndActiveTrue(role)) {
        NotificationDispatch.afterCommit(
            notificationChannels,
            NotificationMessage.of(
                UserId.of(admin.getId()),
                null,
                "NEW_USER",
                "New user: " + user.getFullName(),
                user.getFullName()
                    + " ("
                    + user.getUsername()
                    + ") "
                    + how
                    + " and starts as an employee. Change their role on the Users page if needed."));
      }
    }
  }

  public UserApplicationService(
      SpringDataUserJpaRepository userJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      GoogleIdTokenVerifier googleVerifier,
      Clock clock,
      List<NotificationChannel> notificationChannels,
      LoginAttemptGuard loginAttempts,
      LiveEventBroadcaster liveEvents) {
    this.liveEvents = liveEvents;
    this.loginAttempts = loginAttempts;
    this.notificationChannels = notificationChannels;
    this.userJpaRepository = userJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.googleVerifier = googleVerifier;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    // A locked username gets the same answer as a wrong password, so lockouts leak nothing.
    if (loginAttempts.isLocked(request.username())) {
      throw new BadCredentialsException("Invalid username or password");
    }
    UserEntity user = userJpaRepository.findByUsername(request.username()).orElse(null);
    if (user == null
        || !user.isActive()
        || user.getPasswordHash() == null
        || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      loginAttempts.recordFailure(request.username());
      throw new BadCredentialsException("Invalid username or password");
    }
    loginAttempts.recordSuccess(request.username());
    return issueSession(user);
  }

  public String googleClientId() {
    return googleVerifier.getClientId();
  }

  /**
   * Signs in with a Google ID token. Existing accounts are matched by Google id, then by verified
   * email; anyone else gets a new employee account. Roles are changed later by root.
   */
  /**
   * Signs in with a Google ID token.
   *
   * <p>Accounts are matched only by Google's stable subject id. An existing account is never taken
   * over just because it has the same email: emails entered at registration are not verified, so
   * someone could register a victim's address first. Existing users link Google from "Your account"
   * while signed in ({@link #linkGoogle}).
   */
  public AuthResponse loginWithGoogle(String credential) {
    if (!googleVerifier.isEnabled()) {
      throw new IllegalArgumentException("Google sign-in is not enabled");
    }
    GoogleIdentity identity = googleVerifier.verify(credential);

    UserEntity user = userJpaRepository.findByGoogleSub(identity.subject()).orElse(null);
    if (user != null) {
      if (!user.isActive()) {
        throw new BadCredentialsException("Your account has been disabled.");
      }
      return issueSession(user);
    }

    if (userJpaRepository.findByEmailIgnoreCase(identity.email()).isPresent()) {
      throw new BadCredentialsException(
          "An account with this email already exists. Sign in with your username and password,"
              + " then link Google from Your account.");
    }
    if (!registrationEnabled) {
      throw new UnauthorizedOperationException(
          "New accounts are created by an administrator. Ask IT to add you.");
    }

    String name =
        identity.name() != null && !identity.name().isBlank() ? identity.name() : identity.email();
    UserEntity created =
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
    created.setGoogleSub(identity.subject());
    UserEntity saved = userJpaRepository.save(created);
    announceNewUser(saved, "signed in with Google for the first time");
    return issueSession(saved);
  }

  /** Links a Google identity to the signed-in account; proves ownership of both. */
  public UserProfileResponse linkGoogle(String username, String credential) {
    if (!googleVerifier.isEnabled()) {
      throw new IllegalArgumentException("Google sign-in is not enabled");
    }
    GoogleIdentity identity = googleVerifier.verify(credential);
    UserEntity user =
        userJpaRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    userJpaRepository
        .findByGoogleSub(identity.subject())
        .filter(other -> !other.getId().equals(user.getId()))
        .ifPresent(
            other -> {
              throw new IllegalArgumentException(
                  "This Google account is already linked to another user");
            });
    user.setGoogleSub(identity.subject());
    user.setUpdatedAt(clock.instant());
    return toProfileResponse(userJpaRepository.save(user));
  }

  /**
   * Changes the signed-in user's password. Required before anything else for accounts created with
   * a temporary password.
   */
  public void changePassword(String username, String currentPassword, String newPassword) {
    UserEntity user =
        userJpaRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    if (user.getPasswordHash() != null
        && (currentPassword == null
            || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
      throw new IllegalArgumentException("Your current password is incorrect");
    }
    if (user.getPasswordHash() != null
        && passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new IllegalArgumentException("Choose a different password from your current one");
    }
    user.setPasswordHash(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);
    user.setUpdatedAt(clock.instant());
    userJpaRepository.save(user);
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
    UserAccountResponse created =
        createAccount(
            request.username(),
            request.fullName(),
            request.email(),
            request.role(),
            request.password());
    // The administrator chose this password, so the person must replace it on first sign-in.
    userJpaRepository
        .findById(created.id())
        .ifPresent(
            u -> {
              u.setMustChangePassword(true);
              userJpaRepository.save(u);
            });
    return created;
  }

  private UserAccountResponse createAccount(
      String username, String fullName, String rawEmail, Role role, String password) {
    if (userJpaRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("That username is already taken");
    }
    String email =
        rawEmail == null || rawEmail.isBlank() ? null : rawEmail.trim().toLowerCase(Locale.ROOT);
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

  /** Sets the address notification emails go to; blank removes it. */
  public UserAccountResponse updateEmail(UUID userId, String email, String actorUsername) {
    UserEntity user = loadManageableUser(userId, actorUsername);
    String normalized = email == null || email.isBlank() ? null : email.trim();
    if (normalized != null) {
      userJpaRepository
          .findByEmailIgnoreCase(normalized)
          .filter(other -> !other.getId().equals(user.getId()))
          .ifPresent(
              other -> {
                throw new IllegalArgumentException("Another account already uses this email");
              });
    }
    user.setEmail(normalized);
    user.setUpdatedAt(clock.instant());
    return toAccountResponse(userJpaRepository.save(user));
  }

  public UserAccountResponse setActive(UUID userId, boolean active, String actorUsername) {
    UserEntity user = loadManageableUser(userId, actorUsername);
    user.setActive(active);
    user.setUpdatedAt(clock.instant());
    UserAccountResponse saved = toAccountResponse(userJpaRepository.save(user));
    if (!active) {
      // A deactivated account stops receiving live updates straight away.
      liveEvents.disconnect(user.getId());
    }
    return saved;
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
    String base =
        email
            .substring(0, email.indexOf('@'))
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "");
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
    Set<Category> existing =
        user.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());
    for (Category category : newSkills) {
      if (!existing.contains(category)) {
        user.getSkills().add(new UserSkillEntity(UUID.randomUUID(), user, category));
      }
    }

    UserEntity saved = userJpaRepository.save(user);
    return toTechnicianResponse(saved);
  }

  /** Sets how many open tickets a technician may hold and whether they lead the team. */
  public TechnicianResponse updateTechnicianCapacity(
      UUID technicianId, int maxActiveTickets, boolean teamLead) {
    UserEntity user =
        userJpaRepository
            .findById(technicianId)
            .orElseThrow(() -> new UserNotFoundException("Technician not found: " + technicianId));
    if (user.getRole() != Role.TECHNICIAN) {
      throw new IllegalArgumentException("Capacity can only be set for technicians");
    }
    user.setMaxActiveTickets(maxActiveTickets);
    user.setTeamLead(teamLead);
    return toTechnicianResponse(userJpaRepository.save(user));
  }

  private UserProfileResponse toProfileResponse(UserEntity user) {
    Set<Category> skills =
        user.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());
    return new UserProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getEmail(),
        user.getRole(),
        skills,
        user.isMustChangePassword(),
        user.getGoogleSub() != null,
        user.getPasswordHash() != null);
  }

  private TechnicianResponse toTechnicianResponse(UserEntity tech) {
    Set<Category> skills =
        tech.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());

    int activeTickets =
        incidentJpaRepository.countByAssigneeIdAndStatusNotIn(
            tech.getId(), IncidentStatus.notWorkload());

    return new TechnicianResponse(
        tech.getId(),
        tech.getUsername(),
        tech.getFullName(),
        tech.getEmail(),
        skills,
        activeTickets,
        tech.isActive(),
        tech.getMaxActiveTickets(),
        tech.isTeamLead());
  }
}
