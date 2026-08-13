package dev.launchforge.contracts.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Version 1 of the immutable environment-revision publication contract. */
public record ConfigRevisionPublishedEvent(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID organizationId,
    UUID projectId,
    UUID environmentId,
    long revision,
    String snapshotChecksum,
    String traceId) {
  public static final String EVENT_TYPE = "config.revision-published.v1";
  public static final int SCHEMA_VERSION = 1;
  private static final Pattern CHECKSUM = Pattern.compile("[0-9a-f]{64}");

  public ConfigRevisionPublishedEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(environmentId, "environmentId");
    if (!EVENT_TYPE.equals(eventType)) {
      throw new IllegalArgumentException("eventType is unsupported");
    }
    if (schemaVersion != SCHEMA_VERSION) {
      throw new IllegalArgumentException("schemaVersion is unsupported");
    }
    if (revision < 1) {
      throw new IllegalArgumentException("revision must be positive");
    }
    if (snapshotChecksum == null || !CHECKSUM.matcher(snapshotChecksum).matches()) {
      throw new IllegalArgumentException("snapshotChecksum is invalid");
    }
    if (traceId == null || traceId.isBlank() || traceId.length() > 128) {
      throw new IllegalArgumentException("traceId is invalid");
    }
  }
}
