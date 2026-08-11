package dev.launchforge.configedge.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface EdgeRepository {
  Optional<StoredSdkCredential> findCredential(String lookupId);

  Optional<CredentialLifecycle> findLifecycle(UUID keyId);

  Optional<StoredSnapshot> findCurrentSnapshot(UUID environmentId);

  OptionalLong findCurrentRevision(UUID environmentId);

  void recordUse(UUID keyId);

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
}
