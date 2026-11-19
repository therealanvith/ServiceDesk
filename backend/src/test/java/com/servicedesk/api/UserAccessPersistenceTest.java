package com.servicedesk.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.application.UserApplicationService;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAccessPersistenceTest {
  @Autowired MockMvc mvc;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired UserApplicationService service;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtTokenProvider tokens;

  private UserEntity createUser(Role role) {
    return users.saveAndFlush(new UserEntity(UUID.randomUUID(), "access-test", encoder.encode("test-password"),
        "Access Test", "access@test.example", role, true, null, null));
  }

  @Test
  void disabledUsersCannotLoginOrReuseToken() throws Exception {
    UserEntity account = createUser(Role.EMPLOYEE);
    String token = tokens.generateToken(UserId.of(account.getId()), account.getUsername(), account.getRole());
    account.setActive(false);
    users.flush();
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"access-test\",\"password\":\"test-password\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deletedUsersTokensReturnUnauthorizedInsteadOfServerError() throws Exception {
    String token = tokens.generateToken(UserId.of(UUID.randomUUID()), "deleted-user", Role.EMPLOYEE);
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void repeatedSkillUpdatesKeepExistingRows() {
    UserEntity technician = createUser(Role.TECHNICIAN);
    service.updateTechnicianSkills(technician.getId(), Set.of(Category.NETWORK));
    users.flush();
    UUID skillId = technician.getSkills().iterator().next().getId();
    service.updateTechnicianSkills(technician.getId(), Set.of(Category.NETWORK, Category.HARDWARE));
    users.flush();
    service.updateTechnicianSkills(technician.getId(), Set.of(Category.NETWORK));
    users.flush();
    assertThat(technician.getSkills()).hasSize(1);
    assertThat(technician.getSkills().iterator().next().getId()).isEqualTo(skillId);
  }

  @Test
  void adminsManageStaffButOnlyRootGrantsAdmin() throws Exception {
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "boss", "unused", "Boss",
        null, Role.ADMINISTRATOR, true, null, null));
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "root", "unused", "Root",
        null, Role.ROOT, true, null, null));
    UserEntity employee = createUser(Role.EMPLOYEE);

    mvc.perform(get("/api/v1/users").with(user("boss").roles("ADMINISTRATOR")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/users").with(user("access-test").roles("EMPLOYEE")))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/users/" + employee.getId() + "/role")
            .with(user("boss").roles("ADMINISTRATOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"TECHNICIAN\"}"))
        .andExpect(status().isOk());
    assertThat(users.findById(employee.getId()).orElseThrow().getRole()).isEqualTo(Role.TECHNICIAN);

    mvc.perform(put("/api/v1/users/" + employee.getId() + "/role")
            .with(user("boss").roles("ADMINISTRATOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMINISTRATOR\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/users/" + employee.getId() + "/role")
            .with(user("root").roles("ROOT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMINISTRATOR\"}"))
        .andExpect(status().isOk());
    // Now an administrator, so other administrators can no longer change it.
    mvc.perform(put("/api/v1/users/" + employee.getId() + "/active")
            .with(user("boss").roles("ADMINISTRATOR"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/users/" + employee.getId() + "/role")
            .with(user("root").roles("ROOT"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ROOT\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void adminsCreatePasswordAccounts() throws Exception {
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "boss", "unused", "Boss",
        null, Role.ADMINISTRATOR, true, null, null));
    mvc.perform(post("/api/v1/users").with(user("boss").roles("ADMINISTRATOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new.tech\",\"fullName\":\"New Tech\","
                + "\"role\":\"TECHNICIAN\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/users").with(user("boss").roles("ADMINISTRATOR"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new.tech\",\"fullName\":\"New Tech\","
                + "\"role\":\"TECHNICIAN\",\"password\":\"a-long-enough-password\"}"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new.tech\",\"password\":\"a-long-enough-password\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void selfRegistrationCreatesEmployees() throws Exception {
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new.emp\",\"fullName\":\"New Employee\","
                + "\"password\":\"a-long-enough-password\",\"role\":\"ROOT\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"new.emp\",\"fullName\":\"Again\","
                + "\"password\":\"a-long-enough-password\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void googleOnlyAccountsCannotUsePasswordLogin() throws Exception {
    users.saveAndFlush(new UserEntity(UUID.randomUUID(), "google-user", null,
        "Google User", null, Role.EMPLOYEE, true, null, null));
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"google-user\",\"password\":\"anything-at-all\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void googleSignInIsOffUntilConfigured() throws Exception {
    mvc.perform(get("/api/v1/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.googleClientId").doesNotExist());
    mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
            .content("{\"credential\":\"x.y.z\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = "ADMINISTRATOR")
  void rejectsNullSkillsAndNonTechnicians() throws Exception {
    UserEntity employee = createUser(Role.EMPLOYEE);
    for (String body : new String[] {"{\"skills\":[null]}", "{\"skills\":[\"NETWORK\"]}"}) {
      mvc.perform(put("/api/v1/users/" + employee.getId() + "/skills")
          .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
  }
}
