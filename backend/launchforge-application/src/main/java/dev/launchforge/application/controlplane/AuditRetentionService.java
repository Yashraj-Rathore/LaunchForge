package dev.launchforge.application.controlplane;

import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.OrganizationAccessRepository;
import dev.launchforge.application.organization.OrganizationNotFoundException;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationAbility;
import dev.launchforge.domain.organization.OrganizationId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AuditRetentionService {
  private final OrganizationAccessRepository accessRepository;
  private final ControlPlaneRepository repository;
  private final UnitOfWork unitOfWork;
  private final Clock clock;
  private final Duration minimumAge;
  private final Duration previewTtl;
  private final int maximumBatchSize;
  private final boolean deletionEnabled;

  public AuditRetentionService(
      OrganizationAccessRepository accessRepository,
      ControlPlaneRepository repository,
      UnitOfWork unitOfWork,
      Clock clock,
      Duration minimumAge,
      Duration previewTtl,
      int maximumBatchSize,
      boolean deletionEnabled) {
    this.accessRepository = Objects.requireNonNull(accessRepository, "accessRepository");
    this.repository = Objects.requireNonNull(repository, "repository");
    this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.minimumAge = requirePositive(minimumAge, "minimumAge");
    this.previewTtl = requirePositive(previewTtl, "previewTtl");
    if (maximumBatchSize < 1 || maximumBatchSize > 10_000) {
      throw new IllegalArgumentException("maximumBatchSize is invalid");
    }
    this.maximumBatchSize = maximumBatchSize;
    this.deletionEnabled = deletionEnabled;
  }

  public AuditRetentionPreview preview(
      OidcIdentity actor, OrganizationId organizationId, Instant deleteBefore, int limit) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(deleteBefore, "deleteBefore");
    if (limit < 1 || limit > maximumBatchSize) {
      throw new IllegalArgumentException("Audit retention limit is invalid");
    }
    return unitOfWork.required(
        () -> {
          OrganizationAccess access = requireAccess(actor, organizationId);
          requireRetentionAbility(access);
          Instant now = clock.instant();
          if (deleteBefore.isAfter(now.minus(minimumAge))) {
            throw new IllegalArgumentException("Audit retention cutoff is too recent");
          }
          List<UUID> candidates =
              repository.findAuditRetentionCandidates(access, deleteBefore, limit);
          AuditRetentionPreview preview =
              new AuditRetentionPreview(
                  UUID.randomUUID(),
                  organizationId,
                  deleteBefore,
                  candidates.size(),
                  now,
                  now.plus(previewTtl),
                  null);
          repository.insertAuditRetentionPreview(
              access, actor, new StoredAuditRetentionPreview(preview, candidates));
          return preview;
        });
  }

  public AuditRetentionResult apply(
      OidcIdentity actor,
      OrganizationId organizationId,
      UUID previewId,
      int expectedCandidateCount) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(organizationId, "organizationId");
    Objects.requireNonNull(previewId, "previewId");
    if (expectedCandidateCount < 0 || expectedCandidateCount > maximumBatchSize) {
      throw new IllegalArgumentException("Expected audit candidate count is invalid");
    }
    return unitOfWork.required(
        () -> {
          OrganizationAccess access = requireAccess(actor, organizationId);
          requireRetentionAbility(access);
          if (!deletionEnabled) {
            throw new AuditRetentionConflictException("Audit retention deletion is disabled");
          }
          StoredAuditRetentionPreview stored =
              repository
                  .lockAuditRetentionPreview(access, previewId)
                  .orElseThrow(ControlPlaneNotFoundException::new);
          AuditRetentionPreview preview = stored.preview();
          Instant now = clock.instant();
          if (preview.appliedAt() != null) {
            throw new AuditRetentionConflictException(
                "Audit retention preview was already applied");
          }
          if (now.isAfter(preview.expiresAt())) {
            throw new AuditRetentionConflictException("Audit retention preview expired");
          }
          if (preview.candidateCount() != expectedCandidateCount) {
            throw new AuditRetentionConflictException(
                "Audit retention confirmation does not match");
          }
          int deleted = repository.applyAuditRetentionPreview(access, actor, stored, now);
          AuditRetentionPreview applied =
              new AuditRetentionPreview(
                  preview.id(),
                  preview.organizationId(),
                  preview.deleteBefore(),
                  preview.candidateCount(),
                  preview.createdAt(),
                  preview.expiresAt(),
                  now);
          return new AuditRetentionResult(applied, deleted);
        });
  }

  private OrganizationAccess requireAccess(OidcIdentity actor, OrganizationId organizationId) {
    return accessRepository
        .findFor(actor, organizationId)
        .orElseThrow(OrganizationNotFoundException::new);
  }

  private static void requireRetentionAbility(OrganizationAccess access) {
    if (!access.actorRole().allows(OrganizationAbility.MANAGE_AUDIT_RETENTION)) {
      throw new OperationForbiddenException();
    }
  }

  private static Duration requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
