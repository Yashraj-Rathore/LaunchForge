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
import dev.launchforge.domain.sdkkey.BrowserClientKey;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.SdkKeyStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class BrowserClientKeyService {
  private final ControlPlaneRepository controlPlaneRepository;
  private final BrowserClientKeyRepository repository;
  private final BrowserClientKeyGenerator generator;
  private final UnitOfWork unitOfWork;
  private final Clock clock;

  public BrowserClientKeyService(
      ControlPlaneRepository controlPlaneRepository,
      BrowserClientKeyRepository repository,
      BrowserClientKeyGenerator generator,
      UnitOfWork unitOfWork,
      Clock clock) {
    this.controlPlaneRepository = Objects.requireNonNull(controlPlaneRepository);
    this.repository = Objects.requireNonNull(repository);
    this.generator = Objects.requireNonNull(generator);
    this.unitOfWork = Objects.requireNonNull(unitOfWork);
    this.clock = Objects.requireNonNull(clock);
  }

  public BrowserClientKey create(
      OidcIdentity actor,
      EnvironmentId environmentId,
      String name,
      List<String> allowedOrigins,
      Instant expiresAt) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(environmentId, "environmentId");
    return unitOfWork.required(
        () -> {
          ScopedEnvironment scoped = requireLockedEnvironment(actor, environmentId);
          requireManageKeys(scoped.access(), scoped.environment());
          requireActive(scoped);
          Instant now = clock.instant();
          if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Client key expiry must be in the future");
          }
          GeneratedBrowserClientKey generated = generator.generate();
          BrowserClientKey key =
              new BrowserClientKey(
                  SdkKeyId.random(),
                  scoped.access().organization().id(),
                  scoped.project().id(),
                  scoped.environment().id(),
                  name,
                  generated.clientKey(),
                  generated.fingerprint(),
                  allowedOrigins,
                  SdkKeyStatus.ACTIVE,
                  expiresAt,
                  now,
                  null,
                  null);
          repository.insert(scoped.access(), actor, key);
          return key;
        });
  }

  public List<BrowserClientKey> list(OidcIdentity actor, EnvironmentId environmentId) {
    ScopedEnvironment scoped = requireEnvironment(actor, environmentId);
    requireManageKeys(scoped.access(), scoped.environment());
    return repository.findForEnvironment(scoped.access(), scoped.environment());
  }

  public void revoke(OidcIdentity actor, SdkKeyId keyId) {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(keyId, "keyId");
    unitOfWork.required(
        () -> {
          BrowserClientKeyRepository.ScopedBrowserClientKey scoped =
              repository.lockFor(actor, keyId).orElseThrow(ControlPlaneNotFoundException::new);
          ScopedEnvironment environment =
              requireLockedEnvironment(actor, scoped.key().environmentId());
          requireManageKeys(scoped.access(), environment.environment());
          if (scoped.key().status() != SdkKeyStatus.REVOKED) {
            repository.revoke(scoped.access(), actor, scoped.key(), clock.instant());
          }
          return null;
        });
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
          "Client keys require an active organization, project, and environment");
    }
  }
}
