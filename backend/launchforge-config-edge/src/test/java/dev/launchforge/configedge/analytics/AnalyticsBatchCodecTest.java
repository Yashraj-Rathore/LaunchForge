package dev.launchforge.configedge.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalyticsBatchCodecTest {
  private final AnalyticsBatchCodec codec =
      new AnalyticsBatchCodec(new ObjectMapper(), properties());

  @Test
  void acceptsBoundedPrivacyMinimizedBatch() {
    var batch = codec.decode(validBatch().getBytes(java.nio.charset.StandardCharsets.UTF_8));

    assertEquals(1, batch.events().size());
    assertEquals("checkout", batch.events().getFirst().flagKey());
  }

  @Test
  void rejectsRawContextUnknownFieldsAndOversizedBodies() {
    String withContext =
        validBatch().replace("\"revision\":4", "\"revision\":4,\"context\":{\"email\":\"secret\"}");

    assertThrows(
        InvalidAnalyticsBatchException.class,
        () -> codec.decode(withContext.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    assertThrows(
        InvalidAnalyticsBatchException.class,
        () -> codec.decode(new byte[properties().maximumRequestBytes() + 1]));
  }

  private static String validBatch() {
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
