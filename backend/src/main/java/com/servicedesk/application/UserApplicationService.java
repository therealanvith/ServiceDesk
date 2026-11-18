package com.servicedesk.application;

import com.servicedesk.api.dto.AuthResponse;
import com.servicedesk.api.dto.LoginRequest;
import com.servicedesk.api.dto.TechnicianResponse;
import com.servicedesk.api.dto.UserProfileResponse;
import com.servicedesk.domain.exception.UserNotFoundException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.entity.UserSkillEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

  public UserApplicationService(
      SpringDataUserJpaRepository userJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider) {
    this.userJpaRepository = userJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    UserEntity user =
        userJpaRepository
            .findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid username or password");
    }

    String token =
        jwtTokenProvider.generateToken(UserId.of(user.getId()), user.getUsername(), user.getRole());

    UserProfileResponse profile = toProfileResponse(user);
    return new AuthResponse(token, profile);
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

    user.getSkills().clear();
    if (newSkills != null) {
      for (Category category : newSkills) {
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
