package dev.launchforge.controlapi.audit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.audit.retention")
public record AuditRetentionProperties(
    @DefaultValue("false") boolean deletionEnabled,
    @DefaultValue("365d") Duration minimumAge,
    @DefaultValue("15m") Duration previewTtl,
    @DefaultValue("1000") int maximumBatchSize) {
  public AuditRetentionProperties {
    if (minimumAge == null || minimumAge.isZero() || minimumAge.isNegative()) {
      throw new IllegalArgumentException("minimumAge must be positive");
    }
    if (previewTtl == null
        || previewTtl.compareTo(Duration.ofMinutes(1)) < 0
        || previewTtl.compareTo(Duration.ofHours(1)) > 0) {
      throw new IllegalArgumentException("previewTtl is invalid");
    }
    if (maximumBatchSize < 1 || maximumBatchSize > 10_000) {
      throw new IllegalArgumentException("maximumBatchSize is invalid");
    }
  }
}
