package dev.launchforge.controlapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {
  static final String ABSOLUTE_EXPIRY_ATTRIBUTE =
      "dev.launchforge.security.absoluteSessionExpiryEpochMilli";

  private final SessionSecurityProperties properties;
  private final Clock clock;

  AbsoluteSessionLifetimeFilter(SessionSecurityProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      HttpSession session = request.getSession(false);
      if (session != null && isExpiredOrInitialize(session)) {
        session.invalidate();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private boolean isExpiredOrInitialize(HttpSession session) {
    long now = clock.millis();
    Object storedExpiry = session.getAttribute(ABSOLUTE_EXPIRY_ATTRIBUTE);
    if (storedExpiry == null) {
      session.setAttribute(
          ABSOLUTE_EXPIRY_ATTRIBUTE, now + properties.absoluteLifetime().toMillis());
      return false;
    }
    if (!(storedExpiry instanceof Long expiry)) {
      return true;
    }
    return now >= expiry;
  }
}
