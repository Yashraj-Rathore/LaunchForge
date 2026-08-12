package dev.launchforge.application.controlplane;

import java.time.Instant;
import java.util.UUID;

/** Bounded audit filters. Tenant scope is supplied separately from authenticated membership. */
public record AuditQuery(
    UUID projectId,
    UUID environmentId,
    String actorSubject,
    String action,
    Instant from,
    Instant to,
    int limit) {
  public AuditQuery {
    actorSubject = optional(actorSubject, 255, "Audit actor filter");
    action = optional(action, 64, "Audit action filter");
    if (action != null && !action.matches("^[A-Z][A-Z0-9_]{0,63}$")) {
      throw new IllegalArgumentException("Audit action filter is invalid");
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("Audit time range is invalid");
    }
    if (limit < 1 || limit > 200) {
      throw new IllegalArgumentException("Audit limit must be between 1 and 200");
    }
  }

  private static String optional(String value, int maximum, String label) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.length() > maximum) {
      throw new IllegalArgumentException(label + " is too long");
    }
    return normalized;
  }
}
