package dev.launchforge.contracts.snapshots;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisMaterializationProvenanceTest {
  private static final UUID ENVIRONMENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Test
  void signerAndVerifierBindEverySnapshotAndRevisionField() throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String snapshot = "{\"checksum\":\"" + "a".repeat(64) + "\",\"revision\":7}";
    String snapshotSignature =
        RedisMaterializationProvenance.signSnapshot(
            keys.getPrivate(), ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot);
    String revisionSignature =
        RedisMaterializationProvenance.signRevision(keys.getPrivate(), ENVIRONMENT_ID, 7);

    assertTrue(
        RedisMaterializationProvenance.verifySnapshot(
            keys.getPublic(), ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot, snapshotSignature));
    assertTrue(
        RedisMaterializationProvenance.verifyRevision(
            keys.getPublic(), ENVIRONMENT_ID, 7, revisionSignature));
    assertFalse(
        RedisMaterializationProvenance.verifySnapshot(
            keys.getPublic(), ENVIRONMENT_ID, 8, 1, "a".repeat(64), snapshot, snapshotSignature));
    assertFalse(
        RedisMaterializationProvenance.verifySnapshot(
            keys.getPublic(), ENVIRONMENT_ID, 7, 1, "b".repeat(64), snapshot, snapshotSignature));
    assertFalse(
        RedisMaterializationProvenance.verifyRevision(
            keys.getPublic(), ENVIRONMENT_ID, 8, revisionSignature));
  }

  @Test
  void encodedKeysRoundTripAndInvalidInputsFailClosed() throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String privateKey =
        Base64.getUrlEncoder().withoutPadding().encodeToString(keys.getPrivate().getEncoded());
    String publicKey =
        Base64.getUrlEncoder().withoutPadding().encodeToString(keys.getPublic().getEncoded());

    assertTrue(
        RedisMaterializationProvenance.verifyRevision(
            RedisMaterializationProvenance.decodePublicKey(publicKey),
            ENVIRONMENT_ID,
            1,
            RedisMaterializationProvenance.signRevision(
                RedisMaterializationProvenance.decodePrivateKey(privateKey), ENVIRONMENT_ID, 1)));
    assertFalse(
        RedisMaterializationProvenance.verifyRevision(
            keys.getPublic(), ENVIRONMENT_ID, 1, "not-a-signature"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RedisMaterializationProvenance.decodePrivateKey("not-a-key"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RedisMaterializationProvenance.requireKeyId("unsafe key id"));
  }
}
