package com.servicedesk.infrastructure.persistence.repository;

import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataUserJpaRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByUsername(String username);

  Optional<UserEntity> findByGoogleSub(String googleSub);

  Optional<UserEntity> findByEmailIgnoreCase(String email);

  boolean existsByUsername(String username);

  boolean existsByRole(Role role);

  List<UserEntity> findAllByOrderByRoleAscUsernameAsc();

  List<UserEntity> findByRole(Role role);

  List<UserEntity> findByRoleAndActiveTrue(Role role);
}
