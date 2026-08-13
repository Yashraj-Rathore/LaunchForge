package dev.launchforge.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Versioned edge-to-worker event with tenant scope derived from the authenticated credential. */
public record IngestedEvaluationBatch(
    UUID batchId,
    String eventType,
    int schemaVersion,
    Instant receivedAt,
    UUID organizationId,
    UUID projectId,
    UUID environmentId,
    String projectKey,
    String environmentKey,
    Source source,
    List<EvaluationEvent> events) {
  public static final String EVENT_TYPE = "analytics.evaluation-batch-ingested.v1";
  public static final int SCHEMA_VERSION = 1;
  private static final Pattern RESOURCE_KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");

  public IngestedEvaluationBatch {
    Objects.requireNonNull(batchId, "batchId");
    if (!EVENT_TYPE.equals(eventType)) {
      throw new IllegalArgumentException("eventType is unsupported");
    }
    if (schemaVersion != SCHEMA_VERSION) {
      throw new IllegalArgumentException("schemaVersion is unsupported");
    }
    Objects.requireNonNull(receivedAt, "receivedAt");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(environmentId, "environmentId");
    if (projectKey == null || !RESOURCE_KEY.matcher(projectKey).matches()) {
      throw new IllegalArgumentException("projectKey is invalid");
    }
    if (environmentKey == null || !RESOURCE_KEY.matcher(environmentKey).matches()) {
      throw new IllegalArgumentException("environmentKey is invalid");
    }
    Objects.requireNonNull(source, "source");
    events = List.copyOf(Objects.requireNonNull(events, "events"));
    if (events.isEmpty() || events.size() > EvaluationEventBatch.MAX_EVENTS) {
      throw new IllegalArgumentException("events must contain between 1 and 100 items");
    }
  }

  public enum Source {
    SERVER,
    BROWSER
  }
}
