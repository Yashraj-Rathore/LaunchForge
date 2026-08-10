package dev.launchforge.controlapi.security;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("launchforge.security.session")
public record SessionSecurityProperties(Duration absoluteLifetime) {
  private static final Duration MINIMUM_ABSOLUTE_LIFETIME = Duration.ofMinutes(30);
  private static final Duration MAXIMUM_ABSOLUTE_LIFETIME = Duration.ofHours(24);

  public SessionSecurityProperties {
    Objects.requireNonNull(absoluteLifetime, "absoluteLifetime");
    if (absoluteLifetime.compareTo(MINIMUM_ABSOLUTE_LIFETIME) < 0
        || absoluteLifetime.compareTo(MAXIMUM_ABSOLUTE_LIFETIME) > 0) {
      throw new IllegalArgumentException(
          "Session absolute lifetime must be between 30 minutes and 24 hours");
    }
  }
}
