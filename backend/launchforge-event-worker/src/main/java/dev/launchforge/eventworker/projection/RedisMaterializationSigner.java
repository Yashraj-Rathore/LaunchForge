package dev.launchforge.eventworker.projection;

import dev.launchforge.contracts.snapshots.RedisMaterializationProvenance;
import java.security.PrivateKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class RedisMaterializationSigner {
  private final String keyId;
  private final PrivateKey privateKey;

  RedisMaterializationSigner(
      @Value("${launchforge.distribution.materialization-signing-key-id}") String keyId,
      @Value("${launchforge.distribution.materialization-signing-private-key}")
          String encodedPrivateKey) {
    this.keyId = RedisMaterializationProvenance.requireKeyId(keyId);
    this.privateKey = RedisMaterializationProvenance.decodePrivateKey(encodedPrivateKey);
  }

  SignedMaterialization sign(AuthoritativeSnapshot snapshot) {
    String snapshotSignature =
        RedisMaterializationProvenance.signSnapshot(
            privateKey,
            snapshot.environmentId(),
            snapshot.revision(),
            snapshot.schemaVersion(),
            snapshot.checksum(),
            snapshot.canonicalSnapshot());
    String revisionSignature =
        RedisMaterializationProvenance.signRevision(
            privateKey, snapshot.environmentId(), snapshot.revision());
    return new SignedMaterialization(keyId, snapshotSignature, revisionSignature);
  }

  record SignedMaterialization(String keyId, String snapshotSignature, String revisionSignature) {}
}
