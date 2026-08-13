package dev.launchforge.eventworker.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.launchforge.contracts.events.EvaluationEvent;
import dev.launchforge.contracts.events.IngestedEvaluationBatch;
import dev.launchforge.contracts.events.IngestedEvaluationBatch.Source;
import dev.launchforge.eventworker.configuration.AnalyticsWorkerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class AnalyticsEventBufferTest {
  @Test
  void clickHouseFailureDropsBoundedBatchWithoutThrowingIntoKafkaListener() {
    IngestedAnalyticsCodec codec = mock(IngestedAnalyticsCodec.class);
    IngestedEvaluationBatch batch = batch();
    when(codec.decode("valid")).thenReturn(batch);
    AnalyticsStore unavailable =
        rows -> {
          throw new IllegalStateException("ClickHouse down");
        };
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AnalyticsEventBuffer buffer =
        new AnalyticsEventBuffer(
            codec, unavailable, new AnalyticsWorkerMetrics(registry), properties());

    buffer.receive(
        new ConsumerRecord<>("analytics", 0, 0, batch.environmentId().toString(), "valid"));
    buffer.flush();

    assertEquals(
        1.0,
        registry
            .get("launchforge.analytics.worker")
            .tag("outcome", "store_failure")
            .counter()
            .count());
    assertEquals(
        1.0,
        registry.get("launchforge.analytics.worker").tag("outcome", "dropped").counter().count());
    assertEquals(
        1L,
        registry
            .get("launchforge.analytics.clickhouse.insert.duration")
            .tag("outcome", "failure")
            .timer()
            .count());
    assertEquals(0.0, registry.get("launchforge.analytics.queue.depth").gauge().value());
  }

  private static IngestedEvaluationBatch batch() {
    UUID environmentId = UUID.randomUUID();
    return new IngestedEvaluationBatch(
        UUID.randomUUID(),
        IngestedEvaluationBatch.EVENT_TYPE,
        IngestedEvaluationBatch.SCHEMA_VERSION,
        Instant.parse("2026-08-13T12:01:00Z"),
        UUID.randomUUID(),
        UUID.randomUUID(),
        environmentId,
        "storefront",
        "production",
        Source.SERVER,
        List.of(
            new EvaluationEvent(
                UUID.randomUUID(),
                Instant.parse("2026-08-13T12:00:00Z"),
                "checkout",
                "on",
                "RULE_MATCH",
                4)));
  }

  private static AnalyticsWorkerProperties properties() {
    return new AnalyticsWorkerProperties(
        true,
        "launchforge.analytics.evaluations.v1",
        "analytics-test",
        1,
        1,
        100,
        10,
        Duration.ofSeconds(1),
        URI.create("http://127.0.0.1:1"),
        "launchforge",
        "test",
        Duration.ofMillis(100),
        Duration.ofMillis(100));
  }
}
