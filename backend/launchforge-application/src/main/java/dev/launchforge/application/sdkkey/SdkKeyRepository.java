package dev.launchforge.application.sdkkey;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import dev.launchforge.domain.sdkkey.ServerSdkKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SdkKeyRepository {
  List<ServerSdkKey> findForEnvironment(OrganizationAccess access, Environment environment);

  Optional<ScopedSdkKey> lockFor(OidcIdentity actor, SdkKeyId keyId);

  void insert(
      OrganizationAccess access,
      OidcIdentity actor,
      ServerSdkKey key,
      byte[] verifier,
      String auditAction);

  void endValidity(
      OrganizationAccess access,
      OidcIdentity actor,
      ServerSdkKey key,
      Instant expiresAt,
      boolean revoke,
      String auditAction);

  record ScopedSdkKey(OrganizationAccess access, ServerSdkKey key) {}
}
