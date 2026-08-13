package dev.launchforge.contracts.events;

import java.util.List;
import java.util.Objects;

/** Versioned SDK-to-edge analytics batch. Context and subject data are intentionally absent. */
public record EvaluationEventBatch(
    String eventType, int schemaVersion, List<EvaluationEvent> events) {
  public static final String EVENT_TYPE = "analytics.evaluation-batch.v1";
  public static final int SCHEMA_VERSION = 1;
  public static final int MAX_EVENTS = 100;

  public EvaluationEventBatch {
    if (!EVENT_TYPE.equals(eventType)) {
      throw new IllegalArgumentException("eventType is unsupported");
    }
    if (schemaVersion != SCHEMA_VERSION) {
      throw new IllegalArgumentException("schemaVersion is unsupported");
    }
    events = List.copyOf(Objects.requireNonNull(events, "events"));
    if (events.isEmpty() || events.size() > MAX_EVENTS) {
      throw new IllegalArgumentException("events must contain between 1 and 100 items");
    }
  }
}
