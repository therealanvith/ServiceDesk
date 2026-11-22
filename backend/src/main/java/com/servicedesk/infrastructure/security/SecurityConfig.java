package com.servicedesk.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.ErrorResponse;
import com.servicedesk.infrastructure.security.ratelimit.InMemoryRateLimiter;
import com.servicedesk.infrastructure.security.ratelimit.RateLimitFilter;
import com.servicedesk.infrastructure.security.ratelimit.RateLimitSettings;
import com.servicedesk.infrastructure.security.ratelimit.RateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Spring Security configuration for ServiceDesk REST API. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// Imported so web-slice tests, which skip component scanning, still get the limiter.
@Import({InMemoryRateLimiter.class, RateLimitSettings.class})
public class SecurityConfig {

  private final List<String> allowedOrigins;
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final RateLimiter rateLimiter;
  private final RateLimitSettings rateLimitSettings;

  public SecurityConfig(
      @Value("${app.security.cors.allowed-origins:http://localhost:3000}") String allowedOriginsStr,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      ObjectMapper objectMapper,
      Clock clock,
      RateLimiter rateLimiter,
      RateLimitSettings rateLimitSettings) {
    this.rateLimiter = rateLimiter;
    this.rateLimitSettings = rateLimitSettings;
    this.objectMapper = objectMapper;
    this.clock = clock;
    this.allowedOrigins =
        Arrays.stream(allowedOriginsStr.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList();
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Please sign in again to continue"))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeError(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "You don't have permission to do that")))
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // A live-updates stream is authorised when it opens; the dispatch Spring makes
                // when it ends carries no token and must not be refused.
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/**")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMINISTRATOR", "ROOT")
                    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/users/technicians")
                    .hasAnyRole("TECHNICIAN", "ADMINISTRATOR", "ROOT")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/users/*/skills")
                    .hasAnyRole("ADMINISTRATOR", "ROOT")
                    .requestMatchers("/api/v1/users", "/api/v1/users/**")
                    .hasAnyRole("ADMINISTRATOR", "ROOT")
                    .requestMatchers("/api/v1/incidents/**")
                    .authenticated()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(
            new RateLimitFilter(rateLimiter, rateLimitSettings, objectMapper, clock),
            JwtAuthenticationFilter.class);

    return http.build();
  }

  /** 401/403 bodies use the same {@link ErrorResponse} shape as every other API error. */
  private void writeError(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI()));
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
    configuration.setExposedHeaders(
        List.of("Authorization", "Retry-After", "RateLimit-Limit", "RateLimit-Remaining"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
