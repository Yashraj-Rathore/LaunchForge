package dev.launchforge.domain.controlplane;

import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.Objects;

public record Project(
    ProjectId id,
    OrganizationId organizationId,
    ResourceKey key,
    String name,
    String description,
    Status status,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  public static final int MAX_NAME_LENGTH = 120;
  public static final int MAX_DESCRIPTION_LENGTH = 500;

  public Project {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(key, "key");
    name = requireText(name, "Project name", MAX_NAME_LENGTH);
    description = canonicalOptional(description, MAX_DESCRIPTION_LENGTH);
    Objects.requireNonNull(status, "status");
    requireVersionAndTimes(version, createdAt, updatedAt);
  }

  public enum Status {
    ACTIVE,
    ARCHIVED
  }

  private static String requireText(String value, String label, int maximumLength) {
    Objects.requireNonNull(value, label);
    if (value.isBlank() || !value.equals(value.strip()) || value.length() > maximumLength) {
      throw new ControlPlaneRuleViolationException(label + " is invalid");
    }
    return value;
  }

  private static String canonicalOptional(String value, int maximumLength) {
    if (value == null) {
      return null;
    }
    if (!value.equals(value.strip()) || value.length() > maximumLength) {
      throw new ControlPlaneRuleViolationException("Project description is invalid");
    }
    return value;
  }

  private static void requireVersionAndTimes(long version, Instant createdAt, Instant updatedAt) {
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (version < 0 || updatedAt.isBefore(createdAt)) {
      throw new ControlPlaneRuleViolationException("Project version or timestamps are invalid");
    }
  }
}
