package dev.launchforge.configedge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface EdgeRepository {
  Optional<StoredSdkCredential> findCredential(String lookupId);

  Optional<CredentialLifecycle> findLifecycle(UUID keyId);

  Optional<StoredSnapshot> findCurrentSnapshot(UUID environmentId);

  OptionalLong findCurrentRevision(UUID environmentId);

  void recordUse(UUID keyId);

  default Optional<EnvironmentScope> findEnvironmentScope(UUID environmentId) {
    return Optional.empty();
  }

  default Optional<StoredBrowserCredential> findBrowserCredential(String clientKey) {
    return Optional.empty();
  }

  default Optional<BrowserCredentialLifecycle> findBrowserLifecycle(UUID keyId) {
    return Optional.empty();
  }

  default void recordBrowserUse(UUID keyId) {}

  record StoredSdkCredential(
      UUID keyId,
      UUID environmentId,
      byte[] verifier,
      String pepperVersion,
      String status,
      Instant expiresAt,
      boolean scopeActive) {
    public StoredSdkCredential {
      verifier = verifier.clone();
    }

    @Override
    public byte[] verifier() {
      return verifier.clone();
    }
  }

  record CredentialLifecycle(
      UUID environmentId, String status, Instant expiresAt, boolean scopeActive) {}

  record StoredSnapshot(
      UUID environmentId,
      long revision,
      int schemaVersion,
      String canonicalSnapshot,
      String checksum) {}

  record StoredBrowserCredential(
      UUID keyId,
      UUID environmentId,
      List<String> allowedOrigins,
      String status,
      Instant expiresAt,
      boolean scopeActive) {
    public StoredBrowserCredential {
      allowedOrigins = List.copyOf(allowedOrigins);
    }
  }

  record BrowserCredentialLifecycle(
      UUID environmentId, String status, Instant expiresAt, boolean scopeActive) {}

  record EnvironmentScope(
      UUID organizationId,
      UUID projectId,
      UUID environmentId,
      String projectKey,
      String environmentKey) {}
}
