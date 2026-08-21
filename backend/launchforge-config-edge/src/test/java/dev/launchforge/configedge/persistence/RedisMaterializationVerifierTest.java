package dev.launchforge.configedge.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.launchforge.contracts.snapshots.RedisMaterializationProvenance;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisMaterializationVerifierTest {
  private static final UUID ENVIRONMENT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Test
  void acceptsOnlyMaterializationsFromATrustedSigner() throws Exception {
    KeyPair trusted = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    RedisMaterializationVerifier verifier = verifier("current", trusted);
    String snapshot = "{\"checksum\":\"" + "a".repeat(64) + "\",\"revision\":7}";
    String trustedSignature =
        RedisMaterializationProvenance.signSnapshot(
            trusted.getPrivate(), ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot);
    String forgedSignature =
        RedisMaterializationProvenance.signSnapshot(
            attacker.getPrivate(), ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot);

    assertDoesNotThrow(
        () ->
            verifier.verifySnapshot(
                ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot, "1", "current", trustedSignature));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            verifier.verifySnapshot(
                ENVIRONMENT_ID, 7, 1, "a".repeat(64), snapshot, "1", "current", forgedSignature));
  }

  @Test
  void supportsKeyRotationAndRejectsUnknownKeysAndTamperedRevisions() throws Exception {
    KeyPair previous = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    KeyPair current = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String keys = "previous:" + publicKey(previous) + ",current:" + publicKey(current);
    RedisMaterializationVerifier verifier = new RedisMaterializationVerifier(keys);
    String signature =
        RedisMaterializationProvenance.signRevision(current.getPrivate(), ENVIRONMENT_ID, 12);

    assertDoesNotThrow(
        () -> verifier.verifyRevision(ENVIRONMENT_ID, 12, "1", "current", signature));
    assertThrows(
        IllegalArgumentException.class,
        () -> verifier.verifyRevision(ENVIRONMENT_ID, 11, "1", "current", signature));
    assertThrows(
        IllegalArgumentException.class,
        () -> verifier.verifyRevision(ENVIRONMENT_ID, 12, "1", "unknown", signature));
  }

  @Test
  void rejectsMalformedOrDuplicateTrustConfiguration() throws Exception {
    KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    String encoded = publicKey(keys);

    assertThrows(IllegalArgumentException.class, () -> new RedisMaterializationVerifier(""));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RedisMaterializationVerifier("duplicate:" + encoded + ",duplicate:" + encoded));
  }

  private static RedisMaterializationVerifier verifier(String keyId, KeyPair keys) {
    return new RedisMaterializationVerifier(keyId + ':' + publicKey(keys));
  }

  private static String publicKey(KeyPair keys) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(keys.getPublic().getEncoded());
  }
}
