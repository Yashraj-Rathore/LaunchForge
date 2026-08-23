package dev.launchforge.configedge.configuration;

import dev.launchforge.contracts.snapshots.SnapshotContract;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.config-edge")
public record ConfigEdgeProperties(
    @DefaultValue("1048576") int maximumSnapshotBytes,
    @DefaultValue("1s") Duration revisionPollInterval,
    @DefaultValue("15s") Duration heartbeatInterval,
    @DefaultValue("1000") int maximumConnections,
    @DefaultValue("5") int maximumConnectionsPerKey,
    @DefaultValue("8") int maximumConcurrentDatabaseFallbacks,
    @DefaultValue("100ms") Duration databaseFallbackAcquireTimeout,
    @DefaultValue("launchforge:config:revision-hints:v1") String invalidationChannel) {
  public ConfigEdgeProperties(
      int maximumSnapshotBytes,
      Duration revisionPollInterval,
      Duration heartbeatInterval,
      int maximumConnections,
      int maximumConnectionsPerKey) {
    this(
        maximumSnapshotBytes,
        revisionPollInterval,
        heartbeatInterval,
        maximumConnections,
        maximumConnectionsPerKey,
        8,
        Duration.ofMillis(100),
        "launchforge:config:revision-hints:v1");
  }

  @ConstructorBinding
  public ConfigEdgeProperties {
    SnapshotContract.requireValidMaximumSnapshotBytes(maximumSnapshotBytes);
    if (revisionPollInterval == null
        || revisionPollInterval.isNegative()
        || revisionPollInterval.isZero()
        || revisionPollInterval.compareTo(Duration.ofSeconds(60)) > 0) {
      throw new IllegalArgumentException(
          "revisionPollInterval must be between zero and 60 seconds");
    }
    if (heartbeatInterval == null
        || heartbeatInterval.isNegative()
        || heartbeatInterval.isZero()
        || heartbeatInterval.compareTo(Duration.ofSeconds(60)) > 0) {
      throw new IllegalArgumentException("heartbeatInterval must be between zero and 60 seconds");
    }
    if (maximumConnections < 1
        || maximumConnections > 100_000
        || maximumConnectionsPerKey < 1
        || maximumConnectionsPerKey > maximumConnections) {
      throw new IllegalArgumentException("SSE connection limits are invalid");
    }
    if (maximumConcurrentDatabaseFallbacks < 1 || maximumConcurrentDatabaseFallbacks > 1000) {
      throw new IllegalArgumentException("maximumConcurrentDatabaseFallbacks is invalid");
    }
    if (databaseFallbackAcquireTimeout == null
        || databaseFallbackAcquireTimeout.isNegative()
        || databaseFallbackAcquireTimeout.compareTo(Duration.ofSeconds(5)) > 0) {
      throw new IllegalArgumentException("databaseFallbackAcquireTimeout is invalid");
    }
    if (invalidationChannel == null
        || invalidationChannel.isBlank()
        || invalidationChannel.length() > 249) {
      throw new IllegalArgumentException("invalidationChannel is invalid");
    }
  }
}
