package dev.launchforge.application.sdkkey;

import dev.launchforge.application.controlplane.ControlPlaneNotFoundException;
import dev.launchforge.application.controlplane.ControlPlaneRepository;
import dev.launchforge.application.controlplane.ControlPlaneRepository.ScopedEnvironment;
import dev.launchforge.application.organization.OperationForbiddenException;
import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.controlplane.EnvironmentId;
import dev.launchforge.domain.controlplane.Project;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.organization.OrganizationAbility;
import dev.launchforge.domain.organization.OrganizationRole;
import dev.launchforge.domain.organization.OrganizationStatus;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.SdkKeyStatus;
import dev.launchforge.domain.sdkkey.ServerSdkKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class SdkKeyService {
  public static final Duration MAXIMUM_ROTATION_OVERLAP = Duration.ofHours(24);

  private final ControlPlaneRepository controlPlaneRepository;
  private final SdkKeyRepository repository;
  private final ServerSdkKeyGenerator generator;
  private final UnitOfWork unitOfWork;
  private final Clock clock;

  public SdkKeyService(
      ControlPlaneRepository controlPlaneRepository,
      SdkKeyRepository repository,
      ServerSdkKeyGenerator generator,
      UnitOfWork unitOfWork,
      Clock clock) {
    this.controlPlaneRepository = Objects.requireNonNull(controlPlaneRepository);
    this.repository = Objects.requireNonNull(repository);
    this.generator = Objects.requireNonNull(generator);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
    this.clock = Objects.requireNonNull(clock);
  }

  public IssuedServerSdkKey create(
      OidcIdentity actor, EnvironmentId environmentId, String name, Instant expiresAt) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(environmentId, "environmentId");
    return unitOfWork.required(
        () -> {
          ScopedEnvironment scoped = requireLockedEnvironment(actor, environmentId);
          requireManageKeys(scoped.access(), scoped.environment());
          requireActive(scoped);
          return issue(scoped, actor, name, expiresAt, null, "SDK_KEY_CREATED");
        });
  }

  public List<ServerSdkKey> list(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireManageKeys(scoped.access(), scoped.environment());
    return repository.findForEnvironment(scoped.access(), scoped.environment());
  }

  public IssuedServerSdkKey rotate(
      OidcIdentity actor, SdkKeyId keyId, Duration overlap, Instant replacementExpiresAt) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(keyId, "keyId");
    requireOverlap(overlap);
    return unitOfWork.required(
        () -> {
          SdkKeyRepository.ScopedSdkKey scoped =
              repository.lockFor(actor, keyId).orElseThrow(ControlPlaneNotFoundException::new);
          ScopedEnvironment environment =
              requireLockedEnvironment(actor, scoped.key().environmentId());
          requireManageKeys(scoped.access(), environment.environment());
          if (scoped.key().status() == SdkKeyStatus.REVOKED) {
            throw new SdkKeyConflictException("A revoked SDK key cannot be rotated");
          }
          Instant now = clock.instant();
          Instant oldExpiry = overlap.isZero() ? now : now.plus(overlap);
          if (scoped.key().expiresAt() != null && scoped.key().expiresAt().isBefore(oldExpiry)) {
            oldExpiry = scoped.key().expiresAt();
          }
          repository.endValidity(
              scoped.access(), actor, scoped.key(), oldExpiry, overlap.isZero(), "SDK_KEY_ROTATED");
          requireActive(environment);
          return issue(
              environment,
              actor,
              scoped.key().name(),
              replacementExpiresAt,
              scoped.key().id(),
              "SDK_KEY_REPLACEMENT_CREATED");
        });
  }

  public void revoke(OidcIdentity actor, SdkKeyId keyId) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(keyId, "keyId");
    unitOfWork.required(
        () -> {
          SdkKeyRepository.ScopedSdkKey scoped =
              repository.lockFor(actor, keyId).orElseThrow(ControlPlaneNotFoundException::new);
          ScopedEnvironment environment =
              requireLockedEnvironment(actor, scoped.key().environmentId());
          requireManageKeys(scoped.access(), environment.environment());
          if (scoped.key().status() != SdkKeyStatus.REVOKED) {
            repository.endValidity(
                scoped.access(), actor, scoped.key(), clock.instant(), true, "SDK_KEY_REVOKED");
          }
          return null;
        });
  }

  private IssuedServerSdkKey issue(
      ScopedEnvironment scoped,
      OidcIdentity actor,
      String name,
      Instant expiresAt,
      SdkKeyId rotatedFromId,
      String auditAction) {
    Instant now = clock.instant();
    if (expiresAt != null && !expiresAt.isAfter(now)) {
      throw new IllegalArgumentException("SDK key expiry must be in the future");
    }
    GeneratedSdkKey generated = generator.generate();
    ServerSdkKey key =
        new ServerSdkKey(
            SdkKeyId.random(),
            scoped.access().organization().id(),
            scoped.project().id(),
            scoped.environment().id(),
            name,
            generated.lookupId(),
            generated.fingerprint(),
            generated.pepperVersion(),
            SdkKeyStatus.ACTIVE,
            expiresAt,
            now,
            null,
            null,
            rotatedFromId);
    repository.insert(scoped.access(), actor, key, generated.verifier(), auditAction);
    return new IssuedServerSdkKey(key, generated.credential());
  }

  private ScopedEnvironment requireEnvironment(OidcIdentity actor, EnvironmentId environmentId) {
    return controlPlaneRepository
        .findEnvironmentFor(actor, environmentId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private ScopedEnvironment requireLockedEnvironment(
      OidcIdentity actor, EnvironmentId environmentId) {
    return controlPlaneRepository
        .lockEnvironmentFor(actor, environmentId)
        .orElseThrow(ControlPlaneNotFoundException::new);
  }

  private static void requireManageKeys(OrganizationAccess access, Environment environment) {
    if (!access.actorRole().allows(OrganizationAbility.MANAGE_SDK_KEYS)
        || (access.actorRole() == OrganizationRole.DEVELOPER && environment.productionLike())) {
      throw new OperationForbiddenException();
    }
  }

  private static void requireActive(ScopedEnvironment scoped) {
    if (scoped.access().organization().status() != OrganizationStatus.ACTIVE
        || scoped.project().status() != Project.Status.ACTIVE
        || scoped.environment().status() != Environment.Status.ACTIVE) {
      throw new SdkKeyConflictException(
          "SDK keys require an active organization, project, and environment");
    }
  }

  private static void requireOverlap(Duration overlap) {
    Objects.requireNonNull(overlap, "overlap");
    if (overlap.isNegative() || overlap.compareTo(MAXIMUM_ROTATION_OVERLAP) > 0) {
      throw new IllegalArgumentException("Rotation overlap must be between zero and 24 hours");
    }
  }
}
