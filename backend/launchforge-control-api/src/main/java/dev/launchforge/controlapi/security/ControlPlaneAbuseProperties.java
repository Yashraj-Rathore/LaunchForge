package dev.launchforge.controlapi.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.security.abuse")
public record ControlPlaneAbuseProperties(
    @DefaultValue("true") boolean redisEnabled,
    @DefaultValue("1m") Duration window,
    @DefaultValue("60") int loginRequestsPerWindow,
    @DefaultValue("600") int managementReadRequestsPerWindow,
    @DefaultValue("120") int managementMutationRequestsPerWindow,
    @DefaultValue("30") int keyLifecycleRequestsPerWindow,
    @DefaultValue("1048576") int maximumRequestBytes) {
  public ControlPlaneAbuseProperties {
    if (window == null
        || window.compareTo(Duration.ofSeconds(1)) < 0
        || window.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("Rate-limit window is invalid");
    }
    requireRate(loginRequestsPerWindow, "loginRequestsPerWindow");
    requireRate(managementReadRequestsPerWindow, "managementReadRequestsPerWindow");
    requireRate(managementMutationRequestsPerWindow, "managementMutationRequestsPerWindow");
    requireRate(keyLifecycleRequestsPerWindow, "keyLifecycleRequestsPerWindow");
    if (maximumRequestBytes < 1 || maximumRequestBytes > 1024 * 1024) {
      throw new IllegalArgumentException("maximumRequestBytes is invalid");
    }
  }

  int limit(ManagementRateLimiter.Policy policy) {
    return switch (policy) {
      case LOGIN -> loginRequestsPerWindow;
      case MANAGEMENT_READ -> managementReadRequestsPerWindow;
      case MANAGEMENT_MUTATION -> managementMutationRequestsPerWindow;
      case KEY_LIFECYCLE -> keyLifecycleRequestsPerWindow;
    };
  }

  private static void requireRate(int value, String name) {
    if (value < 1 || value > 1_000_000) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
