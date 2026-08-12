package dev.launchforge.configedge.security;

import dev.launchforge.configedge.persistence.EdgeRepository;
import dev.launchforge.configedge.persistence.EdgeRepository.BrowserCredentialLifecycle;
import dev.launchforge.configedge.persistence.EdgeRepository.StoredBrowserCredential;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class BrowserClientAuthenticationService {
  private static final int MAXIMUM_CLIENT_KEY_LENGTH = 64;

  private final EdgeRepository repository;
  private final Clock clock;

  public BrowserClientAuthenticationService(EdgeRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public BrowserClientScope authenticate(String clientKey, String origin) {
    String candidate = validFormat(clientKey) ? clientKey : "";
    StoredBrowserCredential stored = repository.findBrowserCredential(candidate).orElse(null);
    if (stored == null || !usable(stored.status(), stored.expiresAt())) {
      throw BrowserClientAuthenticationException.unauthorized();
    }
    if (!stored.scopeActive()) {
      throw BrowserClientAuthenticationException.forbidden();
    }
    if (origin != null && !stored.allowedOrigins().contains(origin)) {
      throw BrowserClientAuthenticationException.forbidden();
    }
    repository.recordBrowserUse(stored.keyId());
    return new BrowserClientScope(stored.keyId(), stored.environmentId());
  }

  public void revalidate(BrowserClientScope scope) {
    BrowserCredentialLifecycle lifecycle =
        repository
            .findBrowserLifecycle(scope.keyId())
            .orElseThrow(BrowserClientAuthenticationException::unauthorized);
    if (!lifecycle.environmentId().equals(scope.environmentId())
        || !usable(lifecycle.status(), lifecycle.expiresAt())) {
      throw BrowserClientAuthenticationException.unauthorized();
    }
    if (!lifecycle.scopeActive()) {
      throw BrowserClientAuthenticationException.forbidden();
    }
  }

  private boolean usable(String status, Instant expiresAt) {
    return "ACTIVE".equals(status) && (expiresAt == null || expiresAt.isAfter(clock.instant()));
  }

  private static boolean validFormat(String value) {
    return value != null
        && value.length() <= MAXIMUM_CLIENT_KEY_LENGTH
        && value.matches("lf_client_[A-Za-z0-9_-]{32}");
  }
}
