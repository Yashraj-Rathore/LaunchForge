package dev.launchforge.application.sdkkey;

import dev.launchforge.application.organization.OrganizationAccess;
import dev.launchforge.domain.controlplane.Environment;
import dev.launchforge.domain.organization.OidcIdentity;
import dev.launchforge.domain.sdkkey.BrowserClientKey;
import dev.launchforge.domain.sdkkey.SdkKeyId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BrowserClientKeyRepository {
  List<BrowserClientKey> findForEnvironment(OrganizationAccess access, Environment environment);

  Optional<ScopedBrowserClientKey> lockFor(OidcIdentity actor, SdkKeyId keyId);

  void insert(OrganizationAccess access, OidcIdentity actor, BrowserClientKey key);

  void revoke(
      OrganizationAccess access, OidcIdentity actor, BrowserClientKey key, Instant revokedAt);

  record ScopedBrowserClientKey(OrganizationAccess access, BrowserClientKey key) {}
}
