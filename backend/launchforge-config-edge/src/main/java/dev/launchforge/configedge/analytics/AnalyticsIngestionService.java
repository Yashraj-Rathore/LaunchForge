package dev.launchforge.configedge.analytics;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.EnvironmentScope;
import dev.launchforge.contracts.events.EvaluationEventBatch;
import dev.launchforge.contracts.events.IngestedEvaluationBatch;
import dev.launchforge.contracts.events.IngestedEvaluationBatch.Source;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "launchforge.analytics.ingestion.enabled", havingValue = "true")
final class AnalyticsIngestionService {
  private final EdgeRepository repository;
  private final AnalyticsBatchCodec codec;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final AnalyticsIngestionProperties properties;
  private final AnalyticsIngestionLimiter limiter;
  private final AnalyticsIngestionMetrics metrics;
  private final Clock clock;

  AnalyticsIngestionService(
      EdgeRepository repository,
      AnalyticsBatchCodec codec,
      KafkaTemplate<String, String> kafkaTemplate,
      AnalyticsIngestionProperties properties,
      AnalyticsIngestionLimiter limiter,
      AnalyticsIngestionMetrics metrics,
      Clock clock) {
    this.repository = repository;
    this.codec = codec;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.limiter = limiter;
    this.metrics = metrics;
    this.clock = clock;
  }

  AcceptedBatch ingest(UUID keyId, UUID environmentId, Source source, byte[] body) {
    AnalyticsIngestionLimiter.Lease lease;
    try {
      lease = limiter.acquire(keyId);
    } catch (AnalyticsCapacityException exception) {
      metrics.shed();
      throw exception;
    }
    try {
      EvaluationEventBatch supplied;
      try {
        supplied = codec.decode(body);
        validateTimes(supplied);
      } catch (InvalidAnalyticsBatchException exception) {
        metrics.rejected();
        throw exception;
      }
      EnvironmentScope scope =
          repository
              .findEnvironmentScope(environmentId)
              .orElseThrow(
                  () -> new AnalyticsUnavailableException("Analytics scope is unavailable"));
      Instant now = clock.instant();
      IngestedEvaluationBatch batch =
          new IngestedEvaluationBatch(
              UUID.randomUUID(),
              IngestedEvaluationBatch.EVENT_TYPE,
              IngestedEvaluationBatch.SCHEMA_VERSION,
              now,
              scope.organizationId(),
              scope.projectId(),
              scope.environmentId(),
              scope.projectKey(),
              scope.environmentKey(),
              source,
              supplied.events());
      try {
        kafkaTemplate
            .send(properties.topic(), environmentId.toString(), codec.encode(batch))
            .get(properties.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        metrics.brokerFailure();
        throw new AnalyticsUnavailableException(
            "Analytics broker publication was interrupted", exception);
      } catch (ExecutionException | TimeoutException exception) {
        metrics.brokerFailure();
        throw new AnalyticsUnavailableException("Analytics broker is unavailable", exception);
      }
      metrics.accepted(batch.events().size());
      return new AcceptedBatch(batch.batchId(), batch.events().size());
    } finally {
      lease.close();
    }
  }

  private void validateTimes(EvaluationEventBatch batch) {
    Instant now = clock.instant();
    Instant oldest = now.minus(properties.maximumPastAge());
    Instant newest = now.plus(properties.maximumFutureSkew());
    if (batch.events().stream()
        .anyMatch(
            event -> event.occurredAt().isBefore(oldest) || event.occurredAt().isAfter(newest))) {
      throw new InvalidAnalyticsBatchException("Analytics event timestamp is outside its bound");
    }
  }

  record AcceptedBatch(UUID batchId, int acceptedEvents) {}
}
