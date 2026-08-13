package dev.launchforge.configedge.analytics;

import dev.launchforge.configedge.configuration.AnalyticsIngestionProperties;
import dev.launchforge.contracts.events.EvaluationEvent;
import dev.launchforge.contracts.events.EvaluationEventBatch;
import dev.launchforge.contracts.events.IngestedEvaluationBatch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.ingestion.enabled", havingValue = "true")
final class AnalyticsBatchCodec {
  private static final Set<String> BATCH_FIELDS = Set.of("eventType", "schemaVersion", "events");
  private static final Set<String> EVENT_FIELDS =
      Set.of("eventId", "occurredAt", "flagKey", "variationId", "reason", "revision");
  private final ObjectMapper objectMapper;
  private final AnalyticsIngestionProperties properties;

  AnalyticsBatchCodec(ObjectMapper objectMapper, AnalyticsIngestionProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  EvaluationEventBatch decode(byte[] body) {
    if (body.length == 0 || body.length > properties.maximumRequestBytes()) {
      throw new InvalidAnalyticsBatchException("Analytics request body is outside its byte bound");
    }
    try {
      JsonNode root = objectMapper.readTree(body);
      requireObjectWithFields(root, BATCH_FIELDS, "batch");
      JsonNode eventsNode = root.get("events");
      if (eventsNode == null || !eventsNode.isArray()) {
        throw new IllegalArgumentException("events must be an array");
      }
      List<EvaluationEvent> events = new ArrayList<>();
      for (JsonNode node : eventsNode) {
        requireObjectWithFields(node, EVENT_FIELDS, "event");
        JsonNode variation = node.get("variationId");
        events.add(
            new EvaluationEvent(
                UUID.fromString(requiredText(node, "eventId")),
                Instant.parse(requiredText(node, "occurredAt")),
                requiredText(node, "flagKey"),
                variation == null || variation.isNull() ? null : requiredText(node, "variationId"),
                requiredText(node, "reason"),
                requiredLong(node, "revision")));
      }
      EvaluationEventBatch batch =
          new EvaluationEventBatch(
              requiredText(root, "eventType"), requiredInt(root, "schemaVersion"), events);
      if (batch.events().size() > properties.maximumBatchSize()) {
        throw new IllegalArgumentException("Batch exceeds the configured event limit");
      }
      return batch;
    } catch (RuntimeException exception) {
      throw new InvalidAnalyticsBatchException("Analytics batch is invalid", exception);
    }
  }

  String encode(IngestedEvaluationBatch batch) {
    try {
      return objectMapper.writeValueAsString(batch);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Validated analytics batch could not be encoded", exception);
    }
  }

  private static void requireObjectWithFields(JsonNode node, Set<String> allowed, String label) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException(label + " must be an object");
    }
    Set<String> actual = new HashSet<>(node.propertyNames());
    if (!allowed.containsAll(actual)) {
      throw new IllegalArgumentException(label + " contains an unsupported field");
    }
  }

  private static String requiredText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isString() || value.stringValue().isBlank()) {
      throw new IllegalArgumentException(field + " must be a non-blank string");
    }
    return value.stringValue();
  }

  private static int requiredInt(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw new IllegalArgumentException(field + " must be an integer");
    }
    return value.intValue();
  }

  private static long requiredLong(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw new IllegalArgumentException(field + " must be an integer");
    }
    return value.longValue();
  }
}
