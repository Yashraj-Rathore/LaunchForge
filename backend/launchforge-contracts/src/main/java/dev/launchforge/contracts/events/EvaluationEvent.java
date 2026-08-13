package dev.launchforge.contracts.events;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Privacy-minimized evaluation event supplied by an opted-in SDK. */
public record EvaluationEvent(
    UUID eventId,
    Instant occurredAt,
    String flagKey,
    String variationId,
    String reason,
    long revision) {
  private static final Pattern RESOURCE_KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
  private static final Set<String> REASONS =
      Set.of(
          "FLAG_NOT_FOUND",
          "FLAG_DISABLED",
          "DEFAULT_VARIATION",
          "RULE_MATCH",
          "ROLLOUT_MATCH",
          "MISSING_ROLLOUT_KEY",
          "TYPE_MISMATCH",
          "INVALID_CONFIG",
          "SNAPSHOT_UNAVAILABLE",
          "ERROR_DEFAULT");

  public EvaluationEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(occurredAt, "occurredAt");
    if (flagKey == null || !RESOURCE_KEY.matcher(flagKey).matches()) {
      throw new IllegalArgumentException("flagKey is invalid");
    }
    if (variationId != null && !RESOURCE_KEY.matcher(variationId).matches()) {
      throw new IllegalArgumentException("variationId is invalid");
    }
    if (!REASONS.contains(reason)) {
      throw new IllegalArgumentException("reason is invalid");
    }
    if (revision < 1) {
      throw new IllegalArgumentException("revision must be positive");
    }
  }
}
