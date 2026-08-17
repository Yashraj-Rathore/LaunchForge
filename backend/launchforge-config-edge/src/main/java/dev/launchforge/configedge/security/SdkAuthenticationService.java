package dev.launchforge.configedge.security;

import dev.launchforge.configedge.configuration.SdkKeyPepperProperties;
import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.CredentialLifecycle;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredSdkCredential;
import dev.launchforge.contracts.sdk.ServerSdkKeyCredential;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class SdkAuthenticationService {
  static final String AUTHORIZATION_SCHEME = "LF-SDK ";
  static final int MAXIMUM_AUTHORIZATION_LENGTH = 128;
  private static final byte[] DUMMY_VERIFIER = new byte[32];

  private final EdgeRepository repository;
  private final Map<String, byte[]> peppers;
  private final byte[] fallbackPepper;
  private final Clock clock;

  public SdkAuthenticationService(
      EdgeRepository repository, SdkKeyPepperProperties properties, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.peppers = properties.bytesByVersion();
    this.fallbackPepper = this.peppers.values().iterator().next().clone();
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public SdkCredentialScope authenticate(String authorization) {
    String credential = credential(authorization);
    String lookupId = ServerSdkKeyCredential.lookupId(credential).orElse("");
    StoredSdkCredential stored = repository.findCredential(lookupId).orElse(null);
    byte[] configuredPepper = stored == null ? null : peppers.get(stored.pepperVersion());
    byte[] pepper = configuredPepper == null ? fallbackPepper : configuredPepper;
    byte[] expected =
        stored == null || configuredPepper == null ? DUMMY_VERIFIER : stored.verifier();
    boolean secretMatches = ServerSdkKeyCredential.verify(credential, expected, pepper);
    if (!secretMatches
        || stored == null
        || configuredPepper == null
        || !usable(stored.status(), stored.expiresAt())) {
      throw SdkAuthenticationException.unauthorized();
    }
    if (!stored.scopeActive()) {
      throw SdkAuthenticationException.forbidden();
    }
    repository.recordUse(stored.keyId());
    return new SdkCredentialScope(stored.keyId(), stored.environmentId());
  }

  public void revalidate(SdkCredentialScope scope) {
    CredentialLifecycle lifecycle =
        repository
            .findLifecycle(scope.keyId())
            .orElseThrow(SdkAuthenticationException::unauthorized);
    if (!lifecycle.environmentId().equals(scope.environmentId())
        || !usable(lifecycle.status(), lifecycle.expiresAt())) {
      throw SdkAuthenticationException.unauthorized();
    }
    if (!lifecycle.scopeActive()) {
      throw SdkAuthenticationException.forbidden();
    }
  }

  private boolean usable(String status, Instant expiresAt) {
    return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(clock.instant()));
  }

  private static String credential(String authorization) {
    if (authorization == null
        || authorization.length() > MAXIMUM_AUTHORIZATION_LENGTH
        || authorization.indexOf('\r') >= 0
        || authorization.indexOf('\n') >= 0
        || !authorization.startsWith(AUTHORIZATION_SCHEME)) {
      return "";
    }
    return authorization.substring(AUTHORIZATION_SCHEME.length());
  }
}
