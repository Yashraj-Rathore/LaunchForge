package dev.launchforge.configedge.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.security.abuse")
public record EdgeAbuseProperties(
    @DefaultValue("true") boolean redisEnabled,
    @DefaultValue("1m") Duration window,
    @DefaultValue("600") int snapshotRequestsPerWindow,
    @DefaultValue("30") int streamStartsPerWindow,
    @DefaultValue("600") int analyticsRequestsPerWindow,
    @DefaultValue("2m") Duration streamLeaseDuration) {
  public EdgeAbuseProperties {
    if (window == null
        || window.compareTo(Duration.ofSeconds(1)) < 0
        || window.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("Rate-limit window is invalid");
    }
    requireRate(snapshotRequestsPerWindow, "snapshotRequestsPerWindow");
    requireRate(streamStartsPerWindow, "streamStartsPerWindow");
    requireRate(analyticsRequestsPerWindow, "analyticsRequestsPerWindow");
    if (streamLeaseDuration == null
        || streamLeaseDuration.compareTo(Duration.ofSeconds(30)) < 0
        || streamLeaseDuration.compareTo(Duration.ofMinutes(5)) > 0) {
      throw new IllegalArgumentException("streamLeaseDuration is invalid");
    }
  }

  private static void requireRate(int value, String name) {
    if (value < 1 || value > 1_000_000) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
