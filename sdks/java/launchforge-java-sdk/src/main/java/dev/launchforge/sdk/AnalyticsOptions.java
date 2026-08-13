package dev.launchforge.sdk;

import java.time.Duration;
import java.util.Objects;

/** Explicit opt-in settings for privacy-minimized, best-effort evaluation analytics. */
public record AnalyticsOptions(
    int queueCapacity, int batchSize, Duration flushInterval, Duration requestTimeout) {
  public AnalyticsOptions {
    if (queueCapacity < 1 || queueCapacity > 100_000) {
      throw new IllegalArgumentException("queueCapacity must be between 1 and 100000");
    }
    if (batchSize < 1 || batchSize > 100 || batchSize > queueCapacity) {
      throw new IllegalArgumentException("batchSize must be between 1 and 100 and fit the queue");
    }
    requireDuration(flushInterval, "flushInterval", Duration.ofMillis(100), Duration.ofMinutes(5));
    requireDuration(
        requestTimeout, "requestTimeout", Duration.ofMillis(100), Duration.ofSeconds(30));
  }

  public static AnalyticsOptions defaults() {
    return new AnalyticsOptions(1_000, 50, Duration.ofSeconds(1), Duration.ofSeconds(2));
  }

  private static void requireDuration(
      Duration value, String name, Duration minimum, Duration maximum) {
    Objects.requireNonNull(value, name);
    if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(name + " is outside its supported bound");
    }
  }
}
