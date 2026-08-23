package dev.launchforge.eventworker.analytics;

import dev.launchforge.contracts.events.EvaluationEvent;
import dev.launchforge.contracts.events.IngestedEvaluationBatch;
import dev.launchforge.eventworker.configuration.AnalyticsWorkerProperties;
import dev.launchforge.eventworker.configuration.WorkerSchedulingConfiguration;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
final class AnalyticsEventBuffer {
  private final IngestedAnalyticsCodec codec;
  private final AnalyticsStore store;
  private final AnalyticsWorkerMetrics metrics;
  private final AnalyticsWorkerProperties properties;
  private final ArrayBlockingQueue<AnalyticsEventRow> queue;

  AnalyticsEventBuffer(
      IngestedAnalyticsCodec codec,
      AnalyticsStore store,
      AnalyticsWorkerMetrics metrics,
      AnalyticsWorkerProperties properties) {
    this.codec = codec;
    this.store = store;
    this.metrics = metrics;
    this.properties = properties;
    queue = new ArrayBlockingQueue<>(properties.queueCapacity());
  }

  @KafkaListener(
      topics = "${launchforge.analytics.worker.topic:launchforge.analytics.evaluations.v1}",
      groupId = "${launchforge.analytics.worker.consumer-group:launchforge-analytics-writer-v1}")
  void receive(ConsumerRecord<String, String> record) {
    try {
      IngestedEvaluationBatch batch = codec.decode(record.value());
      if (!batch.environmentId().toString().equals(record.key())) {
        throw new IllegalArgumentException("Kafka key does not match analytics environment");
      }
      List<AnalyticsEventRow> rows =
          batch.events().stream().map(event -> row(batch, event)).toList();
      synchronized (queue) {
        if (queue.remainingCapacity() < rows.size()) {
          metrics.dropped(rows.size());
          return;
        }
        queue.addAll(rows);
        metrics.depth(queue.size());
      }
      metrics.enqueued(rows.size());
    } catch (IllegalArgumentException exception) {
      metrics.invalid();
    }
  }

  @Scheduled(
      fixedDelayString = "${launchforge.analytics.worker.flush-interval:1s}",
      scheduler = WorkerSchedulingConfiguration.ANALYTICS_SCHEDULER)
  void flush() {
    List<AnalyticsEventRow> rows = new ArrayList<>(properties.insertBatchSize());
    queue.drainTo(rows, properties.insertBatchSize());
    metrics.depth(queue.size());
    if (rows.isEmpty()) {
      return;
    }
    Timer.Sample sample = metrics.startInsert();
    try {
      store.insert(rows);
      metrics.stored(rows.size());
      metrics.finishInsert(sample, "success");
    } catch (RuntimeException exception) {
      metrics.storeFailure();
      metrics.dropped(rows.size());
      metrics.finishInsert(sample, "failure");
    }
  }

  private static AnalyticsEventRow row(IngestedEvaluationBatch batch, EvaluationEvent event) {
    return new AnalyticsEventRow(
        event.eventId(),
        event.occurredAt(),
        batch.receivedAt(),
        batch.organizationId(),
        batch.projectId(),
        batch.environmentId(),
        batch.projectKey(),
        batch.environmentKey(),
        event.flagKey(),
        event.variationId(),
        event.reason(),
        event.revision(),
        batch.source().name());
  }
}
