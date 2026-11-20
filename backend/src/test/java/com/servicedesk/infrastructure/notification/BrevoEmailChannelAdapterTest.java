package com.servicedesk.infrastructure.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.notification.NotificationMessage;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.infrastructure.persistence.entity.UserEntity;
import com.servicedesk.infrastructure.persistence.repository.SpringDataUserJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoEmailChannelAdapterTest {

  private final SpringDataUserJpaRepository users = mock(SpringDataUserJpaRepository.class);
  private MockRestServiceServer server;
  private BrevoEmailChannelAdapter adapter;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://brevo.test/v3");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter =
        new BrevoEmailChannelAdapter(
            builder.build(),
            users,
            "brevo-key",
            "desk@example.org",
            "ServiceDesk",
            "https://desk.example.org/");
  }

  private UUID userWithEmail(String email) {
    UUID id = UUID.randomUUID();
    UserEntity user =
        new UserEntity(id, "ana", "x", "Ana <Admin>", email, Role.EMPLOYEE, true, null, null);
    when(users.findById(id)).thenReturn(Optional.of(user));
    return id;
  }

  @Test
  void sendsATransactionalEmailWithAnEscapedBodyAndATicketLink() {
    UUID recipient = userWithEmail("ana@example.org");
    UUID ticket = UUID.randomUUID();
    server
        .expect(requestTo("https://brevo.test/v3/smtp/email"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", "brevo-key"))
        .andExpect(jsonPath("$.sender.email").value("desk@example.org"))
        .andExpect(jsonPath("$.to[0].email").value("ana@example.org"))
        .andExpect(jsonPath("$.subject").value("Incident Resolved: INC-1001"))
        .andExpect(jsonPath("$.htmlContent", Matchers.containsString("&lt;script&gt;")))
        .andExpect(jsonPath("$.htmlContent", Matchers.not(Matchers.containsString("<script>"))))
        .andExpect(
            jsonPath(
                "$.textContent",
                Matchers.containsString("https://desk.example.org/incidents/" + ticket)))
        .andRespond(withSuccess());

    adapter.send(
        NotificationMessage.of(
            UserId.of(recipient),
            IncidentId.of(ticket),
            "RESOLVED",
            "Incident Resolved: INC-1001",
            "Resolution: <script>alert(1)</script>"));

    server.verify();
  }

  @Test
  void skipsPeopleWithoutAnEmailAndSurvivesApiErrors() {
    adapter.send(
        NotificationMessage.of(UserId.of(userWithEmail(null)), null, "X", "Title", "Body"));
    server.verify(); // no request was made

    server.expect(requestTo("https://brevo.test/v3/smtp/email")).andRespond(withServerError());
    adapter.send(
        NotificationMessage.of(
            UserId.of(userWithEmail("ana@example.org")), null, "X", "Title", "Body"));
    server.verify(); // the error was logged, not thrown
  }
}
