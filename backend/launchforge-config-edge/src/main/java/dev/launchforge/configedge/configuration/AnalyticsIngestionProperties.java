package dev.launchforge.configedge.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.analytics.ingestion")
public record AnalyticsIngestionProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("launchforge.analytics.evaluations.v1") String topic,
    @DefaultValue("262144") int maximumRequestBytes,
    @DefaultValue("100") int maximumBatchSize,
    @DefaultValue("16") int maximumConcurrentRequests,
    @DefaultValue("600") int requestsPerKeyPerMinute,
    @DefaultValue("2s") Duration publishTimeout,
    @DefaultValue("24h") Duration maximumPastAge,
    @DefaultValue("5m") Duration maximumFutureSkew) {
  public AnalyticsIngestionProperties {
    if (topic == null || topic.isBlank() || topic.length() > 249) {
      throw new IllegalArgumentException("analytics topic is invalid");
    }
    if (maximumRequestBytes < 1024 || maximumRequestBytes > 1024 * 1024) {
      throw new IllegalArgumentException("maximumRequestBytes is invalid");
    }
    if (maximumBatchSize < 1 || maximumBatchSize > 100) {
      throw new IllegalArgumentException("maximumBatchSize is invalid");
    }
    if (maximumConcurrentRequests < 1 || maximumConcurrentRequests > 10_000) {
      throw new IllegalArgumentException("maximumConcurrentRequests is invalid");
    }
    if (requestsPerKeyPerMinute < 1 || requestsPerKeyPerMinute > 100_000) {
      throw new IllegalArgumentException("requestsPerKeyPerMinute is invalid");
    }
    requireDuration(
        publishTimeout, "publishTimeout", Duration.ofMillis(100), Duration.ofSeconds(30));
    requireDuration(maximumPastAge, "maximumPastAge", Duration.ofMinutes(1), Duration.ofDays(7));
    requireDuration(maximumFutureSkew, "maximumFutureSkew", Duration.ZERO, Duration.ofHours(1));
  }

  private static void requireDuration(
      Duration value, String name, Duration minimum, Duration maximum) {
    if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
