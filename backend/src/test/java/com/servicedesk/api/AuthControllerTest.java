package com.servicedesk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.AuthResponse;
import com.servicedesk.api.dto.LoginRequest;
import com.servicedesk.api.dto.UserProfileResponse;
import com.servicedesk.application.UserApplicationService;
import com.servicedesk.domain.user.Role;
import com.servicedesk.infrastructure.config.ClockConfig;
import com.servicedesk.infrastructure.security.DomainUserDetailsService;
import com.servicedesk.infrastructure.security.JwtAuthenticationFilter;
import com.servicedesk.infrastructure.security.JwtTokenProvider;
import com.servicedesk.infrastructure.security.SecurityConfig;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ClockConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserApplicationService userService;
  @MockBean private JwtTokenProvider jwtTokenProvider;
  @MockBean private DomainUserDetailsService userDetailsService;

  @Test
  @DisplayName("POST /api/v1/auth/login succeeds with valid credentials")
  void testLoginSuccess() throws Exception {
    LoginRequest request = new LoginRequest("employee1", "password123");
    UserProfileResponse profile =
        new UserProfileResponse(
            UUID.randomUUID(), "employee1", "Prof. Alan Turing", "aturing@campus.edu", Role.EMPLOYEE, Set.of());
    AuthResponse authResponse = new AuthResponse("mock.jwt.token", profile);

    when(userService.login(any(LoginRequest.class))).thenReturn(authResponse);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("mock.jwt.token"))
        .andExpect(jsonPath("$.user.username").value("employee1"));
  }
}
