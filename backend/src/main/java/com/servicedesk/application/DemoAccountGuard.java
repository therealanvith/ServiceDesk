package com.servicedesk.application;

import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The seeded demo accounts (emp, tech_net, tech_hw, tech_lead, admin) share a password that is
 * published in this repository. They are only usable when {@code app.demo-accounts.enabled} is true
 * (local demos); otherwise they are deactivated on every start, so a deployment can never be
 * entered with the documented credentials.
 */
@Component
public class DemoAccountGuard implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoAccountGuard.class);

  /** Ids seeded by V5__seed_demo_data.sql. */
  static final List<UUID> DEMO_ACCOUNT_IDS =
      List.of(
          UUID.fromString("11111111-1111-1111-1111-111111111111"),
          UUID.fromString("22222222-2222-2222-2222-222222222222"),
          UUID.fromString("33333333-3333-3333-3333-333333333333"),
          UUID.fromString("44444444-4444-4444-4444-444444444444"),
          UUID.fromString("55555555-5555-5555-5555-555555555555"));

  private final SpringDataUserJpaRepository users;
  private final Clock clock;
  private final boolean enabled;

  public DemoAccountGuard(
      SpringDataUserJpaRepository users,
      Clock clock,
      @Value("${app.demo-accounts.enabled:false}") boolean enabled) {
    this.users = users;
    this.clock = clock;
    this.enabled = enabled;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<UserEntity> demo = users.findAllById(DEMO_ACCOUNT_IDS);
    if (demo.isEmpty()) {
      return;
    }
    int changed = 0;
    for (UserEntity account : demo) {
      if (account.isActive() != enabled) {
        account.setActive(enabled);
        account.setUpdatedAt(clock.instant());
        changed++;
      }
    }
    users.saveAll(demo);
    if (enabled) {
      log.warn(
          "Demo accounts are ENABLED with a publicly documented password. Never enable this"
              + " outside local demos.");
    } else if (changed > 0) {
      log.info(
          "Deactivated {} demo accounts (set DEMO_ACCOUNTS_ENABLED=true for local demos).",
          changed);
    }
  }
}
