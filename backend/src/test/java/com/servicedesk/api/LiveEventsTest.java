package com.servicedesk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.live.LiveNotificationChannelAdapter;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LiveEventsTest {

  @Autowired MockMvc mvc;
  @Autowired SpringDataUserJpaRepository users;
  @Autowired LiveNotificationChannelAdapter liveChannel;

  private UserEntity account(String username, Role role, String email) {
    return users.saveAndFlush(
        new UserEntity(
            UUID.randomUUID(),
            username,
            "unused",
            "Name " + username,
            email,
            role,
            true,
            null,
            null));
  }

  @Test
  void theStreamNeedsASignedInUser() throws Exception {
    mvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
  }

  @Test
  void notificationsArriveOnTheRecipientsStream() throws Exception {
    UserEntity listener = account("listener", Role.EMPLOYEE, null);
    MvcResult stream =
        mvc.perform(
                get("/api/v1/events")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .with(user("listener").roles("EMPLOYEE")))
            .andExpect(request().asyncStarted())
            .andReturn();

    liveChannel.send(
        NotificationMessage.of(
            UserId.of(listener.getId()), null, "ASSIGNMENT", "Picked up", "Someone has it"));

    String body = stream.getResponse().getContentAsString();
    assertThat(body).contains("event:ready").contains("event:notification").contains("Picked up");
  }

  @Test
  void administratorsSetEmailsAndAddressesStayUnique() throws Exception {
    account("boss", Role.ADMINISTRATOR, null);
    UserEntity first = account("first", Role.EMPLOYEE, "first@example.org");
    UserEntity second = account("second", Role.EMPLOYEE, null);

    mvc.perform(
            put("/api/v1/users/" + second.getId() + "/email")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"second@example.org\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("second@example.org"));

    mvc.perform(
            put("/api/v1/users/" + second.getId() + "/email")
                .with(user("boss").roles("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"FIRST@example.org\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(
            put("/api/v1/users/" + first.getId() + "/email")
                .with(user("second").roles("EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"x@example.org\"}"))
        .andExpect(status().isForbidden());
  }
}
