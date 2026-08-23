package dev.launchforge.eventworker.configuration;

import dev.launchforge.contracts.snapshots.SnapshotContract;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.distribution")
public record DistributionProperties(
    @DefaultValue("launchforge.config.revision-published.v1") String topic,
    @DefaultValue("launchforge-config-projector-v1") String consumerGroup,
    @DefaultValue("launchforge:config:revision-hints:v1") String invalidationChannel,
    @DefaultValue("12") int topicPartitions,
    @DefaultValue("1") int topicReplicas,
    @DefaultValue("50") int outboxBatchSize,
    @DefaultValue("30s") Duration outboxLease,
    @DefaultValue("10s") Duration publishTimeout,
    @DefaultValue("1s") Duration retryInitialBackoff,
    @DefaultValue("1m") Duration retryMaximumBackoff,
    @DefaultValue("200") int reconciliationBatchSize,
    @DefaultValue("1048576") int maximumSnapshotBytes) {
  public DistributionProperties {
    requireText(topic, "topic");
    requireText(consumerGroup, "consumerGroup");
    requireText(invalidationChannel, "invalidationChannel");
    if (topicPartitions < 1 || topicPartitions > 256 || topicReplicas < 1) {
      throw new IllegalArgumentException("Kafka topic settings are invalid");
    }
    if (outboxBatchSize < 1 || outboxBatchSize > 1000) {
      throw new IllegalArgumentException("outboxBatchSize is invalid");
    }
    requireDuration(outboxLease, "outboxLease", Duration.ofSeconds(1), Duration.ofMinutes(10));
    requireDuration(
        publishTimeout, "publishTimeout", Duration.ofMillis(100), Duration.ofMinutes(1));
    requireDuration(
        retryInitialBackoff, "retryInitialBackoff", Duration.ofMillis(100), Duration.ofMinutes(1));
    requireDuration(
        retryMaximumBackoff, "retryMaximumBackoff", retryInitialBackoff, Duration.ofHours(1));
    if (reconciliationBatchSize < 1 || reconciliationBatchSize > 10_000) {
      throw new IllegalArgumentException("reconciliationBatchSize is invalid");
    }
    SnapshotContract.requireValidMaximumSnapshotBytes(maximumSnapshotBytes);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank() || value.length() > 249) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }

  private static void requireDuration(
      Duration value, String name, Duration minimum, Duration maximum) {
    if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new IllegalArgumentException(name + " is invalid");
    }
  }
}
