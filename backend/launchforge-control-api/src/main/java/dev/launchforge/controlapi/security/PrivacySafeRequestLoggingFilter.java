package dev.launchforge.controlapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class PrivacySafeRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PrivacySafeRequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      LOGGER.info(
          "ControlRequestCompleted method={} routeFamily={} status={} correlationId={}",
          safeMethod(request.getMethod()),
          routeFamily(request.getRequestURI()),
          response.getStatus(),
          CorrelationIdFilter.get(request));
    }
  }

  private static String safeMethod(String method) {
    return method != null && method.matches("[A-Z]{3,7}") ? method : "OTHER";
  }

  private static String routeFamily(String path) {
    if (path == null) {
      return "other";
    }
    if (path.startsWith("/api/v1/auth/")
        || path.startsWith("/oauth2/")
        || path.startsWith("/login/")) {
      return "authentication";
    }
    if (path.contains("/sdk-keys") || path.contains("/client-keys")) {
      return "key_lifecycle";
    }
    if (path.startsWith("/api/v1/")) {
      return "management";
    }
    if (path.startsWith("/actuator/")) {
      return "actuator";
    }
    return "static";
  }
}
