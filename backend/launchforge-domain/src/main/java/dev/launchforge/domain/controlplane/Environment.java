package dev.launchforge.domain.controlplane;

import java.time.Instant;
import java.util.Objects;

public record Environment(
    EnvironmentId id,
    ProjectId projectId,
    ResourceKey key,
    String name,
    Kind kind,
    Status status,
    long currentRevision,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  public Environment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (name == null || name.isBlank() || !name.equals(name.strip()) || name.length() > 120) {
      throw new ControlPlaneRuleViolationException("Environment name is invalid");
    }
    if (currentRevision < 0 || version < 0 || updatedAt.isBefore(createdAt)) {
      throw new ControlPlaneRuleViolationException("Environment version or timestamps are invalid");
    }
  }

  public boolean productionLike() {
    return kind == Kind.PRODUCTION;
  }

  public enum Kind {
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    CUSTOM
  }

  public enum Status {
    ACTIVE,
    ARCHIVED
  }
}
