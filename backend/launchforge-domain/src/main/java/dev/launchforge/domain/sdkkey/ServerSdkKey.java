package dev.launchforge.domain.sdkkey;

import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.ProjectId;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.Objects;

public record ServerSdkKey(
    SdkKeyId id,
    OrganizationId organizationId,
    ProjectId projectId,
    EnvironmentId environmentId,
    String name,
    String lookupId,
    String fingerprint,
    String pepperVersion,
    SdkKeyStatus status,
    Instant expiresAt,
    Instant createdAt,
    Instant lastUsedAt,
    Instant revokedAt,
    SdkKeyId rotatedFromId) {
  public ServerSdkKey {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(environmentId, "environmentId");
    name = requireText(name, 120, "name");
    lookupId = requirePattern(lookupId, "[A-Za-z0-9_-]{16}", "lookupId");
    fingerprint = requireText(fingerprint, 64, "fingerprint");
    pepperVersion = requirePattern(pepperVersion, "[A-Za-z0-9._-]{1,32}", "pepperVersion");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    if (expiresAt != null && expiresAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("expiresAt must not precede createdAt");
    }
    if (revokedAt != null && revokedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("revokedAt must not precede createdAt");
    }
  }

  public boolean usableAt(Instant instant) {
    Objects.requireNonNull(instant, "instant");
    return status == SdkKeyStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(instant));
  }

  private static String requireText(String value, int maximum, String field) {
    if (value == null
        || value.isBlank()
        || !value.equals(value.strip())
        || value.length() > maximum) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value;
  }

  private static String requirePattern(String value, String pattern, String field) {
    if (value == null || !value.matches(pattern)) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value;
  }
}
