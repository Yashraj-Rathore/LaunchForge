package dev.launchforge.controlapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {
  public static final String HEADER_NAME = "X-Correlation-ID";
  public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName();
  private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = normalize(request.getHeader(HEADER_NAME));
    request.setAttribute(ATTRIBUTE_NAME, correlationId);
    response.setHeader(HEADER_NAME, correlationId);
    String previous = MDC.get("correlationId");
    MDC.put("correlationId", correlationId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) {
        MDC.remove("correlationId");
      } else {
        MDC.put("correlationId", previous);
      }
    }
  }

  public static String current() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servlet) {
      Object value = servlet.getRequest().getAttribute(ATTRIBUTE_NAME);
      if (value instanceof String correlationId) {
        return correlationId;
      }
    }
    return UUID.randomUUID().toString();
  }

  public static String get(HttpServletRequest request) {
    Object value = request.getAttribute(ATTRIBUTE_NAME);
    return value instanceof String correlationId ? correlationId : UUID.randomUUID().toString();
  }

  private static String normalize(String candidate) {
    return candidate != null && SAFE_VALUE.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }
}
