package com.servicedesk.application;

import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the single root account on first start from ROOT_USERNAME / ROOT_PASSWORD.
 *
 * <p>The password is only read when no root account exists yet, so it never lives in source
 * control and later restarts never overwrite it.
 */
@Component
public class RootAccountInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(RootAccountInitializer.class);
  private static final int MIN_PASSWORD_LENGTH = 12;

  private final SpringDataUserJpaRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final String username;
  private final String password;

  public RootAccountInitializer(
      SpringDataUserJpaRepository users,
      PasswordEncoder passwordEncoder,
      Clock clock,
      @Value("${app.root.username:root}") String username,
      @Value("${app.root.password:}") String password) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
    this.username = username == null || username.isBlank() ? "root" : username.trim();
    this.password = password == null ? "" : password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (users.existsByRole(Role.ROOT)) {
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH) {
      log.warn(
          "No root account exists. Set ROOT_PASSWORD (at least {} characters) to create one.",
          MIN_PASSWORD_LENGTH);
      return;
    }
    if (users.existsByUsername(username)) {
      log.error("Cannot create root account: username '{}' is already taken.", username);
      return;
    }
    users.save(
        new UserEntity(
            UUID.randomUUID(),
            username,
            passwordEncoder.encode(password),
            "Root",
            null,
            Role.ROOT,
            true,
            clock.instant(),
            clock.instant()));
    log.info("Created root account '{}'.", username);
  }
}
