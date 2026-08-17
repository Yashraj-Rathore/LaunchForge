package dev.launchforge.controlapi.security;

import dev.launchforge.controlapi.security.ManagementRateLimiter.Decision;
import dev.launchforge.controlapi.security.ManagementRateLimiter.Policy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ManagementRateLimitFilter extends OncePerRequestFilter {
  private final ManagementRateLimiter limiter;

  public ManagementRateLimitFilter(ManagementRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Policy policy = policy(request);
    if (policy == null) {
      chain.doFilter(request, response);
      return;
    }
    Decision decision = limiter.check(policy, partition(request));
    if (!decision.permitted()) {
      response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
      SecurityProblemWriter.write(
          request,
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "MANAGEMENT_RATE_LIMITED",
          "The management request rate limit was reached");
      return;
    }
    chain.doFilter(request, response);
  }

  private static Policy policy(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/oauth2/")
        || path.startsWith("/login/")
        || path.startsWith("/api/v1/auth/")) {
      return Policy.LOGIN;
    }
    if (!path.startsWith("/api/")) {
      return null;
    }
    if (path.contains("/sdk-keys") || path.contains("/client-keys")) {
      return Policy.KEY_LIFECYCLE;
    }
    return HttpMethod.GET.matches(request.getMethod())
        ? Policy.MANAGEMENT_READ
        : Policy.MANAGEMENT_MUTATION;
  }

  private static String partition(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken)) {
      return "identity:" + authentication.getName();
    }
    String address = request.getRemoteAddr();
    return "network:" + (address == null ? "unknown" : address);
  }
}
