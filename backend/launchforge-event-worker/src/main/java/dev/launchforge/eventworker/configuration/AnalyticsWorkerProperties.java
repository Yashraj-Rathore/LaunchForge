package dev.launchforge.eventworker.configuration;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("launchforge.analytics.worker")
public record AnalyticsWorkerProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("launchforge.analytics.evaluations.v1") String topic,
    @DefaultValue("launchforge-analytics-writer-v1") String consumerGroup,
    @DefaultValue("12") int topicPartitions,
    @DefaultValue("1") int topicReplicas,
    @DefaultValue("10000") int queueCapacity,
    @DefaultValue("500") int insertBatchSize,
    @DefaultValue("1s") Duration flushInterval,
    @DefaultValue("http://localhost:58123") URI clickHouseUrl,
    @DefaultValue("launchforge") String clickHouseUser,
    @DefaultValue("") String clickHousePassword,
    @DefaultValue("2s") Duration connectTimeout,
    @DefaultValue("5s") Duration requestTimeout) {
  public AnalyticsWorkerProperties {
    requireText(topic, "topic", 249);
    requireText(consumerGroup, "consumerGroup", 249);
    if (topicPartitions < 1 || topicPartitions > 256 || topicReplicas < 1) {
      throw new IllegalArgumentException("analytics Kafka topic settings are invalid");
    }
    if (queueCapacity < 100 || queueCapacity > 1_000_000) {
      throw new IllegalArgumentException("queueCapacity is invalid");
    }
    if (insertBatchSize < 1 || insertBatchSize > 10_000 || insertBatchSize > queueCapacity) {
      throw new IllegalArgumentException("insertBatchSize is invalid");
    }
    requireDuration(flushInterval, "flushInterval", Duration.ofMillis(100), Duration.ofMinutes(5));
    if (clickHouseUrl == null
        || !clickHouseUrl.isAbsolute()
        || !("http".equalsIgnoreCase(clickHouseUrl.getScheme())
            || "https".equalsIgnoreCase(clickHouseUrl.getScheme()))
        || clickHouseUrl.getHost() == null
        || clickHouseUrl.getRawUserInfo() != null
        || clickHouseUrl.getRawQuery() != null
        || clickHouseUrl.getRawFragment() != null) {
      throw new IllegalArgumentException("clickHouseUrl is invalid");
    }
    requireText(clickHouseUser, "clickHouseUser", 128);
    if (clickHousePassword == null || clickHousePassword.length() > 4096) {
      throw new IllegalArgumentException("clickHousePassword is invalid");
    }
    requireDuration(
        connectTimeout, "connectTimeout", Duration.ofMillis(100), Duration.ofSeconds(30));
    requireDuration(
        requestTimeout, "requestTimeout", Duration.ofMillis(100), Duration.ofMinutes(1));
  }

  private static void requireText(String value, String name, int maximum) {
    if (value == null || value.isBlank() || value.length() > maximum) {
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
