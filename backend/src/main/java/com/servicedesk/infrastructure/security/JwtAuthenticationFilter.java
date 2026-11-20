package com.servicedesk.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Whether a user with a temporary password may call this endpoint. */
  private static boolean isAllowedBeforePasswordChange(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.equals("/api/v1/users/me")
        || path.equals("/api/v1/users/me/password")
        || path.startsWith("/api/v1/auth/")
        || path.equals("/api/v1/health");
  }

  private final JwtTokenProvider jwtTokenProvider;
  private final DomainUserDetailsService userDetailsService;

  public JwtAuthenticationFilter(
      JwtTokenProvider jwtTokenProvider, DomainUserDetailsService userDetailsService) {
    this.jwtTokenProvider = jwtTokenProvider;
    this.userDetailsService = userDetailsService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();

      if (jwtTokenProvider.validateToken(token)) {
        String username = jwtTokenProvider.extractUsername(token);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
          UserDetails userDetails;
          try {
            userDetails = userDetailsService.loadUserByUsername(username);
          } catch (UsernameNotFoundException ex) {
            filterChain.doFilter(request, response);
            return;
          }
          if (!userDetails.isEnabled()
              || !userDetails.isAccountNonLocked()
              || !userDetails.isAccountNonExpired()
              || !userDetails.isCredentialsNonExpired()) {
            filterChain.doFilter(request, response);
            return;
          }

          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  userDetails, null, userDetails.getAuthorities());
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

          SecurityContextHolder.getContext().setAuthentication(authentication);

          if (userDetails instanceof SecurityUser securityUser
              && securityUser.getUserEntity().isMustChangePassword()
              && !isAllowedBeforePasswordChange(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response
                .getWriter()
                .write(
                    "{\"status\":403,\"error\":\"Forbidden\","
                        + "\"message\":\"Change your temporary password to continue\","
                        + "\"code\":\"PASSWORD_CHANGE_REQUIRED\"}");
            return;
          }
        }
      }
    }

    filterChain.doFilter(request, response);
  }
}
