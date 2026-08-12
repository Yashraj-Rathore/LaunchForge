package dev.launchforge.application.controlplane;

import dev.launchforge.domain.organization.OrganizationId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Safe, tenant-scoped audit projection for the operator console. */
public record AuditEvent(
    UUID id,
    OrganizationId organizationId,
    UUID projectId,
    UUID environmentId,
    String actorSubject,
    String action,
    String targetType,
    UUID targetId,
    String safeSummary,
    String humanReason,
    Long fromRevision,
    Long toRevision,
    UUID correlationId,
    Instant createdAt) {
  public AuditEvent {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(actorSubject, "actorSubject");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(targetType, "targetType");
    Objects.requireNonNull(targetId, "targetId");
    Objects.requireNonNull(correlationId, "correlationId");
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
