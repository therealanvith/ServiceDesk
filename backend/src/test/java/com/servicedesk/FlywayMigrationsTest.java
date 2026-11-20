package com.servicedesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Applies every Flyway migration to a real PostgreSQL 16 (the production database) and then
 * validates the JPA entities against the migrated schema. Catches SQL errors and entity/column
 * drift (for example a SMALLINT column mapped as INTEGER) before deployment.
 *
 * <p>The other integration tests use H2 with a schema generated from the entities, so this is the
 * test that guards the migrations. Skipped when Docker isn't available.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
    })
class FlywayMigrationsTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;

  @Test
  void allMigrationsApplyAndEntitiesMatchTheSchema() {
    Integer failed =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
    assertThat(failed).isZero();
  }

  @Test
  void databaseAllowsOnlyOneRootAccount() {
    jdbc.update(
        "INSERT INTO users (id, username, full_name, role) VALUES (gen_random_uuid(), 'root-a',"
            + " 'Root A', 'ROOT')");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users (id, username, full_name, role) VALUES (gen_random_uuid(),"
                        + " 'root-b', 'Root B', 'ROOT')"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
