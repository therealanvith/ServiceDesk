package com.servicedesk.infrastructure.persistence.adapter;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.repository.UserRepository;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.entity.UserSkillEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataIncidentJpaRepository;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Hexagonal persistence adapter implementing the domain UserRepository port. */
@Component
public class JpaUserRepositoryAdapter implements UserRepository {

  private final SpringDataUserJpaRepository userJpaRepository;
  private final SpringDataIncidentJpaRepository incidentJpaRepository;
  private final Clock clock;

  public JpaUserRepositoryAdapter(
      SpringDataUserJpaRepository userJpaRepository,
      SpringDataIncidentJpaRepository incidentJpaRepository,
      Clock clock) {
    this.userJpaRepository = userJpaRepository;
    this.incidentJpaRepository = incidentJpaRepository;
    this.clock = clock;
  }

  @Override
  public Optional<User> findById(UserId id) {
    return userJpaRepository.findById(id.getValue()).map(this::toDomain);
  }

  @Override
  public Optional<User> findByUsername(String username) {
    return userJpaRepository.findByUsername(username).map(this::toDomain);
  }

  @Override
  public List<User> findTechnicians() {
    return userJpaRepository.findByRole(Role.TECHNICIAN).stream().map(this::toDomain).toList();
  }

  @Override
  public List<User> findAll() {
    return userJpaRepository.findAll().stream().map(this::toDomain).toList();
  }

  @Override
  public User save(User user) {
    UserEntity entity =
        userJpaRepository.findById(user.getId().getValue()).orElseGet(UserEntity::new);

    entity.setId(user.getId().getValue());
    entity.setUsername(user.getUsername());
    entity.setFullName(user.getFullName());
    entity.setEmail(user.getEmail());
    entity.setRole(user.getRole());
    entity.setActive(user.isAvailable());
    Instant now = clock.instant();
    if (entity.getCreatedAt() == null) {
      entity.setCreatedAt(now);
    }
    entity.setUpdatedAt(now);

    // Keep existing skill rows and add only new ones: deleting and re-inserting the same
    // (user, category) in one flush can violate the unique constraint.
    Set<Category> wanted = user.getSkills();
    entity.getSkills().removeIf(skill -> !wanted.contains(skill.getCategory()));
    Set<Category> existing =
        entity.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());
    for (Category category : wanted) {
      if (!existing.contains(category)) {
        entity.getSkills().add(new UserSkillEntity(UUID.randomUUID(), entity, category));
      }
    }

    UserEntity saved = userJpaRepository.save(entity);
    return toDomain(saved);
  }

  private User toDomain(UserEntity entity) {
    Set<Category> skills =
        entity.getSkills().stream().map(UserSkillEntity::getCategory).collect(Collectors.toSet());

    int activeTickets =
        incidentJpaRepository.countByAssigneeIdAndStatusNotIn(
            entity.getId(), IncidentStatus.notWorkload());

    return new User(
        UserId.of(entity.getId()),
        entity.getUsername(),
        entity.getFullName(),
        entity.getEmail(),
        entity.getRole(),
        skills,
        activeTickets,
        entity.isActive(),
        entity.getMaxActiveTickets(),
        entity.isTeamLead());
  }
}
