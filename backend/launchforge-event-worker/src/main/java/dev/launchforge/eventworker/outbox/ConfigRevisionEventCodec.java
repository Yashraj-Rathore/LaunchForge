package dev.launchforge.eventworker.outbox;

import dev.launchforge.contracts.events.ConfigRevisionPublishedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ConfigRevisionEventCodec {
  private final ObjectMapper objectMapper;

  public ConfigRevisionEventCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ConfigRevisionPublishedEvent decode(String json) {
    try {
      JsonNode root = objectMapper.readTree(json);
      return new ConfigRevisionPublishedEvent(
          UUID.fromString(requiredText(root, "eventId")),
          requiredText(root, "eventType"),
          root.path("schemaVersion").intValue(),
          Instant.parse(requiredText(root, "occurredAt")),
          UUID.fromString(requiredText(root, "organizationId")),
          UUID.fromString(requiredText(root, "projectId")),
          UUID.fromString(requiredText(root, "environmentId")),
          root.path("revision").longValue(),
          requiredText(root, "snapshotChecksum"),
          requiredText(root, "traceId"));
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Config revision event is invalid", exception);
    }
  }

  private static String requiredText(JsonNode root, String field) {
    String value = root.path(field).stringValue();
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Required event field is missing: " + field);
    }
    return value;
  }
}
