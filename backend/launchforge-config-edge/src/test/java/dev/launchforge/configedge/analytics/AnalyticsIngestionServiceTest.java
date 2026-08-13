package dev.launchforge.configedge.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.EnvironmentScope;
import dev.launchforge.contracts.events.IngestedEvaluationBatch.Source;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

class AnalyticsIngestionServiceTest {
  @Test
  void derivesTenantScopeFromCredentialAndPublishesNoRawContext() {
    AnalyticsIngestionProperties properties = properties();
    Clock clock = Clock.fixed(Instant.parse("2026-08-13T12:01:00Z"), ZoneOffset.UTC);
    EdgeRepository repository = mock(EdgeRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    UUID keyId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID environmentId = UUID.randomUUID();
    when(repository.findEnvironmentScope(environmentId))
        .thenReturn(
            Optional.of(
                new EnvironmentScope(
                    organizationId, projectId, environmentId, "storefront", "production")));
    when(kafka.send(eq(properties.topic()), eq(environmentId.toString()), anyString()))
        .thenReturn(CompletableFuture.completedFuture(null));
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ObjectMapper mapper = new ObjectMapper();
    AnalyticsIngestionService service =
        new AnalyticsIngestionService(
            repository,
            new AnalyticsBatchCodec(mapper, properties),
            kafka,
            properties,
            new AnalyticsIngestionLimiter(properties, clock),
            new AnalyticsIngestionMetrics(registry),
            clock);

    var accepted =
        service.ingest(
            keyId, environmentId, Source.SERVER, batch().getBytes(StandardCharsets.UTF_8));

    assertEquals(1, accepted.acceptedEvents());
    var payload = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(kafka).send(eq(properties.topic()), eq(environmentId.toString()), payload.capture());
    assertEquals(
        organizationId.toString(),
        mapper.readTree(payload.getValue()).path("organizationId").stringValue());
    assertFalse(payload.getValue().contains("private-subject"));
    assertFalse(payload.getValue().contains("attributes"));
  }

  private static String batch() {
    return """
        {"eventType":"analytics.evaluation-batch.v1","schemaVersion":1,"events":[{
          "eventId":"5d63160a-c7e0-45fe-aa65-7af23bc9f860",
          "occurredAt":"2026-08-13T12:00:00Z","flagKey":"checkout",
          "variationId":"on","reason":"RULE_MATCH","revision":4}]}
        """;
  }

  private static AnalyticsIngestionProperties properties() {
    return new AnalyticsIngestionProperties(
        true,
        "launchforge.analytics.evaluations.v1",
        262_144,
        100,
        4,
        60,
        Duration.ofSeconds(1),
        Duration.ofHours(24),
        Duration.ofMinutes(5));
  }
}
