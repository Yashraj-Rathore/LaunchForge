package dev.launchforge.application.controlplane;

import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditRetentionPreview(
    UUID id,
    OrganizationId organizationId,
    Instant deleteBefore,
    int candidateCount,
    Instant createdAt,
    Instant expiresAt,
    Instant appliedAt) {
  public AuditRetentionPreview {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(deleteBefore, "deleteBefore");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (candidateCount < 0) {
      throw new IllegalArgumentException("candidateCount cannot be negative");
    }
  }
}
