package dev.launchforge.eventworker.outbox;

import dev.launchforge.contracts.events.ConfigRevisionPublishedEvent;
import dev.launchforge.eventworker.configuration.DistributionProperties;
import dev.launchforge.eventworker.configuration.WorkerSchedulingConfiguration;
import dev.launchforge.eventworker.observability.DistributionMetrics;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private final JdbcOutboxRepository repository;
  private final ConfigRevisionEventCodec codec;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final DistributionProperties properties;
  private final DistributionMetrics metrics;
  private final String owner = UUID.randomUUID().toString();

  public OutboxPublisher(
      JdbcOutboxRepository repository,
      ConfigRevisionEventCodec codec,
      KafkaTemplate<String, String> kafkaTemplate,
      DistributionProperties properties,
      DistributionMetrics metrics) {
    this.repository = repository;
    this.codec = codec;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Scheduled(
      fixedDelayString = "${launchforge.distribution.outbox-poll-interval:500ms}",
      scheduler = WorkerSchedulingConfiguration.CONFIGURATION_SCHEDULER)
  public void publishAvailable() {
    for (OutboxEvent row :
        repository.lease(owner, properties.outboxBatchSize(), properties.outboxLease())) {
      publish(row);
    }
    metrics.recordOutboxStats(repository.stats());
  }

  private void publish(OutboxEvent row) {
    long started = System.nanoTime();
    ConfigRevisionPublishedEvent event;
    try {
      event = codec.decode(row.payload());
      if (!event.environmentId().equals(row.environmentId())
          || event.revision() != row.revision()) {
        throw new IllegalArgumentException("Outbox envelope does not match its payload");
      }
    } catch (RuntimeException exception) {
      if (repository.markFailed(row.id(), owner, "OUTBOX_EVENT_INVALID")) {
        metrics.outboxFailed();
      }
      metrics.recordOutboxDuration("failed", System.nanoTime() - started);
      return;
    }
    try {
      kafkaTemplate
          .send(properties.topic(), event.environmentId().toString(), row.payload())
          .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (repository.markPublished(row.id(), owner)) {
        metrics.outboxPublished();
      }
      metrics.recordOutboxDuration("published", System.nanoTime() - started);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      retry(row, "KAFKA_PUBLISH_INTERRUPTED");
      metrics.recordOutboxDuration("retry", System.nanoTime() - started);
    } catch (Exception exception) {
      retry(row, "KAFKA_PUBLISH_FAILED");
      metrics.recordOutboxDuration("retry", System.nanoTime() - started);
    }
  }

  private void retry(OutboxEvent row, String code) {
    if (repository.releaseForRetry(row.id(), owner, retryDelay(row.attempt()), code)) {
      metrics.outboxRetry();
    }
  }

  private Duration retryDelay(int attempt) {
    long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 20);
    long boundedMillis =
        Math.min(
            properties.retryMaximumBackoff().toMillis(),
            properties.retryInitialBackoff().toMillis() * multiplier);
    return Duration.ofMillis(boundedMillis);
  }
}
