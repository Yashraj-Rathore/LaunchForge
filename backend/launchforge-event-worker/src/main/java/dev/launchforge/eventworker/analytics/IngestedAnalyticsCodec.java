package dev.launchforge.eventworker.analytics;

import dev.launchforge.contracts.events.IngestedEvaluationBatch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "launchforge.analytics.worker.enabled", havingValue = "true")
final class IngestedAnalyticsCodec {
  private final ObjectMapper objectMapper;

  IngestedAnalyticsCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  IngestedEvaluationBatch decode(String json) {
    if (json == null || json.isBlank() || json.length() > 512 * 1024) {
      throw new IllegalArgumentException("Analytics Kafka message is outside its bound");
    }
    try {
      return objectMapper.readValue(json, IngestedEvaluationBatch.class);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Analytics Kafka message is invalid", exception);
    }
  }
}
