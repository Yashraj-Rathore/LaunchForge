package dev.launchforge.domain.organization;

import java.time.Instant;
import java.util.Objects;

public record Organization(
    OrganizationId id,
    OrganizationSlug slug,
    String name,
    OrganizationStatus status,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  private static final int MAX_NAME_LENGTH = 120;

  public Organization {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(slug, "slug");
    name = requireName(name);
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    if (version < 0) {
      throw new DomainRuleViolationException("Organization version cannot be negative");
    }
    if (updatedAt.isBefore(createdAt)) {
      throw new DomainRuleViolationException("Organization update time cannot precede creation");
    }
  }

  public Organization rename(String newName, long expectedVersion, Instant now) {
    requireExpectedVersion(expectedVersion);
    return new Organization(id, slug, newName, status, version + 1, createdAt, now);
  }

  public Organization transitionTo(
      OrganizationStatus newStatus, long expectedVersion, Instant now) {
    requireExpectedVersion(expectedVersion);
    Objects.requireNonNull(newStatus, "newStatus");
    if (status == OrganizationStatus.CLOSED && newStatus != OrganizationStatus.CLOSED) {
      throw new DomainRuleViolationException("A closed organization cannot be reopened");
    }
    return new Organization(id, slug, name, newStatus, version + 1, createdAt, now);
  }

  private void requireExpectedVersion(long expectedVersion) {
    if (expectedVersion != version) {
      throw new DomainRuleViolationException("Organization version does not match");
    }
  }

  private static String requireName(String value) {
    Objects.requireNonNull(value, "name");
    if (value.isBlank() || !value.equals(value.strip()) || value.length() > MAX_NAME_LENGTH) {
      throw new DomainRuleViolationException(
          "Organization name is blank, non-canonical, or too long");
    }
    return value;
  }
}
